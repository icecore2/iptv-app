import SwiftUI
import AVKit
import IPTVCore
import IPTVPlayer

/// Multiplatform video playback screen with time-shift controls, gestures, stream diagnostics, and EPG drawers.
public struct PlayerScreen: View {
    @ObservedObject public var viewModel: PlayerViewModel
    @ObservedObject public var settingsViewModel: SettingsViewModel
    public let favoriteIds: Set<String>
    public let onToggleFavorite: (String) -> Void
    public let onBack: () -> Void
    public let onOpenSettings: () -> Void

    // Gesture feedback states
    @State private var doubleTapFeedback: (isForward: Bool, count: Int)? = nil
    @State private var isDraggingScrub: Bool = false
    @State private var dragDeltaSeconds: TimeInterval = 0

    public init(
        viewModel: PlayerViewModel,
        settingsViewModel: SettingsViewModel,
        favoriteIds: Set<String> = [],
        onToggleFavorite: @escaping (String) -> Void,
        onBack: @escaping () -> Void,
        onOpenSettings: @escaping () -> Void
    ) {
        self.viewModel = viewModel
        self.settingsViewModel = settingsViewModel
        self.favoriteIds = favoriteIds
        self.onToggleFavorite = onToggleFavorite
        self.onBack = onBack
        self.onOpenSettings = onOpenSettings
    }

    public var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            // 1. Native AVPlayer Video Surface
            VideoPlayer(player: viewModel.playerEngine.player)
                .ignoresSafeArea()
                .aspectRatioMode(viewModel.aspectRatioMode)
                .onTapGesture {
                    viewModel.toggleControls()
                }

            // 2. Gesture Detector for Double-Tap Seek and Drag Scrub
            gestureOverlayView

            // 3. Floating Double-Tap Feedback Badges
            feedbackBadgesView

            // 4. Stream Diagnostics Overlay if enabled in settings
            if settingsViewModel.settings.showStreamInfoOverlay {
                liveStreamDiagnosticsBadge
            }

            // 5. Full Player HUD Controls (Top Bar, Bottom Scrubber & Buttons)
            if viewModel.isControlsVisible {
                playerControlsOverlay
            }

            // 6. Buffering Indicator
            if viewModel.isBuffering {
                ProgressView()
                    .scaleEffect(1.5)
                    .tint(.white)
            }

            // 7. Error Message Banner
            if let error = viewModel.errorMessage {
                VStack {
                    HStack {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .foregroundStyle(.red)
                        Text(error)
                            .foregroundStyle(.white)
                            .font(.subheadline)
                    }
                    .padding()
                    .background(Color.black.opacity(0.8))
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                }
            }
        }
        #if os(iOS)
        .statusBarHidden(!viewModel.isControlsVisible)
        #endif
        .sheet(isPresented: $viewModel.isChannelSelectorVisible) {
            PlayerChannelSelectorSheet(
                channels: viewModel.channelList,
                currentChannel: viewModel.currentChannel,
                matcher: viewModel.matcher,
                onSelectChannel: { newChan in
                    viewModel.playChannel(channel: newChan, playlist: viewModel.channelList, matcher: viewModel.matcher)
                },
                onDismiss: { viewModel.setChannelSelectorVisible(false) }
            )
        }
        .sheet(isPresented: $viewModel.isEpgSheetVisible) {
            if let chan = viewModel.currentChannel {
                EpgScheduleSheet(
                    channel: chan,
                    schedule: viewModel.currentSchedule,
                    onPlayLive: {
                        viewModel.playChannel(channel: chan, playlist: viewModel.channelList, matcher: viewModel.matcher)
                    },
                    onPlayCatchup: { prog in
                        viewModel.playProgrammeVod(programme: prog, channel: chan, playlist: viewModel.channelList, matcher: viewModel.matcher)
                    },
                    onDismiss: { viewModel.setEpgSheetVisible(false) },
                    onOpenMetadata: { title in viewModel.openMetadata(for: title) }
                )
            }
        }
        .sheet(isPresented: Binding(
            get: { viewModel.selectedMetadataItem != nil },
            set: { if !$0 { viewModel.closeMetadata() } }
        )) {
            if let metaItem = viewModel.selectedMetadataItem {
                ProgrammeDetailsSplitView(
                    rawTitle: metaItem.title,
                    initialMetadata: metaItem.metadata,
                    metadataRepository: viewModel.metadataRepository,
                    preferredLanguage: settingsViewModel.settings.metadataLanguage,
                    onClose: { viewModel.closeMetadata() }
                )
            }
        }
        .sheet(isPresented: $viewModel.isStreamInfoDialogVisible) {
            StreamInfoDialog(
                streamInfo: viewModel.streamInfo,
                onDismiss: { viewModel.setStreamInfoDialogVisible(false) }
            )
        }
        .sheet(isPresented: $viewModel.isSpeedDialogVisible) {
            PlaybackSpeedDialog(
                currentSpeed: viewModel.playbackSpeed,
                onSelect: { viewModel.setPlaybackSpeed($0) },
                onDismiss: { viewModel.setSpeedDialogVisible(false) }
            )
        }
        .sheet(isPresented: $viewModel.isSleepTimerDialogVisible) {
            SleepTimerDialog(
                currentRemainingMinutes: viewModel.sleepTimerMinutesRemaining,
                onSetTimer: { viewModel.setSleepTimer(minutes: $0) },
                onCancelTimer: { viewModel.cancelSleepTimer() },
                onDismiss: { viewModel.setSleepTimerDialogVisible(false) }
            )
        }
    }

    // MARK: - Gestures

    private var gestureOverlayView: some View {
        GeometryReader { proxy in
            HStack(spacing: 0) {
                // Left half (Double tap to rewind 10s)
                Color.clear
                    .contentShape(Rectangle())
                    .onTapGesture(count: 2) {
                        viewModel.seekBy(deltaSeconds: -10)
                        triggerFeedback(isForward: false)
                    }

                // Right half (Double tap to forward 10s)
                Color.clear
                    .contentShape(Rectangle())
                    .onTapGesture(count: 2) {
                        viewModel.seekBy(deltaSeconds: 10)
                        triggerFeedback(isForward: true)
                    }
            }
        }
    }

    private func triggerFeedback(isForward: Bool) {
        doubleTapFeedback = (isForward, 10)
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.75) {
            doubleTapFeedback = nil
        }
    }

    @ViewBuilder
    private var feedbackBadgesView: some View {
        if let feedback = doubleTapFeedback {
            HStack {
                if !feedback.isForward {
                    VStack(spacing: 6) {
                        Image(systemName: "gobackward.10")
                            .font(.system(size: 36))
                        Text("-10s")
                            .font(.caption.bold())
                    }
                    .padding(20)
                    .background(.ultraThinMaterial)
                    .clipShape(Circle())
                    .foregroundStyle(.white)
                    .padding(.leading, 40)
                    Spacer()
                } else {
                    Spacer()
                    VStack(spacing: 6) {
                        Image(systemName: "goforward.10")
                            .font(.system(size: 36))
                        Text("+10s")
                            .font(.caption.bold())
                    }
                    .padding(20)
                    .background(.ultraThinMaterial)
                    .clipShape(Circle())
                    .foregroundStyle(.white)
                    .padding(.trailing, 40)
                }
            }
        }
    }

    // MARK: - HUD Controls

    private var playerControlsOverlay: some View {
        VStack {
            // Top Bar
            topBarView

            Spacer()

            // Bottom Bar
            bottomBarView
        }
        .background(
            LinearGradient(
                colors: [Color.black.opacity(0.8), Color.clear, Color.clear, Color.black.opacity(0.85)],
                startPoint: .top,
                endPoint: .bottom
            )
        )
        .ignoresSafeArea()
    }

    private var topBarView: some View {
        HStack(spacing: 12) {
            Button(action: onBack) {
                Image(systemName: "chevron.left")
                    .font(.title2.bold())
                    .foregroundStyle(.white)
            }
            .buttonStyle(.plain)

            VStack(alignment: .leading, spacing: 2) {
                Text(viewModel.currentChannel?.name ?? "Live TV")
                    .font(.headline)
                    .foregroundStyle(.white)
                    .lineLimit(1)

                if let prog = viewModel.activeProgramme {
                    HStack(spacing: 6) {
                        Text(prog.title)
                            .font(.caption)
                            .foregroundStyle(.white.opacity(0.8))
                            .lineLimit(1)

                        if settingsViewModel.settings.showInlineMetadataBadge {
                            SourceBadgeView(source: .auto) {
                                viewModel.openMetadata(for: prog.title)
                            }
                        }
                    }
                } else if let channel = viewModel.currentChannel {
                    HStack(spacing: 6) {
                        Text(channel.group)
                            .font(.caption)
                            .foregroundStyle(.white.opacity(0.7))
                            .lineLimit(1)

                        if channel.isVod && settingsViewModel.settings.showInlineMetadataBadge {
                            SourceBadgeView(source: .auto) {
                                viewModel.openMetadata(for: channel.name)
                            }
                        }
                    }
                }
            }

            Spacer()

            // Favorite Button
            if let curId = viewModel.currentChannel?.id {
                let isFav = favoriteIds.contains(curId)
                Button(action: { onToggleFavorite(curId) }) {
                    Image(systemName: isFav ? "heart.fill" : "heart")
                        .foregroundStyle(isFav ? .red : .white)
                        .font(.title3)
                }
                .buttonStyle(.plain)
            }

            // Aspect Ratio Cycle
            Button(action: { viewModel.cycleAspectRatio() }) {
                Text(viewModel.aspectRatioMode.rawValue)
                    .font(.caption2.bold())
                    .padding(.horizontal, 6)
                    .padding(.vertical, 3)
                    .background(Color.white.opacity(0.2))
                    .foregroundStyle(.white)
                    .clipShape(RoundedRectangle(cornerRadius: 4))
            }
            .buttonStyle(.plain)

            // Screen Lock Toggle
            Button(action: { viewModel.toggleLock() }) {
                Image(systemName: viewModel.isLocked ? "lock.fill" : "lock.open")
                    .foregroundStyle(viewModel.isLocked ? .yellow : .white)
            }
            .buttonStyle(.plain)

            // Settings button
            Button(action: onOpenSettings) {
                Image(systemName: "gearshape")
                    .foregroundStyle(.white)
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 16)
        .padding(.top, 48)
        .padding(.bottom, 12)
    }

    private var bottomBarView: some View {
        VStack(spacing: 12) {
            // Enhanced Timeline Scrubber
            if !viewModel.isLocked {
                EnhancedPlayerScrubber(
                    isVod: viewModel.isVodPlayback,
                    currentPosMs: viewModel.isVodPlayback ? viewModel.vodProgressMs : viewModel.livePositionFromStartMs,
                    durationMs: viewModel.isVodPlayback ? viewModel.vodDurationMs : viewModel.liveSessionDurationMs,
                    isAtLiveEdge: viewModel.isAtLiveEdge,
                    timeShiftOffsetMs: viewModel.timeShiftOffsetMs,
                    onSeek: { targetMs in
                        if viewModel.isVodPlayback {
                            viewModel.seek(toSeconds: Double(targetMs) / 1000.0)
                        } else {
                            let deltaMs = targetMs - viewModel.livePositionFromStartMs
                            viewModel.seekBy(deltaSeconds: Double(deltaMs) / 1000.0)
                        }
                    },
                    onGoToLive: {
                        viewModel.seekBy(deltaSeconds: Double(viewModel.timeShiftOffsetMs) / 1000.0)
                    }
                )
            }

            // Transport Control Buttons
            if !viewModel.isLocked {
                HStack(spacing: 20) {
                    // Previous Channel
                    Button(action: { viewModel.playPrevious() }) {
                        Image(systemName: "backward.end.fill")
                            .font(.title2)
                    }

                    // Seek Backward 10s
                    Button(action: { viewModel.seekBy(deltaSeconds: -10) }) {
                        Image(systemName: "gobackward.10")
                            .font(.title2)
                    }

                    // Play / Pause
                    Button(action: { viewModel.togglePlayPause() }) {
                        Image(systemName: viewModel.isPlaying ? "pause.circle.fill" : "play.circle.fill")
                            .font(.system(size: 48))
                    }

                    // Seek Forward 10s
                    Button(action: { viewModel.seekBy(deltaSeconds: 10) }) {
                        Image(systemName: "goforward.10")
                            .font(.title2)
                    }

                    // Next Channel
                    Button(action: { viewModel.playNext() }) {
                        Image(systemName: "forward.end.fill")
                            .font(.title2)
                    }

                    Spacer()

                    // Channel Selector Drawer
                    Button(action: { viewModel.setChannelSelectorVisible(true) }) {
                        Image(systemName: "list.bullet.rectangle")
                            .font(.title3)
                    }

                    // EPG Schedule Drawer
                    Button(action: { viewModel.setEpgSheetVisible(true) }) {
                        Image(systemName: "calendar")
                            .font(.title3)
                    }

                    // Diagnostics Modal
                    Button(action: { viewModel.setStreamInfoDialogVisible(true) }) {
                        Image(systemName: "info.circle")
                            .font(.title3)
                    }

                    // Speed Modal
                    Button(action: { viewModel.setSpeedDialogVisible(true) }) {
                        Text(String(format: "%.1fx", viewModel.playbackSpeed))
                            .font(.caption.bold())
                    }

                    // Sleep Timer
                    Button(action: { viewModel.setSleepTimerDialogVisible(true) }) {
                        Image(systemName: viewModel.sleepTimerMinutesRemaining != nil ? "moon.zzz.fill" : "moon.zzz")
                            .font(.title3)
                            .foregroundStyle(viewModel.sleepTimerMinutesRemaining != nil ? .yellow : .white)
                    }

                    // Mute Toggle
                    Button(action: { viewModel.toggleMute() }) {
                        Image(systemName: viewModel.isMuted ? "speaker.slash.fill" : "speaker.wave.2.fill")
                            .font(.title3)
                    }
                }
                .foregroundStyle(.white)
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 16)
        .padding(.bottom, 36)
    }

    private var liveStreamDiagnosticsBadge: some View {
        VStack {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("\(viewModel.streamInfo.resolution) • \(viewModel.streamInfo.frameRate)")
                    Text("\(viewModel.streamInfo.bitrate) • \(viewModel.streamInfo.videoCodec)")
                    Text("Buffer: \(viewModel.streamInfo.bufferPercentage)%")
                }
                .font(.system(size: 11, weight: .bold, design: .monospaced))
                .foregroundStyle(.white)
                .padding(8)
                .background(Color.black.opacity(0.6))
                .clipShape(RoundedRectangle(cornerRadius: 6))

                Spacer()
            }
            .padding(.top, 96)
            .padding(.leading, 16)
            Spacer()
        }
    }
}

// Extension to apply AspectRatioMode to SwiftUI View
extension View {
    @ViewBuilder
    func aspectRatioMode(_ mode: AspectRatioMode) -> some View {
        switch mode {
        case .fit:
            self.aspectRatio(contentMode: .fit)
        case .zoom:
            self.aspectRatio(contentMode: .fill)
        case .fill:
            self
        }
    }
}
