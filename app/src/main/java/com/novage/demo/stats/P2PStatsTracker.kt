package com.novage.demo.stats

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.novage.p2pml.*
import com.novage.p2pml.CoreEventMap
import com.novage.p2pml.interop.EventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class P2PStats(
    val bytesDownloadedHttp: Long = 0L,
    val bytesDownloadedP2p: Long = 0L,
    val bytesUploaded: Long = 0L,
    val connectedPeers: Set<String> = emptySet(),
)

class P2PStatsTracker @OptIn(UnstableApi::class) constructor
    (private val p2pMediaLoader: P2PMediaLoader) {

    private val _statsFlow = MutableStateFlow(P2PStats())
    val statsFlow: StateFlow<P2PStats> = _statsFlow

    private val chunkDownloadedListener = object : EventListener<ChunkDownloadedDetails> {
        override fun onEvent(data: ChunkDownloadedDetails) {
            if (data.downloadSource == "http") {
                _statsFlow.value = _statsFlow.value.copy(
                    bytesDownloadedHttp = _statsFlow.value.bytesDownloadedHttp + data.bytesLength
                )
            } else if (data.downloadSource == "p2p") {
                _statsFlow.value = _statsFlow.value.copy(
                    bytesDownloadedP2p = _statsFlow.value.bytesDownloadedP2p + data.bytesLength
                )
            }
        }
    }

    private val chunkUploadedListener = object : EventListener<ChunkUploadedDetails> {
        override fun onEvent(data: ChunkUploadedDetails) {
            _statsFlow.value = _statsFlow.value.copy(
                bytesUploaded = _statsFlow.value.bytesUploaded + data.bytesLength
            )
        }
    }

    private val peerConnectListener = object : EventListener<PeerDetails> {
        override fun onEvent(data: PeerDetails) {
            val updatedPeers = _statsFlow.value.connectedPeers.toMutableSet().apply {
                add(data.peerId)
            }
            _statsFlow.value = _statsFlow.value.copy(connectedPeers = updatedPeers)
        }
    }

    private val peerCloseListener = object : EventListener<PeerDetails> {
        override fun onEvent(data: PeerDetails) {
            val updatedPeers = _statsFlow.value.connectedPeers.toMutableSet().apply {
                remove(data.peerId)
            }
            _statsFlow.value = _statsFlow.value.copy(connectedPeers = updatedPeers)
        }
    }


    @OptIn(UnstableApi::class)
    fun startTracking() {
        p2pMediaLoader.addEventListener(CoreEventMap.OnChunkDownloaded, chunkDownloadedListener)
        p2pMediaLoader.addEventListener(CoreEventMap.OnChunkUploaded, chunkUploadedListener)
        p2pMediaLoader.addEventListener(CoreEventMap.OnPeerConnect, peerConnectListener)
        p2pMediaLoader.addEventListener(CoreEventMap.OnPeerClose, peerCloseListener)
    }

    @OptIn(UnstableApi::class)
    fun stopTracking() {
        p2pMediaLoader.removeEventListener(CoreEventMap.OnChunkDownloaded, chunkDownloadedListener)
        p2pMediaLoader.removeEventListener(CoreEventMap.OnChunkUploaded, chunkUploadedListener)
        p2pMediaLoader.removeEventListener(CoreEventMap.OnPeerConnect, peerConnectListener)
        p2pMediaLoader.removeEventListener(CoreEventMap.OnPeerClose, peerCloseListener)
    }
}
