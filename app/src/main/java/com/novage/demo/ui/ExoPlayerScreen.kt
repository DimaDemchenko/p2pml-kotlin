package com.novage.demo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.novage.demo.stats.P2PStats
import com.novage.demo.ui.P2PStatistics

@Composable
fun ExoPlayerScreen(
    player: ExoPlayer?,
    videoTitle: String,
    isLoading: Boolean,
    stats: P2PStats
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Video player occupies the upper portion of the screen.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    PlayerView(context).apply {
                        this.player = player
                    }
                },
                update = { playerView -> playerView.player = player }
            )
            // Title overlay at the top of the video
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                        )
                    )
                    .padding(16.dp)
                    .align(Alignment.TopStart)
            ) {
                Text(
                    text = videoTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )
            }
            // Center loading indicator if needed
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        // Statistics block positioned below the video player.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.8f))
                .padding(16.dp)
        ) {
            P2PStatistics(
                totalHttpDownloaded = stats.bytesDownloadedHttp / (1024 * 1024).toDouble(),
                totalP2PDownloaded = stats.bytesDownloadedP2p / (1024 * 1024).toDouble(),
                totalP2PUploaded = stats.bytesUploaded / (1024 * 1024).toDouble(),
                activePeers = stats.connectedPeers.size, // Pass list of peer IDs
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
