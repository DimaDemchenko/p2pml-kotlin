package com.novage.demo.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import com.novage.demo.Streams
import com.novage.p2pml.P2PMediaLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@UnstableApi
class ExoPlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val context: Context
        get() = getApplication()

    val player: ExoPlayer by lazy {
        ExoPlayer.Builder(context).build()
    }
    private var p2pml: P2PMediaLoader? = null

    private val _loadingState = MutableStateFlow(true)
    val loadingState: StateFlow<Boolean> get() = _loadingState

    fun setupP2PML() {
        p2pml = P2PMediaLoader(
            onP2PReadyCallback = { initializePlayback() },
            onP2PReadyErrorCallback = { onReadyError(it) },
            coreConfigJson = "{\"swarmId\":\"TEST_KOTLIN\"}",
            serverPort = 8081,
        )
        p2pml!!.start(context, player)
    }

    private fun initializePlayback() {
        val manifest = p2pml?.getManifestUrl(Streams.HLS_BIG_BUCK_BUNNY)
            ?: throw IllegalStateException("P2PML is not started")
        val loggingDataSourceFactory = LoggingDataSourceFactory(context)

        val mediaSource = HlsMediaSource.Factory(loggingDataSourceFactory)
            .createMediaSource(MediaItem.fromUri(manifest))

        player.apply {
            playWhenReady = true
            setMediaSource(mediaSource)
            prepare()
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        viewModelScope.launch {
                            _loadingState.value = false
                        }
                    }
                }
            })
        }
    }

    private fun onReadyError(message: String) {
        // Handle error
        Log.e("ExoPlayerViewModel", message)
    }

    fun releasePlayer() {
        player.release()
        p2pml?.stop()
    }

    fun updateP2PConfig(isP2PDisabled: Boolean) {
        val configJson = "{\"isP2PDisabled\": $isP2PDisabled}"
        p2pml?.applyDynamicConfig(configJson)
    }
}


@UnstableApi
class LoggingDataSourceFactory(
    context: Context,
) : DataSource.Factory {
    private val httpDataSourceFactory =
        DefaultHttpDataSource
            .Factory()
            // Set your connection parameters here
            .setConnectTimeoutMs(30000)
            // Set your read timeout here
            .setReadTimeoutMs(30000)

    private val baseDataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

    override fun createDataSource(): DataSource =
        LoggingDataSource(baseDataSourceFactory.createDataSource())
}


@UnstableApi
class LoggingDataSource(
    private val wrappedDataSource: DataSource,
) : DataSource {
    override fun open(dataSpec: DataSpec): Long {
        Log.d("HLSSegmentLogger", "Requesting: ${dataSpec.uri}")
        return try {
            wrappedDataSource.open(dataSpec)
        } catch (e: Exception) {
            Log.e("HLSSegmentLogger", "Error opening data source: ${e.message}", e)
            throw e
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        try {
            wrappedDataSource.read(buffer, offset, length)
        } catch (e: Exception) {
            Log.e("HLSSegmentLogger", "Error reading data source: ${e.message}", e)
            throw e
        }

    override fun addTransferListener(transferListener: TransferListener) {
        wrappedDataSource.addTransferListener(transferListener)
    }

    override fun getUri(): Uri? = wrappedDataSource.uri

    override fun close() {
        try {
            wrappedDataSource.close()
        } catch (e: Exception) {
            Log.e("HLSSegmentLogger", "Error closing data source: ${e.message}", e)
        }
    }
}
