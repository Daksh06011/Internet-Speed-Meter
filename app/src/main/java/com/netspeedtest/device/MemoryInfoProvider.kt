package com.netspeedtest.device

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

data class MemorySnapshot(
    val totalBytes: Long,
    val availableBytes: Long,
    val thresholdBytes: Long,
    val lowMemory: Boolean,
    /** Proportional set size of this app, in bytes. */
    val appPssBytes: Long?,
) {
    val usedBytes: Long get() = (totalBytes - availableBytes).coerceAtLeast(0)
    val usedFraction: Float get() = if (totalBytes <= 0) 0f else usedBytes.toFloat() / totalBytes
}

class MemoryInfoProvider(context: Context) {
    private val activityManager = context.getSystemService(ActivityManager::class.java)

    fun snapshot(includeApp: Boolean = false): MemorySnapshot? {
        val manager = activityManager ?: return null
        val info = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        if (info.totalMem <= 0) return null // never show fabricated zeros
        return MemorySnapshot(
            totalBytes = info.totalMem,
            availableBytes = info.availMem,
            thresholdBytes = info.threshold,
            lowMemory = info.lowMemory,
            appPssBytes = if (includeApp) Debug.getPss().takeIf { it > 0 }?.times(1024) else null,
        )
    }

    fun observe(intervalMs: Long = 3_000L, includeApp: Boolean = false): Flow<MemorySnapshot?> = flow {
        while (true) {
            emit(snapshot(includeApp))
            delay(intervalMs)
        }
    }.flowOn(Dispatchers.Default)
}
