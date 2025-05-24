package com.novage.p2pml.parser

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import com.novage.p2pml.Constants.QueryParams
import com.novage.p2pml.Constants.StreamTypes
import com.novage.p2pml.Stream
import com.novage.p2pml.utils.Utils

internal class MultivariantPlaylistParser(
    serverPort: Int,
) : BasePlaylistParser(serverPort) {
    override fun loadManifest(raw: String) = ManifestDocument(raw, StringBuilder(raw))

    override fun prepareParser(
        ctx: ParserContext,
        doc: ManifestDocument,
    ) { }

    @OptIn(UnstableApi::class)
    override suspend fun processEntries(
        ctx: ParserContext,
        doc: ManifestDocument,
    ) {
        val playlist = ctx.hlsPlaylist as HlsMultivariantPlaylist

        playlist.variants.forEachIndexed { idx, v ->
            processVariant(ctx, v, idx, doc)
        }

        playlist.audios.forEachIndexed { idx, a ->
            processRendition(ctx, a, idx, doc)
        }

        playlist.subtitles.forEach { s ->
            Utils.replaceToProxyUrl(
                doc.raw,
                ctx.manifestUrl,
                s.url.toString(),
                doc.builder,
                serverPort,
            )
        }

        playlist.closedCaptions.forEach { cc ->
            Utils.replaceToProxyUrl(
                doc.raw,
                ctx.manifestUrl,
                cc.url.toString(),
                doc.builder,
                serverPort,
            )
        }
    }

    @OptIn(UnstableApi::class)
    private fun processVariant(
        ctx: ParserContext,
        variant: HlsMultivariantPlaylist.Variant,
        index: Int,
        doc: ManifestDocument,
    ) {
        val manifestUrl = ctx.manifestUrl
        val streamUrl = variant.url.toString()
        val stream = Stream(runtimeId = streamUrl, type = StreamTypes.MAIN, index = index, manifestUrl)
        ctx.streamRegistry.add(stream)

        Utils.replaceToProxyUrl(
            doc.raw,
            manifestUrl,
            streamUrl,
            doc.builder,
            serverPort,
            QueryParams.MANIFEST,
        )
    }

    @OptIn(UnstableApi::class)
    private fun processRendition(
        ctx: ParserContext,
        rendition: HlsMultivariantPlaylist.Rendition,
        index: Int,
        doc: ManifestDocument,
    ) {
        val manifestUrl = ctx.manifestUrl
        val streamUrl = rendition.url.toString()
        val stream = Stream(runtimeId = streamUrl, type = StreamTypes.SECONDARY, index = index, manifestUrl)

        ctx.streamRegistry.add(stream)

        Utils.replaceToProxyUrl(
            doc.raw,
            manifestUrl,
            streamUrl,
            doc.builder,
            serverPort,
            QueryParams.MANIFEST,
        )
    }

    override fun renderDocument(doc: ManifestDocument) = doc.builder.toString()
}
