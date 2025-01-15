package com.novage.p2pml.providers

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import com.novage.p2pml.PlaybackInfo

interface PlaybackProvider {
    @OptIn(UnstableApi::class)
    suspend fun getAbsolutePlaybackPosition(parsedMediaPlaylist: HlsMediaPlaylist): Double

    suspend fun getPlaybackPositionAndSpeed(): PlaybackInfo

    suspend fun resetData()
}
