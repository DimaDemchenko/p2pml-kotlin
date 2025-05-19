package com.novage.p2pml.service

import com.novage.p2pml.Segment

internal class SegmentRepository {
    private val data = mutableMapOf<String, MutableMap<Long, Segment>>()
    private val segmentRuntimeIds = mutableSetOf<String>()

    fun clearRuntimeIds() = segmentRuntimeIds.clear()
    fun addRuntimeId(id: String) = segmentRuntimeIds.add(id)
    fun containsRuntimeId(id: String) = segmentRuntimeIds.contains(id)


    fun clearFor(url: String) = data[url]?.clear()
    fun addSegment(url: String, seg: Segment) {
        data.getOrPut(url) { mutableMapOf() }[seg.externalId] = seg
    }
    fun isSegmentPresent(url: String, segId: Long): Boolean {
        return data[url]?.containsKey(segId) ?: false
    }
    fun getSegment(url: String, segId: Long): Segment? {
        return data[url]?.get(segId)
    }

    fun removeObsolete(url: String, threshold: Long): List<String> {
        val map = data[url] ?: return emptyList()
        val removed = map.filterKeys { it < threshold }.values.map { it.runtimeId }
        removed.forEach { rid -> map.values.removeIf { it.runtimeId == rid } }
        return removed
    }

    fun clearAll() {
        data.clear()
        segmentRuntimeIds.clear()
    }
}