package com.novage.p2pml.utils

import com.novage.p2pml.CoreEventMap
import com.novage.p2pml.interop.EventListener

class EventEmitter {
    private val listeners = mutableMapOf<CoreEventMap<*>, MutableList<EventListener<*>>>()

    fun <T> addEventListener(
        event: CoreEventMap<T>,
        listener: EventListener<T>,
    ) {
        val list = listeners.getOrPut(event) { mutableListOf() }
        list.add(listener)
    }

    fun <T> emit(
        event: CoreEventMap<T>,
        data: T,
    ) {
        listeners[event]?.forEach { listener ->
            @Suppress("UNCHECKED_CAST")
            (listener as EventListener<T>).onEvent(data)
        }
    }

    fun <T> removeEventListener(
        event: CoreEventMap<T>,
        listener: EventListener<T>,
    ) {
        listeners[event]?.remove(listener)
    }

    fun <T> hasListeners(event: CoreEventMap<T>): Boolean = listeners[event]?.isNotEmpty() ?: false

    fun removeAllListeners() {
        listeners.clear()
    }
}
