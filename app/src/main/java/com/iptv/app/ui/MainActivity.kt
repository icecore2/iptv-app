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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.iptv.app.ui.screens.ChannelListScreen
import com.iptv.app.ui.screens.PlayerScreen
import com.iptv.app.ui.screens.PlaylistInputScreen
import com.iptv.app.ui.theme.IptvTheme
import com.iptv.app.ui.viewmodel.PlayerViewModel
import com.iptv.app.ui.viewmodel.PlaylistViewModel

class MainActivity : ComponentActivity() {

    private val playlistViewModel: PlaylistViewModel by viewModels()
    private val playerViewModel: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            IptvTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    IptvAppNavigation(
                        playlistViewModel = playlistViewModel,
                        playerViewModel = playerViewModel
                    )
                }
            }
        }
    }
}

@Composable
fun IptvAppNavigation(
    playlistViewModel: PlaylistViewModel,
    playerViewModel: PlayerViewModel
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
                onPlaylistLoaded = {
                    navController.navigate("channels") {
                        popUpTo("input") { inclusive = true }
                    }
                }
            )
        }

        composable("channels") {
            ChannelListScreen(
                viewModel = playlistViewModel,
                onChannelSelected = { channel, channels ->
                    playerViewModel.playChannel(
                        channel = channel,
                        playlist = channels,
                        matcher = playlistState.epgMatcher
                    )
                    navController.navigate("player")
                },
                onChangePlaylist = {
                    navController.navigate("input")
                }
            )
        }

        composable("player") {
            PlayerScreen(
                viewModel = playerViewModel,
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
