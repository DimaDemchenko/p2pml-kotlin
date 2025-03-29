package com.novage.demo

import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.media3.common.util.UnstableApi
import com.novage.demo.ui.ExoPlayerScreen
import com.novage.demo.viewmodel.ExoPlayerViewModel

@UnstableApi
class MainActivity : ComponentActivity() {
    private val viewModel: ExoPlayerViewModel by lazy {
        ExoPlayerViewModel(application)
    }

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable debugging of WebView to see P2PML logs
        WebView.setWebContentsDebuggingEnabled(true)

        viewModel.setupP2PML()

        setContent {
            val isLoading by viewModel.loadingState.collectAsState()
            val stats by viewModel.p2pStats.collectAsState()
            ExoPlayerScreen(player = viewModel.player, videoTitle = "Test Stream", isLoading, stats)
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.updateP2PConfig(isP2PDisabled = true)
    }

    override fun onRestart() {
        super.onRestart()
        viewModel.updateP2PConfig(isP2PDisabled = false)
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.releasePlayer()
    }
}
