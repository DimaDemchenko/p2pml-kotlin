package com.novage.p2pml.builder

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.novage.p2pml.Constants
import com.novage.p2pml.P2PMediaLoader
import com.novage.p2pml.interop.OnP2PReadyCallback
import com.novage.p2pml.interop.OnP2PReadyErrorCallback

class P2PMediaLoaderBuilder(
    private val context: Context,
) {
    private var onReady: OnP2PReadyCallback? = null
    private var onError: OnP2PReadyErrorCallback? = null
    private var coreConfigJson: String = ""
    private var serverPort: Int = Constants.DEFAULT_SERVER_PORT
    private val customJsIfs = mutableListOf<Pair<String, Any>>()
    private var enableDebugLogs: Boolean = false

    fun onReady(callback: OnP2PReadyCallback) = apply {
        this.onReady = callback
    }

    fun onError(callback: OnP2PReadyErrorCallback) = apply {
        this.onError = callback
    }

    /**
     * Supply core P2P config as JSON string.
     */
    fun coreConfig(json: String) = apply {
        this.coreConfigJson = json
    }

    /**
     * Sets the local HTTP server port (default 8080).
     */
    fun serverPort(port: Int) = apply {
        this.serverPort = port
    }

    /**
     * Enable verbose debug logging.
     */
    fun enableDebug() = apply {
        this.enableDebugLogs = true
    }

    /**
     * Inject a custom Javascript interface into the WebView.
     */
    fun addJavaScriptInterface(name: String, obj: Any) = apply {
        customJsIfs += name to obj
    }

    /**
     * Build the P2PMediaLoader instance.
     */
    @OptIn(UnstableApi::class)
    fun build(): P2PMediaLoader {
        requireNotNull(onReady) { "onReady callback must be provided" }
        requireNotNull(onError) { "onError callback must be provided" }

        return P2PMediaLoader(
            context = context,
            onP2PReadyCallback = onReady!!,
            onP2PReadyErrorCallback = onError!!,
            coreConfigJson = coreConfigJson,
            serverPort = serverPort,
            customJavaScriptInterfaces = customJsIfs,
            enableDebugLogs = enableDebugLogs
        )
    }
}