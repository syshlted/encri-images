/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.device

import android.graphics.BitmapFactory
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.systemhalted.encri.client.EncriDocument
import com.systemhalted.encri.client.EncriResult
import com.systemhalted.encri.client.valueOrNull
import com.systemhalted.encri.images.device.OnPhone.context
import com.systemhalted.encri.images.device.OnPhone.heldHeap
import com.systemhalted.encri.images.device.OnPhone.log
import com.systemhalted.encri.images.device.OnPhone.ok
import com.systemhalted.encri.images.device.OnPhone.spread
import com.systemhalted.encri.images.device.OnPhone.timed
import com.systemhalted.encri.images.models.Media
import com.systemhalted.encri.images.models.MediaType
import com.systemhalted.encri.images.vault.Library
import com.systemhalted.encri.images.vault.LinkState
import com.systemhalted.encri.images.vault.VaultSession
import com.systemhalted.encri.images.vault.VaultUris
import com.systemhalted.encri.images.vault.walkLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What the vault costs this app on this phone (I6; `ROADMAP.md`, *Measure a real library*): how long the walk
 * takes and what it holds, and how fast encri's previews and a whole picture arrive.
 *
 * Through the app's own link and grant, so what is measured is what the app does. It skips when there is no
 * session to measure. Arguments, each optional: `walks` (3), `previews` (48), `previewSize` (180, a grid cell on
 * a phone 720 pixels wide), `concurrency` (4, Glide's own for a phone of four cores or more) and `reads` (3).
 */
@RunWith(AndroidJUnit4::class)
class WalkMeasurement {
    @Test
    fun measure() {
        val state = OnPhone.settled()
        assumeTrue("no session to measure: ${state.javaClass.simpleName}", state is LinkState.Open && !state.locked)
        val session = (state as LinkState.Open).session
        log("measure: API ${Build.VERSION.SDK_INT}, ${Build.MODEL}")
        val library = walk(session)
        previews(session, library.medias.filter { it.mediaType == MediaType.IMAGE })
        reads(session, library.medias.filter { it.mediaType == MediaType.IMAGE })
    }

    /** The walk, [OnPhone.int] `walks` times; the library the last one found stays held while its heap is read. */
    private fun walk(session: VaultSession): Library {
        var library: Library? = null
        repeat(OnPhone.int("walks", WALKS)) { run ->
            library = null
            val before = heldHeap()
            val counting = CountingSession(session)
            val (walked, millis) = timed { runBlocking { walkLibrary(counting) } }
            library = ok(walked)
            val held = heldHeap() - before
            val found = requireNotNull(library)
            log(
                "walk ${run + 1}: $millis ms; ${counting.folders.size} folders listed holding ${counting.entries} " +
                    "entries; ${found.medias.size} photos and videos in ${found.albums.size} albums; " +
                    "a folder listed in ${counting.folders.map { it.second }.spread()}; " +
                    "the largest folder, of ${counting.largest.first} entries, in ${counting.largest.second} ms; " +
                    "${held / KIBIBYTE} KiB held",
            )
        }
        return requireNotNull(library)
    }

    /** encri's previews at a grid cell's size: one at a time, the same again, and as Glide asks for them. */
    private fun previews(
        session: VaultSession,
        photos: List<Media>,
    ) {
        val ids = photos.take(OnPhone.int("previews", PREVIEWS)).map(::idOf)
        if (ids.isEmpty()) return log("previews: no photos")
        val size = OnPhone.int("previewSize", PREVIEW_SIZE)
        val sizes = mutableListOf<Int>()
        val first =
            ids.map { id ->
                timed { ok(runBlocking { session.thumbnail(id, size, size) }) }
                    .also {
                        sizes +=
                            it.first.size
                    }.second
            }
        val again = ids.map { id -> timed { ok(runBlocking { session.thumbnail(id, size, size) }) }.second }
        val concurrency = OnPhone.int("concurrency", CONCURRENCY)
        val permits = Semaphore(concurrency)
        val (_, together) =
            timed {
                runBlocking {
                    ids
                        .map { id ->
                            async(Dispatchers.IO) { permits.withPermit { ok(session.thumbnail(id, size, size)) } }
                        }.awaitAll()
                }
            }
        log(
            "previews of ${size}x$size, ${sizes.sorted()[sizes.size / 2] / KIBIBYTE} KiB each at the median: " +
                "first asked ${first.spread()}; asked again ${again.spread()}; " +
                "${ids.size} asked $concurrency at a time in $together ms, ${ids.size * MILLIS /
                    together.coerceAtLeast(
                        1,
                    )
                } a second",
        )
    }

    /**
     * The largest photos read whole, as the viewer reads one, and decoded to the screen's size, as Glide does for
     * the viewer: the wait between a tap and a picture.
     */
    private fun reads(
        session: VaultSession,
        photos: List<Media>,
    ) {
        val screen = context.resources.displayMetrics
        photos.sortedByDescending { it.sizeBytes }.take(OnPhone.int("reads", READS)).forEach { photo ->
            val (descriptor, opened) = timed { ok(runBlocking { session.openRead(idOf(photo)) }) }
            val (bytes, read) = timed { ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() } }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val sample =
                generateSequence(1) { it * 2 }.first {
                    bounds.outWidth / (it * 2) < screen.widthPixels || bounds.outHeight / (it * 2) < screen.heightPixels
                }
            val (_, decoded) =
                timed {
                    BitmapFactory
                        .decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                        .recycle()
                }
            log(
                "read: ${bounds.outWidth}x${bounds.outHeight}, ${bytes.size / KIBIBYTE} KiB, opened in $opened ms, " +
                    "read in $read ms (${bytes.size * MILLIS / MEBIBYTE / read.coerceAtLeast(1)} MiB/s), " +
                    "decoded at 1/$sample in $decoded ms",
            )
        }
    }

    private fun idOf(media: Media): String = requireNotNull(VaultUris.documentIdOf(media.uri))

    /** The session, with each folder's listing timed: its entries, and how long it took. */
    private class CountingSession(
        private val session: VaultSession,
    ) : VaultSession by session {
        val folders = mutableListOf<Pair<Int, Long>>()
        val entries get() = folders.sumOf { it.first }
        val largest get() = folders.maxByOrNull { it.first } ?: (0 to 0L)

        override suspend fun list(folderId: String): EncriResult<List<EncriDocument>> {
            val started = SystemClock.elapsedRealtime()
            val listed = session.list(folderId)
            folders += (listed.valueOrNull()?.size ?: 0) to SystemClock.elapsedRealtime() - started
            return listed
        }

        // The app's session, which the measurement borrows and does not end.
        override fun close() = Unit
    }

    private companion object {
        const val WALKS = 3
        const val PREVIEWS = 48
        const val PREVIEW_SIZE = 180
        const val CONCURRENCY = 4
        const val READS = 3
        const val KIBIBYTE = 1024
        const val MEBIBYTE = 1024 * 1024
        const val MILLIS = 1000L
    }
}
