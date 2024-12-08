package com.example.p2pml.utils

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class P2PStateManager {
    private var isP2PEngineEnabled = true
    private val mutex = Mutex()

    suspend fun isP2PEngineEnabled(): Boolean {
        return mutex.withLock {
            isP2PEngineEnabled
        }
    }

    suspend fun changeP2PEngineStatus(isP2PEngineStatusEnabled: Boolean) = mutex.withLock {
        if (isP2PEngineStatusEnabled == isP2PEngineEnabled) return@withLock

        isP2PEngineEnabled = isP2PEngineStatusEnabled
    }
}