import Foundation
import Combine
import IPTVCore
import IPTVData
import IPTVPlayer

@MainActor
public final class PlayerViewModel: ObservableObject {

    public let playerEngine = AVPlayerEngine()
    private let timeShiftController = TimeShiftBufferController()

    @Published public var currentChannel: M3uItem? = nil
    @Published public var currentEpg: ChannelWithEpg? = nil
    @Published public var activeProgramme: EpgProgramme? = nil
    @Published public var isVodPlayback: Bool = false
    @Published public var vodProgressMs: Int64 = 0
    @Published public var vodDurationMs: Int64 = 0
    @Published public var channelList: [M3uItem] = []
    @Published public var matcher: EpgMatcher? = nil
    @Published public var isPlaying: Bool = false
    @Published public var isBuffering: Bool = false
    @Published public var errorMessage: String? = nil
    @Published public var aspectRatioMode: AspectRatioMode = .fit
    @Published public var isControlsVisible: Bool = true
    @Published public var isLocked: Bool = false
    @Published public var isMuted: Bool = false
    @Published public var playbackSpeed: Float = 1.0
    @Published public var sleepTimerMinutesRemaining: Int? = nil
    @Published public var streamInfo: StreamInfo = StreamInfo()

    // Dialog state flags
    @Published public var isStreamInfoDialogVisible: Bool = false
    @Published public var isEpgSheetVisible: Bool = false
    @Published public var isChannelSelectorVisible: Bool = false
    @Published public var isSpeedDialogVisible: Bool = false
    @Published public var isSleepTimerDialogVisible: Bool = false
    @Published public var originalChannel: M3uItem? = nil

    // Time-Shift Replay Buffer for Live Streams
    @Published public var liveSessionStartTimeMs: Int64 = 0
    @Published public var liveSessionDurationMs: Int64 = 0
    @Published public var livePositionFromStartMs: Int64 = 0
    @Published public var isAtLiveEdge: Bool = true
    @Published public var timeShiftOffsetMs: Int64 = 0
    @Published public var canGoBackToStart: Bool = false

    // EPG Schedule for timeline markers & tooltips
    @Published public var currentSchedule: [EpgProgramme] = []
    @Published public var selectedMetadataItem: (title: String, metadata: ProgrammeMetadata?)? = nil

    public let metadataRepository: ProgrammeMetadataRepository
    private var sleepTimerTask: Task<Void, Never>?
    private var cancellables = Set<AnyCancellable>()
    private var lastMatchedProgramme: EpgProgramme? = nil

    public init(metadataRepository: ProgrammeMetadataRepository = ProgrammeMetadataRepository()) {
        self.metadataRepository = metadataRepository
        bindPlayerEngine()
        setupNowPlayingCallbacks()
    }

    public func openMetadata(for title: String, metadata: ProgrammeMetadata? = nil) {
        self.selectedMetadataItem = (title: title, metadata: metadata)
    }

    public func closeMetadata() {
        self.selectedMetadataItem = nil
    }

    public var hasAnyDialogOpen: Bool {
        isStreamInfoDialogVisible || isEpgSheetVisible ||
        isChannelSelectorVisible || isSpeedDialogVisible ||
        isSleepTimerDialogVisible
    }

    private func bindPlayerEngine() {
        playerEngine.$isPlaying
            .receive(on: RunLoop.main)
            .sink { [weak self] val in
                self?.isPlaying = val
                self?.syncNowPlaying()
            }
            .store(in: &cancellables)

        playerEngine.$isBuffering
            .receive(on: RunLoop.main)
            .assign(to: &$isBuffering)

        playerEngine.$errorMessage
            .receive(on: RunLoop.main)
            .assign(to: &$errorMessage)

        playerEngine.$streamInfo
            .receive(on: RunLoop.main)
            .assign(to: &$streamInfo)

        playerEngine.$currentTime
            .receive(on: RunLoop.main)
            .sink { [weak self] time in
                guard let self = self else { return }
                if self.isVodPlayback {
                    self.vodProgressMs = Int64(time * 1000)
                    self.vodDurationMs = Int64(self.playerEngine.duration * 1000)
                } else {
                    let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
                    let sessionElapsed = max(0, nowMs - self.liveSessionStartTimeMs)
                    self.updateLiveBufferProgress(sessionDurationMs: sessionElapsed, positionFromStartMs: sessionElapsed)
                }
            }
            .store(in: &cancellables)
    }

    private func setupNowPlayingCallbacks() {
        NowPlayingController.shared.onPlayPauseToggled = { [weak self] in
            self?.playerEngine.togglePlayPause()
        }
        NowPlayingController.shared.onNextChannel = { [weak self] in
            self?.playNext()
        }
        NowPlayingController.shared.onPreviousChannel = { [weak self] in
            self?.playPrevious()
        }
        NowPlayingController.shared.onSkip = { [weak self] delta in
            self?.playerEngine.seekBy(deltaSeconds: delta)
        }
    }

    private func syncNowPlaying() {
        guard let channel = currentChannel else {
            NowPlayingController.shared.clearNowPlaying()
            return
        }

        NowPlayingController.shared.updateNowPlaying(
            channelName: channel.name,
            programmeTitle: activeProgramme?.title,
            isLive: !isVodPlayback,
            duration: isVodPlayback ? Double(vodDurationMs) / 1000.0 : 0,
            elapsedTime: isVodPlayback ? Double(vodProgressMs) / 1000.0 : 0,
            isPlaying: isPlaying
        )
    }

    public func playChannel(channel: M3uItem, playlist: [M3uItem], matcher: EpgMatcher? = nil) {
        let enriched = matcher?.enrichChannel(channel: channel) ?? ChannelWithEpg(channel: channel)
        self.currentChannel = channel
        self.currentEpg = enriched
        self.activeProgramme = enriched.currentProgramme
        self.isVodPlayback = channel.isVod
        self.vodProgressMs = 0
        self.vodDurationMs = 0
        self.liveSessionStartTimeMs = Int64(Date().timeIntervalSince1970 * 1000)
        self.liveSessionDurationMs = 0
        self.livePositionFromStartMs = 0
        self.isAtLiveEdge = true
        self.timeShiftOffsetMs = 0
        self.canGoBackToStart = false
        self.currentSchedule = matcher?.getSchedule(channel: channel) ?? []
        self.channelList = playlist
        self.matcher = matcher
        self.errorMessage = nil
        self.isBuffering = true
        self.originalChannel = nil

        guard let streamUrl = URL(string: channel.streamUrl) else {
            self.errorMessage = "Invalid stream URL"
            return
        }

        playerEngine.loadStream(url: streamUrl, headers: channel.headers, isVod: channel.isVod)
        syncNowPlaying()
    }

    public func playProgrammeVod(
        programme: EpgProgramme,
        channel: M3uItem,
        playlist: [M3uItem] = [],
        matcher: EpgMatcher? = nil
    ) {
        let vodUrlStr = CatchupResolver.buildVodUrl(channel: channel, programme: programme)
        let vodItem = M3uItem(
            id: "\(channel.id)_vod_\(programme.startEpochMillis)",
            name: programme.title,
            streamUrl: vodUrlStr,
            group: "\(channel.name) • VOD Catchup",
            logoUrl: programme.iconUrl ?? channel.logoUrl,
            tvgId: channel.tvgId,
            tvgName: channel.tvgName,
            headers: channel.headers,
            isRadio: false,
            isCatchup: true
        )
        let durMs = max(0, programme.stopEpochMillis - programme.startEpochMillis)
        let enriched = ChannelWithEpg(channel: vodItem, currentProgramme: programme)

        self.currentChannel = vodItem
        self.currentEpg = enriched
        self.activeProgramme = programme
        self.isVodPlayback = true
        self.vodProgressMs = 0
        self.vodDurationMs = durMs
        self.liveSessionStartTimeMs = 0
        self.liveSessionDurationMs = 0
        self.livePositionFromStartMs = 0
        self.isAtLiveEdge = true
        self.timeShiftOffsetMs = 0
        self.canGoBackToStart = false
        self.currentSchedule = matcher?.getSchedule(channel: channel) ?? []
        self.channelList = playlist
        self.matcher = matcher
        self.errorMessage = nil
        self.isBuffering = true
        self.originalChannel = channel

        guard let vodUrl = URL(string: vodUrlStr) else {
            self.errorMessage = "Invalid catchup URL"
            return
        }

        playerEngine.loadStream(url: vodUrl, headers: channel.headers, isVod: true)
        syncNowPlaying()
    }

    public func getProgrammeAtTime(epochMillis: Int64) -> EpgProgramme? {
        if let cached = lastMatchedProgramme,
           epochMillis >= cached.startEpochMillis && epochMillis < cached.stopEpochMillis {
            return cached
        }
        let match = currentSchedule.first(where: {
            epochMillis >= $0.startEpochMillis && epochMillis < $0.stopEpochMillis
        })
        lastMatchedProgramme = match
        return match
    }

    public func updateVodProgress(currentPosMs: Int64, durationMs: Int64) {
        self.vodProgressMs = currentPosMs
        if durationMs > 0 {
            self.vodDurationMs = durationMs
        }
    }

    public func updateLiveBufferProgress(sessionDurationMs: Int64, positionFromStartMs: Int64) {
        let state = timeShiftController.updateProgress(
            sessionDurationMs: sessionDurationMs,
            positionFromStartMs: positionFromStartMs
        )
        self.liveSessionDurationMs = state.liveSessionDurationMs
        self.livePositionFromStartMs = state.livePositionFromStartMs
        self.timeShiftOffsetMs = state.timeShiftOffsetMs
        self.isAtLiveEdge = state.isAtLiveEdge
        self.canGoBackToStart = state.canGoBackToStart
    }

    public func resetLiveBuffer() {
        let state = timeShiftController.resetLiveBuffer()
        self.liveSessionStartTimeMs = state.liveSessionStartTimeMs
        self.liveSessionDurationMs = state.liveSessionDurationMs
        self.livePositionFromStartMs = state.livePositionFromStartMs
        self.timeShiftOffsetMs = state.timeShiftOffsetMs
        self.isAtLiveEdge = state.isAtLiveEdge
        self.canGoBackToStart = state.canGoBackToStart
    }

    public func playNext() {
        guard !channelList.isEmpty, let cur = currentChannel else { return }
        let currentIndex = channelList.firstIndex(where: { $0.streamUrl == cur.streamUrl }) ?? -1
        let nextIndex = (currentIndex == -1 || currentIndex >= channelList.count - 1) ? 0 : currentIndex + 1
        let nextChannel = channelList[nextIndex]
        playChannel(channel: nextChannel, playlist: channelList, matcher: matcher)
    }

    public func playPrevious() {
        guard !channelList.isEmpty, let cur = currentChannel else { return }
        let currentIndex = channelList.firstIndex(where: { $0.streamUrl == cur.streamUrl }) ?? -1
        let prevIndex = (currentIndex <= 0) ? channelList.count - 1 : currentIndex - 1
        let prevChannel = channelList[prevIndex]
        playChannel(channel: prevChannel, playlist: channelList, matcher: matcher)
    }

    public func cycleAspectRatio() {
        switch aspectRatioMode {
        case .fit: aspectRatioMode = .zoom
        case .zoom: aspectRatioMode = .fill
        case .fill: aspectRatioMode = .fit
        }
    }

    public func setAspectRatioMode(_ mode: AspectRatioMode) {
        self.aspectRatioMode = mode
    }

    public func togglePlayPause() {
        playerEngine.togglePlayPause()
    }

    public func seek(toSeconds seconds: TimeInterval) {
        playerEngine.seek(to: seconds)
    }

    public func seekBy(deltaSeconds: TimeInterval) {
        playerEngine.seekBy(deltaSeconds: deltaSeconds)
    }

    public func toggleControls() {
        if isLocked {
            // Locked screen only reveals unlock prompt
            isControlsVisible.toggle()
            return
        }
        isControlsVisible.toggle()
    }

    public func setControlsVisible(_ visible: Bool) {
        isControlsVisible = visible
    }

    public func toggleLock() {
        isLocked.toggle()
    }

    public func toggleMute() {
        isMuted.toggle()
        playerEngine.setMuted(isMuted)
    }

    public func setPlaybackSpeed(_ speed: Float) {
        playbackSpeed = speed
        playerEngine.setRate(speed)
    }

    public func setSleepTimer(minutes: Int) {
        cancelSleepTimer()
        sleepTimerMinutesRemaining = minutes

        sleepTimerTask = Task { [weak self] in
            var remaining = minutes
            while remaining > 0 {
                try? await Task.sleep(nanoseconds: 60_000_000_000)
                if Task.isCancelled { return }
                remaining -= 1
                await MainActor.run {
                    self?.sleepTimerMinutesRemaining = remaining
                }
            }
            // Timer expired -> pause stream
            await MainActor.run {
                self?.playerEngine.pause()
                self?.sleepTimerMinutesRemaining = nil
            }
        }
    }

    public func cancelSleepTimer() {
        sleepTimerTask?.cancel()
        sleepTimerTask = nil
        sleepTimerMinutesRemaining = nil
    }

    public func setStreamInfoDialogVisible(_ visible: Bool) {
        isStreamInfoDialogVisible = visible
    }

    public func setEpgSheetVisible(_ visible: Bool) {
        isEpgSheetVisible = visible
    }

    public func setChannelSelectorVisible(_ visible: Bool) {
        isChannelSelectorVisible = visible
    }

    public func setSpeedDialogVisible(_ visible: Bool) {
        isSpeedDialogVisible = visible
    }

    public func setSleepTimerDialogVisible(_ visible: Bool) {
        isSleepTimerDialogVisible = visible
    }
}
