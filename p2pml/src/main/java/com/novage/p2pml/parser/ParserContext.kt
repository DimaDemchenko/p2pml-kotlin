package com.novage.p2pml.parser

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.hls.playlist.HlsPlaylist
import com.novage.p2pml.providers.PlaybackProvider
import com.novage.p2pml.service.SegmentRepository
import com.novage.p2pml.service.StreamRegistry
import com.novage.p2pml.service.UpdateParamsStore

@OptIn(UnstableApi::class)
internal data class ParserContext(
    val manifestUrl: String,
    val hlsPlaylist: HlsPlaylist,
    val streamRegistry: StreamRegistry,
    val segmentRepo: SegmentRepository,
    val updateStore: UpdateParamsStore,
    val playbackProvider: PlaybackProvider,
)

data class ManifestDocument(
    val raw: String,
    val builder: StringBuilder,
)
