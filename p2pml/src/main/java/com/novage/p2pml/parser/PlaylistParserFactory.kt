package com.novage.p2pml.parser

import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser
import com.novage.p2pml.providers.PlaybackProvider
import com.novage.p2pml.service.SegmentRepository
import com.novage.p2pml.service.StreamRegistry
import com.novage.p2pml.service.UpdateParamsStore

@UnstableApi
internal class PlaylistParserFactory {
    private val coreParser = HlsPlaylistParser()

    suspend fun parse(
        manifestUrl: String,
        rawManifest: String,
        streamRegistry: StreamRegistry,
        segmentRepo: SegmentRepository,
        updateStore: UpdateParamsStore,
        playbackProvider: PlaybackProvider,
        serverPort: Int,
    ): String {
        val parsed = coreParser.parse(manifestUrl.toUri(), rawManifest.byteInputStream())
        val ctx =
            ParserContext(
                manifestUrl,
                parsed,
                streamRegistry,
                segmentRepo,
                updateStore,
                playbackProvider,
            )

        val parser: PlaylistParser =
            when (parsed) {
                is HlsMediaPlaylist -> MediaPlaylistParser(serverPort)
                is HlsMultivariantPlaylist -> MultivariantPlaylistParser(serverPort)
                else -> throw IllegalArgumentException("Unsupported HLS playlist type")
            }

        return parser.parse(rawManifest, ctx)
    }
}
