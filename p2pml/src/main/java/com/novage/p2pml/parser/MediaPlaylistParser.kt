package com.novage.p2pml.parser

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import com.novage.p2pml.Constants.QueryParams
import com.novage.p2pml.Constants.StreamTypes
import com.novage.p2pml.Segment
import com.novage.p2pml.Stream
import com.novage.p2pml.UpdateStreamParams
import com.novage.p2pml.utils.Utils
import com.novage.p2pml.utils.Utils.findUrlInManifest

@UnstableApi
internal class MediaPlaylistParser(
    serverPort: Int,
): BasePlaylistParser(serverPort) {


    override fun loadManifest(raw: String): ManifestDocument {
        return ManifestDocument(raw, StringBuilder(raw))
    }

    override fun prepareParser(ctx: ParserContext, doc: ManifestDocument) {
        ctx.segmentRepo.clearRuntimeIds()
    }

    override suspend fun processEntries(ctx: ParserContext, doc: ManifestDocument) {
        val mediaPlaylist = ctx.hlsPlaylist as HlsMediaPlaylist
        val manifestUrl = ctx.manifestUrl

        val isStreamLive = !mediaPlaylist.hasEndTag
        val newMediaSequence = mediaPlaylist.mediaSequence

        val segmentsToRemove = ctx.segmentRepo.removeObsolete(manifestUrl, newMediaSequence)
        val initializationSegments = mutableSetOf<HlsMediaPlaylist.Segment>()
        val segmentsToAdd = mutableListOf<Segment>()

        val initialStartTime = getInitialStartTime(ctx, isStreamLive, mediaPlaylist)

        mediaPlaylist.segments.forEachIndexed { index, segment ->
            if (segment.initializationSegment != null) {
                initializationSegments.add(segment.initializationSegment!!)
            }

            val segmentIndex = index + newMediaSequence

            processSegment(segment, manifestUrl, doc.builder)

            val newSegment =
                addNewSegment(ctx, manifestUrl, segmentIndex, initialStartTime, segment)
            if (newSegment != null) {
                segmentsToAdd.add(newSegment)
            }
        }

        initializationSegments.forEach { initializationSegment ->
            Utils.replaceToProxyUrl(
                doc.raw,
                manifestUrl,
                initializationSegment.url,
                doc.builder,
                serverPort,
            )
        }

        updateStreamData(
            ctx,
            manifestUrl,
            segmentsToAdd,
            segmentsToRemove,
            isLive = isStreamLive,
        )
        updateStreamRegistry(ctx, manifestUrl)
    }

    private fun updateStreamRegistry(
        ctx: ParserContext,
        variantUrl: String,
    ) {
        val stream = ctx.streamRegistry.find(variantUrl)
        if (stream != null) return

        ctx.streamRegistry.add(
            Stream(runtimeId = variantUrl, type = StreamTypes.MAIN, index = 0, null)
        )

    }

    private fun updateStreamData(
        ctx: ParserContext,
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

        ctx.updateStore.put(variantUrl, updateStream)
    }

    private fun addNewSegment(
        ctx: ParserContext,
        variantUrl: String,
        segmentId: Long,
        initialStartTime: Double,
        segment: HlsMediaPlaylist.Segment,
    ): Segment? {
        val runtimeUrl = segment.getRuntimeUrl(variantUrl)
        ctx.segmentRepo.addRuntimeId(runtimeUrl)
        if (ctx.segmentRepo.isSegmentPresent(variantUrl, segmentId)) return null

        val prevSegment = ctx.segmentRepo.getSegment(variantUrl, segmentId - 1)

        val segmentDurationInSeconds = segment.durationUs / 1_000_000.0
        val startTime = prevSegment?.endTime ?: initialStartTime
        val endTime = startTime + segmentDurationInSeconds

        val absoluteUrl = segment.getAbsoluteUrl(variantUrl)


        val newSegment =
            Segment(
                runtimeId = runtimeUrl,
                externalId = segmentId,
                url = absoluteUrl,
                byteRange = segment.byteRange,
                startTime = startTime,
                endTime = endTime,
            )

        ctx.segmentRepo.addSegment(variantUrl, newSegment)

        return newSegment
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


    private suspend fun getInitialStartTime(
        ctx: ParserContext,
        isLive: Boolean,
        mediaPlaylist: HlsMediaPlaylist,
    ): Double =
        if (isLive) {
            ctx.playbackProvider.getAbsolutePlaybackPosition(mediaPlaylist)
        } else {
            0.0
        }


    override fun renderDocument(doc: ManifestDocument): String {
        return doc.builder.toString()
    }
}