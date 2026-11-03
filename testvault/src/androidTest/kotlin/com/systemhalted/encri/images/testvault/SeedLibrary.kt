/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.testvault

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Bundle
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.systemhalted.encri.client.EncriSession
import com.systemhalted.encri.client.NameConflict
import com.systemhalted.encri.images.testvault.TestVault.blocking
import com.systemhalted.encri.images.testvault.TestVault.log
import com.systemhalted.encri.images.testvault.TestVault.ok
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import kotlin.random.Random

/**
 * Seeds a library of photos into the vault on this phone, for the measurements and the device tests (I6).
 *
 * Everything goes under one new top folder, whose id is written to [TestVault.seeded] before anything is put in
 * it, so a seed that stops part way can still be removed, and [RemoveLibrary] deletes that folder and nothing
 * else. What to make comes from the instrumentation's arguments ([SeedPlan]); each count defaults to none.
 *
 * The pictures are made here, each one different, and dated back over [SeedPlan.years] so the Reels screen has
 * a date for every day or so, as a real library does.
 */
@RunWith(AndroidJUnit4::class)
class SeedLibrary {
    @Test
    fun seed() {
        val plan = SeedPlan.from(TestVault.arguments)
        log("seed: $plan")
        TestVault.use { session ->
            val top = ok(blocking { session.createFolder(session.grant.scope.id, TOP, NameConflict.PICK_FREE_NAME) })
            TestVault.seeded.appendText("${top.id}\n")
            val groups = layOut(session, top.id, plan)
            val total = groups.sumOf { it.count }
            val dates = Dates(total, plan.years)
            var index = 0
            groups.forEach { group ->
                runBlocking { write(session, group, index, dates, plan) }
                index += group.count
            }
            val totals = ok(blocking { session.measure(top.id) })
            log(
                "seed: done, ${totals.documents} documents in ${totals.folders} folders, " +
                    "${totals.bytes / MEBIBYTE} MiB, under a new top folder",
            )
        }
    }

    /** The folders, made, and what goes in each. */
    private fun layOut(
        session: EncriSession,
        top: String,
        plan: SeedPlan,
    ): List<Group> {
        fun folder(
            parent: String,
            name: String,
        ): String = ok(blocking { session.createFolder(parent, name) }).id

        val groups = mutableListOf<Group>()
        if (plan.camera > 0) groups += Group(folder(top, "Camera"), plan.camera, plan.width, plan.height)
        if (plan.albums > 0 && plan.perAlbum > 0) {
            val albums = folder(top, "Albums")
            repeat(plan.albums) { groups += Group(folder(albums, "Album %03d".format(it + 1)), plan.perAlbum, plan) }
        }
        if (plan.nested > 0) {
            val deepest = (1..NESTED_DEPTH).fold(folder(top, "Nested")) { parent, level -> folder(parent, "$level") }
            groups += Group(deepest, plan.nested, plan)
        }
        if (plan.large > 0) groups += Group(folder(top, "Large"), plan.large, LARGE_WIDTH, LARGE_HEIGHT)
        if (plan.huge > 0) groups += Group(folder(top, "Huge"), plan.huge, HUGE_WIDTH, HUGE_HEIGHT, encoders = 1)
        return groups
    }

    /**
     * One folder's pictures: made by [Group.encoders] coroutines while the last one made is written, so the
     * phone's cores encode and encri's pipe is never idle waiting for a picture.
     */
    private suspend fun write(
        session: EncriSession,
        group: Group,
        first: Int,
        dates: Dates,
        plan: SeedPlan,
    ) = coroutineScope {
        val started = SystemClock.elapsedRealtime()
        val queue = Channel<Int>(Channel.UNLIMITED)
        (first until first + group.count).forEach(queue::trySend)
        queue.close()
        val ready = Channel<Pair<Int, ByteArray>>(READY)
        val encoders =
            List(group.encoders) {
                launch(Dispatchers.Default) {
                    for (index in queue) ready.send(index to jpeg(index, group.width, group.height, plan.quality))
                }
            }
        launch {
            encoders.joinAll()
            ready.close()
        }
        var written = 0
        var bytes = 0L
        for ((index, jpeg) in ready) {
            withTimeout(OPERATION_MILLIS) {
                val made = ok(session.createFile(group.folder, "IMG_%05d.jpg".format(index + 1), "image/jpeg"))
                ok(session.write(made.id) { it.write(jpeg) })
                ok(session.setTimes(made.id, createdMillis = dates.of(index), modifiedMillis = dates.of(index)))
            }
            written++
            bytes += jpeg.size
            if (written % PROGRESS == 0) log("seed: $written of ${group.count} in this folder")
        }
        repeat(plan.others) {
            withTimeout(OPERATION_MILLIS) {
                val note = ok(session.createFile(group.folder, "notes ${it + 1}.txt", "text/plain"))
                ok(session.write(note.id) { out -> out.write(NOTE) })
            }
        }
        val millis = SystemClock.elapsedRealtime() - started
        log(
            "seed: ${group.count} pictures of ${group.width}x${group.height}, " +
                "${bytes / KIBIBYTE / group.count} KiB each, in $millis ms, ${millis / group.count} ms each",
        )
    }

    /** Where some pictures go, and how large they are. */
    private data class Group(
        val folder: String,
        val count: Int,
        val width: Int,
        val height: Int,
        val encoders: Int = ENCODERS,
    ) {
        constructor(folder: String, count: Int, plan: SeedPlan) : this(folder, count, plan.width, plan.height)
    }

    /** Dates spread evenly back from now, newest first, over [years]. */
    private class Dates(
        private val total: Int,
        years: Int,
    ) {
        private val now = System.currentTimeMillis()
        private val span = years * YEAR_MILLIS

        fun of(index: Int): Long = now - span * index / total.coerceAtLeast(1)
    }

    private companion object {
        const val TOP = "Encri Images test library"
        const val NESTED_DEPTH = 5
        const val LARGE_WIDTH = 4000
        const val LARGE_HEIGHT = 3000
        const val HUGE_WIDTH = 8000
        const val HUGE_HEIGHT = 6000
        const val ENCODERS = 3
        const val READY = 4
        const val PROGRESS = 250
        const val OPERATION_MILLIS = 120_000L
        const val KIBIBYTE = 1024
        const val MEBIBYTE = 1024 * 1024
        const val YEAR_MILLIS = 365L * 24 * 60 * 60 * 1000
        val NOTE = "Not a picture, which the gallery leaves out.\n".toByteArray()
    }
}

/** What a seed makes. Every count defaults to none; sizes are a phone camera's, smaller. */
internal data class SeedPlan(
    val camera: Int,
    val albums: Int,
    val perAlbum: Int,
    val nested: Int,
    val large: Int,
    val huge: Int,
    val others: Int,
    val width: Int,
    val height: Int,
    val quality: Int,
    val years: Int,
) {
    companion object {
        fun from(arguments: Bundle): SeedPlan {
            fun int(
                key: String,
                default: Int = 0,
            ): Int = arguments.getString(key)?.toInt() ?: default

            return SeedPlan(
                camera = int("camera"),
                albums = int("albums"),
                perAlbum = int("perAlbum"),
                nested = int("nested"),
                large = int("large"),
                huge = int("huge"),
                others = int("others"),
                width = int("width", DEFAULT_WIDTH),
                height = int("height", DEFAULT_HEIGHT),
                quality = int("quality", DEFAULT_QUALITY),
                years = int("years", DEFAULT_YEARS),
            )
        }

        private const val DEFAULT_WIDTH = 1600
        private const val DEFAULT_HEIGHT = 1200
        private const val DEFAULT_QUALITY = 85
        private const val DEFAULT_YEARS = 3
    }
}

/** Random pixels, faint, to lay over a picture. */
private val grain: Bitmap by lazy {
    val random = Random(GRAIN_SEED)
    val pixels =
        IntArray(GRAIN * GRAIN) {
            Color.argb(GRAIN_ALPHA, random.nextInt(CHANNEL), random.nextInt(CHANNEL), random.nextInt(CHANNEL))
        }
    Bitmap.createBitmap(pixels, GRAIN, GRAIN, Bitmap.Config.ARGB_8888)
}

/** A picture no other index makes: a gradient of its own, some shapes, grain, and its number. */
internal fun jpeg(
    index: Int,
    width: Int,
    height: Int,
    quality: Int,
): ByteArray {
    // RGB_565, half of ARGB's memory, which matters for a picture of 48 megapixels being encoded at all.
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
    val canvas = Canvas(bitmap)
    val random = Random(index)
    val hue = (index * HUE_STEP % DEGREES).toFloat()
    val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader =
                LinearGradient(
                    0f,
                    0f,
                    width.toFloat(),
                    height.toFloat(),
                    Color.HSVToColor(floatArrayOf(hue, SATURATION, BRIGHT)),
                    Color.HSVToColor(floatArrayOf((hue + HUE_TURN) % DEGREES, SATURATION, DARK)),
                    Shader.TileMode.CLAMP,
                )
        }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    paint.shader = null
    repeat(SHAPES) {
        paint.color = Color.argb(SHAPE_ALPHA, random.nextInt(CHANNEL), random.nextInt(CHANNEL), random.nextInt(CHANNEL))
        val radius = random.nextInt(width / SMALLEST_SHAPE, width / LARGEST_SHAPE).toFloat()
        canvas.drawCircle(random.nextInt(width).toFloat(), random.nextInt(height).toFloat(), radius, paint)
    }
    // Grain, as a camera's sensor leaves it: what makes a photo several megabytes, where a gradient alone
    // compresses to almost nothing. A tile, repeated, which JPEG's blocks of eight pixels cannot tell from noise.
    paint.color = Color.BLACK
    paint.shader = BitmapShader(grain, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    paint.shader = null
    paint.color = Color.WHITE
    paint.textSize = height / LABEL_PART.toFloat()
    canvas.drawText("#${index + 1}", width / LABEL_PART.toFloat(), height / 2f, paint)
    return ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        bitmap.recycle()
        out.toByteArray()
    }
}

private const val HUE_STEP = 37
private const val DEGREES = 360
private const val HUE_TURN = 120f
private const val SATURATION = 0.7f
private const val BRIGHT = 0.95f
private const val DARK = 0.35f
private const val SHAPES = 40
private const val SHAPE_ALPHA = 160
private const val CHANNEL = 256
private const val SMALLEST_SHAPE = 40
private const val LARGEST_SHAPE = 6
private const val LABEL_PART = 6
private const val GRAIN = 256
private const val GRAIN_ALPHA = 48
private const val GRAIN_SEED = 7
