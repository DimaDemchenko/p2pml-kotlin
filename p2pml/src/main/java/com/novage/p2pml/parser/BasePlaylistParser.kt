package com.novage.p2pml.parser

internal interface PlaylistParser {
    suspend fun parse(rawManifest: String, context: ParserContext): String
}

internal abstract class BasePlaylistParser(protected val serverPort: Int): PlaylistParser {
    override suspend fun parse(rawManifest: String, context: ParserContext): String {
        val document = loadManifest(rawManifest)

        prepareParser(context, document)
        processEntries(context, document)

        return renderDocument(document)
    }

    protected abstract fun loadManifest(raw: String): ManifestDocument
    protected abstract fun prepareParser(ctx: ParserContext, doc: ManifestDocument)
    protected abstract suspend fun processEntries(ctx: ParserContext, doc: ManifestDocument )
    protected abstract fun renderDocument(doc: ManifestDocument): String

}