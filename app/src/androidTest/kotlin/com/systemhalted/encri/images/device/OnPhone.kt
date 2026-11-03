/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.device

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.systemhalted.encri.client.EncriResult
import com.systemhalted.encri.images.ImagesApplication
import com.systemhalted.encri.images.vault.LinkState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * What the app's device tests share (I6). They run in the app's own process, as instrumentation does, so they
 * reach encri as the app does, through its one link and with its grant, and ask for nothing more.
 */
internal object OnPhone {
    const val TAG = "EncriImagesDevice"

    val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    val context: Context get() = instrumentation.targetContext
    val images: ImagesApplication get() = context.applicationContext as ImagesApplication

    /** The link's first answer: a session, or why there is none. */
    fun settled(): LinkState {
        images.link.open()
        return runBlocking {
            withTimeout(SETTLE_MILLIS) {
                images.link.state.first { it is LinkState.Open || it is LinkState.Refused }
            }
        }
    }

    fun int(
        key: String,
        default: Int,
    ): Int = InstrumentationRegistry.getArguments().getString(key)?.toInt() ?: default

    /** One line of what was measured. Counts and times only: nothing that names a document (encri D8). */
    fun log(line: String) {
        Log.i(TAG, line)
    }

    fun <T> ok(result: EncriResult<T>): T =
        when (result) {
            is EncriResult.Ok -> result.value
            is EncriResult.Failed -> throw AssertionError("failed: $result")
        }

    /** What [block] answers and how long it took, in milliseconds. */
    inline fun <T> timed(block: () -> T): Pair<T, Long> {
        val started = SystemClock.elapsedRealtimeNanos()
        val answer = block()
        return answer to (SystemClock.elapsedRealtimeNanos() - started) / NANOS_PER_MILLI
    }

    /** "median, 90th percentile and longest", of times in milliseconds. */
    fun List<Long>.spread(): String {
        if (isEmpty()) return "none"
        val sorted = sorted()
        val at = { part: Double -> sorted[((sorted.size - 1) * part).toInt()] }
        return "median ${at(MEDIAN)} ms, 90th ${at(NINETIETH)} ms, longest ${sorted.last()} ms, of $size"
    }

    /** The heap this process holds once the collector has run, in bytes. */
    fun heldHeap(): Long {
        repeat(COLLECTIONS) {
            Runtime.getRuntime().gc()
            SystemClock.sleep(COLLECTION_PAUSE_MILLIS)
        }
        return Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }
    }

    private const val SETTLE_MILLIS = 30_000L
    const val NANOS_PER_MILLI = 1_000_000L
    private const val MEDIAN = 0.5
    private const val NINETIETH = 0.9
    private const val COLLECTIONS = 3
    private const val COLLECTION_PAUSE_MILLIS = 100L
}
