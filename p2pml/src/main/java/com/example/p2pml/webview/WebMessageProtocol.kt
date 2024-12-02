package com.example.p2pml.webview

import android.annotation.SuppressLint
import android.net.Uri
import android.util.Log
import android.webkit.WebView
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebMessagePortCompat
import androidx.webkit.WebViewCompat
import com.example.p2pml.SegmentRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class WebMessageProtocol(
    private val webView: WebView,
    private val coroutineScope: CoroutineScope
) {
    @SuppressLint("RequiresFeature")
    private val channels: Array<WebMessagePortCompat> =
        WebViewCompat.createWebMessageChannel(webView)
    private val segmentResponseCallbacks = mutableMapOf<String, CompletableDeferred<ByteArray>>()
    private val mutex = Mutex()
    private var incomingSegmentRequest: String? = null

    init {
        setupWebMessageCallback()
    }

    @SuppressLint("RequiresFeature")
    private fun setupWebMessageCallback() {
        channels[0].setWebMessageCallback(
            object : WebMessagePortCompat.WebMessageCallbackCompat() {
                override fun onMessage(port: WebMessagePortCompat, message: WebMessageCompat?) {
                    when (message?.type) {
                        WebMessageCompat.TYPE_ARRAY_BUFFER -> {
                            handleSegmentIdBytes(message.arrayBuffer)
                        }

                        WebMessageCompat.TYPE_STRING -> {
                            handleMessage(message.data!!)
                        }
                    }
                }
            },
        )
    }

    private fun handleSegmentIdBytes(arrayBuffer: ByteArray) {
        if (incomingSegmentRequest == null) {
            throw IllegalStateException("Received segment bytes without a segment ID")
        }

        coroutineScope.launch {
            val deferred = getSegmentResponseCallback(incomingSegmentRequest!!)

            if (deferred != null) {
                deferred.complete(arrayBuffer)
            } else {
                Log.d(
                    "WebMessageProtocol",
                    "Error: No deferred found for segment ID: $incomingSegmentRequest"
                )
            }

            removeSegmentResponseCallback(incomingSegmentRequest!!)
            incomingSegmentRequest = null
        }
    }

    private fun handleMessage(message: String) {
        if (message.contains("error"))
            handleErrorMessage(message)
        else
            handleSegmentIdMessage(message)
    }

    private fun handleErrorMessage(message: String) {
        coroutineScope.launch {
            message.substringAfter("error|").let {
                val deferred = getSegmentResponseCallback(it)
                if (deferred != null) {
                    deferred.completeExceptionally(
                        Exception("Error occurred while fetching segment")
                    )
                    Log.d(
                        "WebMessageProtocol",
                        "Completed deferred with error for segment ID: $it"
                    )
                } else {
                    Log.d(
                        "WebMessageProtocol",
                        "Error: No deferred found for segment ID: $it"
                    )
                }
            }
        }
    }


    private fun handleSegmentIdMessage(segmentId: String) {
        if (incomingSegmentRequest != null) {
            Log.d(
                "WebMessageProtocol",
                "Error: Received segment ID while another request is pending"
            )
        }
        incomingSegmentRequest = segmentId
    }

    @SuppressLint("RequiresFeature")
    suspend fun sendInitialMessage() {
        withContext(Dispatchers.Main) {
            val initialMessage = WebMessageCompat("", arrayOf(channels[1]))
            WebViewCompat.postWebMessage(
                webView,
                initialMessage,
                Uri.parse("*")
            )
        }
    }

    suspend fun requestSegmentBytes(
        segmentUrl: String,
    ): CompletableDeferred<ByteArray> {
        val deferred = CompletableDeferred<ByteArray>()
        val segmentRequest = SegmentRequest(segmentUrl)
        val jsonRequest = Json.encodeToString(segmentRequest)

        addSegmentResponseCallback(segmentUrl, deferred)

        sendSegmentRequest(jsonRequest)

        return deferred
    }

    private suspend fun sendSegmentRequest(segmentUrl: String) {
        withContext(Dispatchers.Main) {
            webView.evaluateJavascript(
                "javascript:window.p2p.processSegmentRequest('$segmentUrl');",
                null
            )
        }
    }

    private suspend fun addSegmentResponseCallback(
        segmentId: String,
        deferred: CompletableDeferred<ByteArray>
    ) {
        mutex.withLock {
            segmentResponseCallbacks[segmentId] = deferred
        }
    }

    private suspend fun getSegmentResponseCallback(
        segmentId: String
    ): CompletableDeferred<ByteArray>? {
        return mutex.withLock {
            segmentResponseCallbacks[segmentId]
        }
    }

    private suspend fun removeSegmentResponseCallback(segmentId: String) {
        mutex.withLock {
            segmentResponseCallbacks.remove(segmentId)
        }
    }
}
