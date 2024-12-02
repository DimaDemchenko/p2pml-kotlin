package com.example.p2pml.server

import androidx.media3.common.util.UnstableApi
import com.example.p2pml.utils.P2PStateManager
import com.example.p2pml.parser.HlsManifestParser
import com.example.p2pml.webview.WebViewManager
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.routing.routing
import okhttp3.OkHttpClient

@UnstableApi
internal class ServerModule(
    private val webViewManager: WebViewManager,
    private val manifestParser: HlsManifestParser,
    private val p2pEngineStateManager: P2PStateManager,
    private val customP2pmlImplementationPath: String? = null,
    private val onServerStarted: () -> Unit
) {
    private val httpClient: OkHttpClient = OkHttpClient()
    private var server: ApplicationEngine? = null

    fun startServer(port: Int = 8080) {
        if (server != null) return

        val manifestHandler = ManifestHandler(httpClient, manifestParser, webViewManager)
        val segmentHandler = SegmentHandler(
            httpClient, webViewManager,
            manifestParser, p2pEngineStateManager
        )

        val routingModule = ServerRoutes(
            manifestHandler,
            segmentHandler,
            customP2pmlImplementationPath
        )

        server = embeddedServer(CIO, port) {
            install(CORS) {
                anyHost()
            }

            routing {
                routingModule.setup(this)
            }

            environment.monitor.subscribe(ApplicationStarted) {
                onServerStarted()
            }
        }.start(wait = false)
    }

    fun stopServer() {
        server?.stop(1000, 1000)
        server = null
    }
}