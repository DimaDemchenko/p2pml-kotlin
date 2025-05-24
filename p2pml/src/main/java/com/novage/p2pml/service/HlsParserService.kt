package com.novage.p2pml.service

import androidx.media3.common.util.UnstableApi
import com.novage.p2pml.parser.PlaylistParserFactory
import com.novage.p2pml.providers.PlaybackProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

@UnstableApi
internal class HlsParserService(
    private val playbackProvider: PlaybackProvider,
    private val serverPort: Int,
) {
    private val streamRegistry = StreamRegistry()
    private val segmentRepo = SegmentRepository()
    private val updateStore = UpdateParamsStore()
    private val playlistParserFactory = PlaylistParserFactory()
    private val mutex = Mutex()

    suspend fun isCurrentSegment(segmentRuntimeUrl: String): Boolean =
        mutex.withLock {
            segmentRepo.containsRuntimeId(segmentRuntimeUrl)
        }

    suspend fun getUpdateStreamParamsJson(variantUrl: String): String? {
        mutex.withLock {
            val updateStream = updateStore.get(variantUrl) ?: return null

            return Json.encodeToString(updateStream)
        }
    }

    suspend fun getStreamsJson(): String =
        mutex.withLock {
            Json.encodeToString(streamRegistry.getAll())
        }

    suspend fun doesManifestExist(manifestUrl: String): Boolean =
        mutex.withLock {
            streamRegistry.doesManifestExist(manifestUrl)
        }

    suspend fun reset() {
        mutex.withLock {
            streamRegistry.clearAll()
            segmentRepo.clearAll()
            updateStore.clearAll()
        }
    }

    suspend fun parse(
        manifestUrl: String,
        rawManifest: String,
    ): String =
        mutex.withLock {
            return playlistParserFactory.parse(
                manifestUrl,
                rawManifest,
                streamRegistry,
                segmentRepo,
                updateStore,
                playbackProvider,
                serverPort,
            )
        }
}
