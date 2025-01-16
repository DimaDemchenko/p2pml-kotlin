package com.novage.p2pml.parser

import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser
import com.novage.p2pml.Constants.QueryParams
import com.novage.p2pml.Constants.StreamTypes
import com.novage.p2pml.Segment
import com.novage.p2pml.Stream
import com.novage.p2pml.UpdateStreamParams
import com.novage.p2pml.providers.PlaybackProvider
import com.novage.p2pml.utils.Utils
import io.ktor.http.encodeURLQueryComponent
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@UnstableApi
internal class HlsManifestParser(
    private val playbackProvider: PlaybackProvider,
    private val serverPort: Int,
) {
    private val parser = HlsPlaylistParser()
    private val mutex = Mutex()

    private var currentMasterManifestUrl: String? = null

    private val streams = mutableListOf<Stream>()
    private val streamSegments = mutableMapOf<String, MutableMap<Long, Segment>>()
    private val updateStreamParams = mutableMapOf<String, UpdateStreamParams>()

    private val currentSegmentRuntimeIds = mutableSetOf<String>()

    suspend fun getModifiedManifest(
        originalManifest: String,
        manifestUrl: String,
    ): String =
        mutex.withLock {
            parseHlsManifest(manifestUrl, originalManifest)
        }

    suspend fun isCurrentSegment(segmentUrl: String): Boolean =
        mutex.withLock {
            currentSegmentRuntimeIds.contains(segmentUrl)
        }

    suspend fun doesManifestExist(manifestUrl: String): Boolean =
        mutex.withLock {
            currentMasterManifestUrl == manifestUrl ||
                streams.any { it.runtimeId == manifestUrl }
        }

    private suspend fun parseHlsManifest(
        manifestUrl: String,
        manifest: String,
    ): String {
        val hlsPlaylist = parser.parse(manifestUrl.toUri(), manifest.byteInputStream())

        return when (hlsPlaylist) {
            is HlsMediaPlaylist -> parseMediaPlaylist(manifestUrl, hlsPlaylist, manifest)
            is HlsMultivariantPlaylist ->
                parseMultiVariantPlaylist(
                    manifestUrl,
                    hlsPlaylist,
                    manifest,
                )

            else -> throw IllegalStateException("Unsupported playlist type")
        }
    }

    private fun removeObsoleteSegments(
        variantUrl: String,
        removeUntilId: Long,
    ): List<String> {
        val segmentsMap = streamSegments[variantUrl] ?: return emptyList()
        val obsoleteSegments = segmentsMap.filterKeys { it < removeUntilId }

        obsoleteSegments.forEach { (id, _) ->
            segmentsMap.remove(id)
        }

        return obsoleteSegments.values.map { it.runtimeId }
    }

    private fun addNewSegment(
        variantUrl: String,
        segmentId: Long,
        initialStartTime: Double,
        segment: HlsMediaPlaylist.Segment,
    ): Segment? {
        val segmentsMap = streamSegments.getOrPut(variantUrl) { mutableMapOf() }
        if (segmentsMap.contains(segmentId)) return null

        val prevSegment = segmentsMap[segmentId - 1]

        val segmentDurationInSeconds = segment.durationUs / 1_000_000.0
        val startTime = prevSegment?.endTime ?: initialStartTime
        val endTime = startTime + segmentDurationInSeconds

        val absoluteUrl = segment.getAbsoluteUrl(variantUrl)
        val runtimeUrl = segment.getRuntimeUrl(variantUrl)

        val newSegment =
            Segment(
                runtimeId = runtimeUrl,
                externalId = segmentId,
                url = absoluteUrl,
                byteRange = segment.byteRange,
                startTime = startTime,
                endTime = endTime,
            )

        segmentsMap[segmentId] = newSegment

        return newSegment
    }

    private suspend fun getInitialStartTime(
        isLive: Boolean,
        mediaPlaylist: HlsMediaPlaylist,
    ): Double =
        if (isLive) {
            playbackProvider.getAbsolutePlaybackPosition(mediaPlaylist)
        } else {
            0.0
        }

    private suspend fun parseMediaPlaylist(
        manifestUrl: String,
        mediaPlaylist: HlsMediaPlaylist,
        originalManifest: String,
    ): String {
        val isStreamLive = !mediaPlaylist.hasEndTag
        val newMediaSequence = mediaPlaylist.mediaSequence
        val updatedManifestBuilder = StringBuilder(originalManifest)

        val segmentsToRemove = removeObsoleteSegments(manifestUrl, newMediaSequence)
        val initializationSegments = mutableSetOf<HlsMediaPlaylist.Segment>()
        val segmentsToAdd = mutableListOf<Segment>()

        val initialStartTime = getInitialStartTime(isStreamLive, mediaPlaylist)
        currentSegmentRuntimeIds.clear()
        mediaPlaylist.segments.forEachIndexed { index, segment ->
            if (segment.initializationSegment != null) {
                initializationSegments.add(segment.initializationSegment!!)
            }

            val segmentIndex = index + newMediaSequence

            val runtimeUrl = segment.getRuntimeUrl(manifestUrl)
            currentSegmentRuntimeIds.add(runtimeUrl)

            processSegment(segment, manifestUrl, updatedManifestBuilder)

            val newSegment =
                addNewSegment(manifestUrl, segmentIndex, initialStartTime, segment)
            if (newSegment != null) {
                segmentsToAdd.add(newSegment)
            }
        }

        initializationSegments.forEach { initializationSegment ->
            replaceUrlInManifest(
                originalManifest,
                manifestUrl,
                initializationSegment.url,
                updatedManifestBuilder,
            )
        }

        updateStreamData(manifestUrl, segmentsToAdd, segmentsToRemove, isStreamLive)

        val stream = findStreamByRuntimeId(manifestUrl)
        // This should be fired if there is no master manifest
        if (stream == null) {
            streams.add(Stream(runtimeId = manifestUrl, type = StreamTypes.MAIN, index = 0))
        }

        return updatedManifestBuilder.toString()
    }

    suspend fun getUpdateStreamParamsJson(variantUrl: String): String? {
        mutex.withLock {
            val updateStream =
                updateStreamParams[variantUrl]
                    ?: return null

            return Json.encodeToString(updateStream)
        }
    }

    suspend fun getStreamsJson(): String =
        mutex.withLock {
            Json.encodeToString(streams)
        }

    private fun parseMultiVariantPlaylist(
        manifestUrl: String,
        hlsPlaylist: HlsMultivariantPlaylist,
        originalManifest: String,
    ): String {
        val updatedManifestBuilder = StringBuilder(originalManifest)
        currentMasterManifestUrl = manifestUrl

        hlsPlaylist.variants.forEachIndexed { index, variant ->
            processVariant(
                variant,
                index,
                originalManifest,
                manifestUrl,
                updatedManifestBuilder,
            )
        }

        hlsPlaylist.audios.forEachIndexed { index, audio ->
            processRendition(
                audio,
                index,
                originalManifest,
                manifestUrl,
                updatedManifestBuilder,
            )
        }

        hlsPlaylist.subtitles.forEach { subtitle ->
            replaceUrlInManifest(
                originalManifest,
                manifestUrl,
                subtitle.url.toString(),
                updatedManifestBuilder,
            )
        }

        hlsPlaylist.closedCaptions.forEach { caption ->
            replaceUrlInManifest(
                originalManifest,
                manifestUrl,
                caption.url.toString(),
                updatedManifestBuilder,
            )
        }
        return updatedManifestBuilder.toString()
    }

    private fun updateStreamData(
        variantUrl: String,
        newSegments: List<Segment>,
        segmentsToRemove: List<String>,
        isLive: Boolean,
    ) {
        val updateStream =
            UpdateStreamParams(
                streamRuntimeId = variantUrl,
                addSegments = newSegments,
                removeSegmentsIds = segmentsToRemove,
                isLive = isLive,
            )

        updateStreamParams[variantUrl] = updateStream
    }

    private fun findStreamByRuntimeId(runtimeId: String): Stream? =
        streams.find {
            it.runtimeId == runtimeId
        }

    private fun processVariant(
        variant: HlsMultivariantPlaylist.Variant,
        index: Int,
        manifest: String,
        manifestUrl: String,
        updatedManifestBuilder: StringBuilder,
    ) {
        val streamUrl = variant.url.toString()
        streams.add(Stream(runtimeId = streamUrl, type = StreamTypes.MAIN, index = index))

        replaceUrlInManifest(
            manifest,
            manifestUrl,
            streamUrl,
            updatedManifestBuilder,
            QueryParams.MANIFEST,
        )
    }

    private fun processRendition(
        rendition: HlsMultivariantPlaylist.Rendition,
        index: Int,
        manifest: String,
        manifestUrl: String,
        updatedManifestBuilder: StringBuilder,
    ) {
        val streamUrl = rendition.url.toString()
        streams.add(Stream(runtimeId = streamUrl, type = StreamTypes.SECONDARY, index = index))

        replaceUrlInManifest(
            manifest,
            manifestUrl,
            streamUrl,
            updatedManifestBuilder,
            QueryParams.MANIFEST,
        )
    }

    private fun processSegment(
        segment: HlsMediaPlaylist.Segment,
        variantUrl: String,
        manifestBuilder: StringBuilder,
    ) {
        val segmentUriInManifest =
            findUrlInManifest(
                manifestBuilder.toString(),
                segment.url,
                variantUrl,
            )
        val absoluteSegmentUrl = segment.getAbsoluteUrl(variantUrl)
        val byteRange = segment.byteRange

        val encodedAbsoluteSegmentUrl =
            if (byteRange != null) {
                Utils.encodeUrlToBase64("$absoluteSegmentUrl|${byteRange.start}-${byteRange.end}")
            } else {
                Utils.encodeUrlToBase64(absoluteSegmentUrl)
            }

        val newUrl =
            Utils.getUrl(
                serverPort,
                "${QueryParams.SEGMENT}$encodedAbsoluteSegmentUrl",
            )

        val startIndex =
            manifestBuilder
                .indexOf(segmentUriInManifest)
                .takeIf { it != -1 }
                ?: throw IllegalStateException("URL not found in manifest: $segment.url")
        val endIndex = startIndex + segmentUriInManifest.length

        manifestBuilder.replace(
            startIndex,
            endIndex,
            newUrl,
        )
    }

    private fun replaceUrlInManifest(
        manifest: String,
        baseManifestUrl: String,
        originalUrl: String,
        updatedManifestBuilder: StringBuilder,
        queryParam: String? = null,
    ) {
        val urlToFind = findUrlInManifest(manifest, originalUrl, baseManifestUrl)
        val absoluteUrl =
            Utils.getAbsoluteUrl(baseManifestUrl, originalUrl).encodeURLQueryComponent()
        val newUrl =
            if (queryParam != null) {
                Utils.getUrl(serverPort, "$queryParam$absoluteUrl")
            } else {
                absoluteUrl
            }

        val startIndex =
            updatedManifestBuilder
                .indexOf(urlToFind)
                .takeIf { it != -1 }
                ?: throw IllegalStateException("URL not found in manifest: $originalUrl")
        val endIndex = startIndex + urlToFind.length
        updatedManifestBuilder.replace(startIndex, endIndex, newUrl)
    }

    private fun findUrlInManifest(
        manifest: String,
        urlToFind: String,
        manifestUrl: String,
    ): String {
        val baseManifestURL = manifestUrl.substringBeforeLast("/") + "/"
        val relativeUrlToFind = urlToFind.removePrefix(baseManifestURL)

        return when {
            manifest.contains(urlToFind) -> urlToFind
            manifest.contains(relativeUrlToFind) -> relativeUrlToFind
            else -> throw IllegalStateException(
                "URL not found in manifest. urlToFind:" +
                    "$urlToFind, manifestUrl: $manifestUrl",
            )
        }
    }

    suspend fun reset() {
        mutex.withLock {
            streams.clear()
            streamSegments.clear()
            updateStreamParams.clear()
            currentSegmentRuntimeIds.clear()
        }
    }
}
