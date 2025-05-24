package com.novage.p2pml.service

import com.novage.p2pml.Stream

internal class StreamRegistry {
    private val streams = mutableListOf<Stream>()

    fun add(s: Stream) {
        streams += s
    }

    fun find(id: String) = streams.find { it.runtimeId == id }

    fun clearAll() = streams.clear()

    fun doesManifestExist(manifestUrl: String): Boolean = streams.any { it.masterManifestUrl == manifestUrl || it.runtimeId == manifestUrl }

    fun getAll(): List<Stream> = streams
}
