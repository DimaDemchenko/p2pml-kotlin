package com.novage.p2pml.service

import com.novage.p2pml.UpdateStreamParams

internal class UpdateParamsStore {
    private val store = mutableMapOf<String, UpdateStreamParams>()
    fun put(id: String, params: UpdateStreamParams) { store[id] = params }
    fun get(id: String) = store[id]
    fun clearAll() = store.clear()
}