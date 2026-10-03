package com.iptv.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.iptv.app.data.SharedPreferencesSavedPlaylistRepository
import com.iptv.app.data.SharedPreferencesSettingsRepository
import com.iptv.app.ui.screens.ChannelListScreen
import com.iptv.app.ui.screens.EpgProgrammesScreen
import com.iptv.app.ui.screens.PlayerScreen
import com.iptv.app.ui.screens.PlaylistInputScreen
import com.iptv.app.ui.screens.SettingsScreen
import com.iptv.app.ui.theme.IptvTheme
import com.iptv.app.ui.viewmodel.PlayerViewModel
import com.iptv.app.ui.viewmodel.PlaylistViewModel
import com.iptv.app.ui.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {

    private val settingsViewModel: SettingsViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return SettingsViewModel(
                    settingsRepository = SharedPreferencesSettingsRepository(applicationContext)
                ) as T
            }
        }
    }

    private val playlistViewModel: PlaylistViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return PlaylistViewModel(
                    savedPlaylistRepository = SharedPreferencesSavedPlaylistRepository(applicationContext)
                ) as T
            }
        }
    }
    private val playerViewModel: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (settingsViewModel.settings.value.autoLoadLastPlaylist) {
            playlistViewModel.loadSavedPlaylists(autoLoadActive = true)
        }

        setContent {
            IptvTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    IptvAppNavigation(
                        playlistViewModel = playlistViewModel,
                        playerViewModel = playerViewModel,
                        settingsViewModel = settingsViewModel
                    )
                }
            }
        }
    }
}

@Composable
fun IptvAppNavigation(
    playlistViewModel: PlaylistViewModel,
    playerViewModel: PlayerViewModel,
    settingsViewModel: SettingsViewModel
) {
    val navController = rememberNavController()
    val playlistState by playlistViewModel.uiState.collectAsState()

    NavHost(
        navController = navController,
        startDestination = if (playlistState.channels.isEmpty()) "input" else "channels"
    ) {
        composable("input") {
            PlaylistInputScreen(
                viewModel = playlistViewModel,
                settingsViewModel = settingsViewModel,
                onPlaylistLoaded = {
                    navController.navigate("channels") {
                        popUpTo("input") { inclusive = true }
                    }
                },
                onOpenSettings = {
                    navController.navigate("settings")
                }
            )
        }

        composable("channels") {
            val rawChannels = playlistState.channels.map { it.channel }
            ChannelListScreen(
                viewModel = playlistViewModel,
                settingsViewModel = settingsViewModel,
                onChannelSelected = { channel, channels ->
                    playerViewModel.playChannel(
                        channel = channel,
                        playlist = channels,
                        matcher = playlistState.epgMatcher
                    )
                    navController.navigate("player")
                },
                onOpenEpgGuide = {
                    navController.navigate("epg")
                },
                onPlayProgrammeVod = { programme, channel ->
                    playerViewModel.playProgrammeVod(
                        programme = programme,
                        channel = channel,
                        playlist = rawChannels,
                        matcher = playlistState.epgMatcher
                    )
                    navController.navigate("player")
                },
                onChangePlaylist = {
                    playlistViewModel.clearPlaylist()
                    navController.navigate("input") {
                        popUpTo("channels") { inclusive = true }
                    }
                },
                onOpenSettings = {
                    navController.navigate("settings")
                }
            )
        }

        composable("epg") {
            val rawChannels = playlistState.channels.map { it.channel }
            EpgProgrammesScreen(
                viewModel = playlistViewModel,
                settingsViewModel = settingsViewModel,
                onPlayProgrammeVod = { programme, channel ->
                    playerViewModel.playProgrammeVod(
                        programme = programme,
                        channel = channel,
                        playlist = rawChannels,
                        matcher = playlistState.epgMatcher
                    )
                    navController.navigate("player")
                },
                onPlayChannelLive = { channel ->
                    playerViewModel.playChannel(
                        channel = channel,
                        playlist = rawChannels,
                        matcher = playlistState.epgMatcher
                    )
                    navController.navigate("player")
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable("player") {
            PlayerScreen(
                viewModel = playerViewModel,
                settingsViewModel = settingsViewModel,
                favoriteIds = playlistState.favoriteIds,
                onToggleFavorite = { playlistViewModel.toggleFavorite(it) },
                onBack = {
                    navController.popBackStack()
                },
                onOpenSettings = {
                    navController.navigate("settings")
                }
            )
        }

        composable("settings") {
            SettingsScreen(
                viewModel = settingsViewModel,
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
