package com.example.p2pml

import android.content.Context
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.media3.common.util.UnstableApi
import com.example.p2pml.Constants.QueryParams.MANIFEST
import com.example.p2pml.Constants.CORE_FILE_PATH
import com.example.p2pml.parser.HlsManifestParser
import com.example.p2pml.server.ServerModule
import com.example.p2pml.utils.Utils
import com.example.p2pml.webview.WebViewManager
import io.ktor.http.encodeURLQueryComponent
import kotlinx.coroutines.CompletableDeferred

@UnstableApi
class P2PML(
    context: Context,
    coroutineScope: LifecycleCoroutineScope,
    private val serverPort: Int = Constants.DEFAULT_SERVER_PORT
) {
    private val webViewManager: WebViewManager = WebViewManager(context, coroutineScope) {
        webViewLoadDeferred.complete(Unit)
    }
    private val manifestParser: HlsManifestParser = HlsManifestParser(serverPort)
    private val serverModule: ServerModule = ServerModule(webViewManager, manifestParser) {
        onServerStarted()
    }
    private val webViewLoadDeferred = CompletableDeferred<Unit>()

    init {
        startServer()
    }

    private fun startServer() {
        serverModule.startServer(serverPort)
    }

    private fun onServerStarted() {
        webViewManager.loadWebView(Utils.getUrl(serverPort, CORE_FILE_PATH))
    }

    suspend fun getServerManifestUrl(manifestUrl: String): String {
        webViewLoadDeferred.await()
        val encodedManifestURL = manifestUrl.encodeURLQueryComponent()
        return Utils.getUrl(serverPort, "$MANIFEST$encodedManifestURL")
    }

    fun stopServer() {
        serverModule.stopServer()
        webViewManager.destroy()
    }

    fun setUpPlaybackInfoCallback(callback: () -> Pair<Float, Float>) {
        webViewManager.setUpPlaybackInfoCallback(callback)
    }
}