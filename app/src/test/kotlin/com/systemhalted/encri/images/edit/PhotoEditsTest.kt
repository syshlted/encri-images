/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.edit

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoEditsTest {
    private val files = mutableListOf<File>()

    @After
    fun tearDown() = files.forEach { it.delete() }

    /** A photo [width] by [height] in quarters: red top-left, green top-right, blue bottom-left, white bottom-right. */
    private fun quarters(
        width: Int = 40,
        height: Int = 30,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        val halfX = width / 2
        val halfY = height / 2
        for ((rect, colour) in listOf(
            Rect(0, 0, halfX, halfY) to Color.RED,
            Rect(halfX, 0, width, halfY) to Color.GREEN,
            Rect(0, halfY, halfX, height) to Color.BLUE,
            Rect(halfX, halfY, width, height) to Color.WHITE,
        )) {
            paint.color = colour
            canvas.drawRect(rect, paint)
        }
        return bitmap
    }

    private fun saved(
        bitmap: Bitmap,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
    ): File =
        File.createTempFile("photo", null).also { file ->
            files += file
            file.outputStream().use { bitmap.compress(format, 100, it) }
        }

    private fun editsOf(file: File) =
        PhotoEdits { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) }

    /** The copy [edit] asks of [file], written and read back. */
    private fun rendered(
        file: File,
        edit: Edit,
        format: PhotoFormat = PhotoFormat.PNG,
    ): Bitmap {
        val written = ByteArrayOutputStream()
        val photo = runBlocking { editsOf(file).render(edit, format) }
        photo.writeTo(written)
        photo.release()
        return BitmapFactory.decodeByteArray(written.toByteArray(), 0, written.size())
    }

    /** Whether [colour] is [near] to within what a JPEG does to a flat colour. */
    private fun assertNear(
        near: Int,
        colour: Int,
        where: String,
    ) {
        val off =
            maxOf(
                abs(Color.red(near) - Color.red(colour)),
                abs(Color.green(near) - Color.green(colour)),
                abs(Color.blue(near) - Color.blue(colour)),
            )
        assertTrue("$where: ${Integer.toHexString(colour)} is not near ${Integer.toHexString(near)}", off < 48)
    }

    @Test
    fun `a preview is the photo, upright, with its size`() {
        val preview = runBlocking { editsOf(saved(quarters())).preview() }
        assertEquals(Size(40f, 30f), preview.size)
        assertEquals(40, preview.bitmap.width)
        assertEquals(Color.RED, preview.bitmap.getPixel(5, 5))
    }

    @Test
    fun `a preview of a large photo is a power of two smaller, its size the photo's`() {
        val preview = runBlocking { editsOf(saved(quarters(400, 300))).preview(longest = 150) }
        assertEquals(Size(400f, 300f), preview.size)
        assertEquals(100, preview.bitmap.width)
        assertEquals(75, preview.bitmap.height)
    }

    @Test
    fun `a crop keeps the part chosen, and only that`() {
        val photo = Edit(Size(40f, 30f))
        val left = rendered(saved(quarters()), photo.copy(crop = Box(0f, 0f, 20f, 30f)))
        assertEquals(20, left.width)
        assertEquals(30, left.height)
        assertEquals(Color.RED, left.getPixel(19, 0))
        assertEquals(Color.BLUE, left.getPixel(0, 29))
    }

    @Test
    fun `a quarter turn turns the pixels clockwise`() {
        val turned = rendered(saved(quarters()), Edit(Size(40f, 30f)).turnedClockwise())
        assertEquals(30, turned.width)
        assertEquals(40, turned.height)
        assertEquals("the bottom-left goes to the top-left", Color.BLUE, turned.getPixel(0, 0))
        assertEquals(Color.RED, turned.getPixel(29, 0))
        assertEquals(Color.WHITE, turned.getPixel(0, 39))
        assertEquals(Color.GREEN, turned.getPixel(29, 39))
    }

    @Test
    fun `a crop of the turned photo is of what the user saw`() {
        val turned = Edit(Size(40f, 30f)).turnedClockwise()
        // The top half of the turned photo is the left half of the photo as it was.
        val top = rendered(saved(quarters()), turned.copy(crop = Box(0f, 0f, 30f, 20f)))
        assertEquals(30, top.width)
        assertEquals(20, top.height)
        assertEquals(Color.BLUE, top.getPixel(0, 0))
        assertEquals(Color.RED, top.getPixel(29, 19))
    }

    @Test
    fun `a photo's own EXIF orientation is shown and kept, and the copy is upright with none`() {
        val file = saved(quarters(), Bitmap.CompressFormat.JPEG)
        ExifInterface(file.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        val preview = runBlocking { editsOf(file).preview() }
        assertEquals(Size(30f, 40f), preview.size)
        assertNear(Color.BLUE, preview.bitmap.getPixel(7, 10), "the preview's top-left")

        val written = ByteArrayOutputStream()
        runBlocking { editsOf(file).render(Edit(preview.size).copy(crop = Box(0f, 0f, 30f, 20f)), PhotoFormat.JPEG) }
            .writeTo(written)
        val copy = written.toByteArray()
        val upright = BitmapFactory.decodeByteArray(copy, 0, copy.size)
        assertEquals(30, upright.width)
        assertEquals(20, upright.height)
        assertNear(Color.BLUE, upright.getPixel(7, 10), "the copy's top-left")
        assertNear(Color.RED, upright.getPixel(22, 10), "the copy's top-right")
        val orientation = ExifInterface(copy.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, 0)
        assertTrue("no orientation, or upright: $orientation", orientation <= ExifInterface.ORIENTATION_NORMAL)
    }

    @Test
    fun `a tall photo is decoded a band at a time, with nothing lost where bands meet`() {
        val tall = Bitmap.createBitmap(3, 2500, Bitmap.Config.ARGB_8888)
        val paint = Paint()
        for ((rows, colour) in listOf(
            0 until 1024 to Color.RED,
            1024 until 2048 to Color.GREEN,
            2048 until 2500 to Color.BLUE,
        )) {
            paint.color = colour
            Canvas(tall).drawRect(0f, rows.first.toFloat(), 3f, rows.last + 1f, paint)
        }
        val edit = Edit(Size(3f, 2500f)).copy(crop = Box(0f, 0f, 3f, 2499f))
        val copy = rendered(saved(tall), edit)
        assertEquals(2499, copy.height)
        assertEquals(Color.RED, copy.getPixel(1, 1023))
        assertEquals(Color.GREEN, copy.getPixel(1, 1024))
        assertEquals(Color.GREEN, copy.getPixel(1, 2047))
        assertEquals(Color.BLUE, copy.getPixel(1, 2048))
        assertEquals(Color.BLUE, copy.getPixel(1, 2498))
    }

    @Test
    fun `a format decoded only whole gives the same copy`() {
        val bmp = bmpOf(quarters())
        val left = rendered(bmp, Edit(Size(40f, 30f)).copy(crop = Box(0f, 0f, 20f, 30f)).turnedClockwise())
        assertEquals(30, left.width)
        assertEquals(20, left.height)
        assertEquals(Color.BLUE, left.getPixel(0, 0))
        assertEquals(Color.RED, left.getPixel(29, 19))
    }

    @Test
    fun `a copy is written in the photo's own format where Android writes it, and JPEG where it does not`() {
        assertEquals(PhotoFormat.PNG, PhotoFormat.of("image/png"))
        assertEquals(PhotoFormat.WEBP, PhotoFormat.of("IMAGE/WEBP"))
        assertEquals(PhotoFormat.JPEG, PhotoFormat.of("image/jpeg"))
        assertEquals(PhotoFormat.JPEG, PhotoFormat.of("image/heic"))
        val file = saved(quarters())
        val half = Edit(Size(40f, 30f)).copy(crop = Box(0f, 0f, 20f, 15f))
        val magic =
            mapOf(
                PhotoFormat.JPEG to byteArrayOf(0xFF.toByte(), 0xD8.toByte()),
                PhotoFormat.PNG to byteArrayOf(0x89.toByte(), 'P'.code.toByte()),
                PhotoFormat.WEBP to "RIFF".toByteArray(),
            )
        for ((format, start) in magic) {
            val written = ByteArrayOutputStream()
            val photo = runBlocking { editsOf(file).render(half, format) }
            assertEquals(20, photo.width)
            assertEquals(15, photo.height)
            photo.writeTo(written)
            assertEquals(format.name, start.toList(), written.toByteArray().take(start.size))
        }
    }

    @Test
    fun `what Android cannot read is not edited`() {
        val junk = File.createTempFile("junk", null).also { files += it }.apply { writeBytes(ByteArray(64) { 3 }) }
        assertThrows(IOException::class.java) { runBlocking { editsOf(junk).preview() } }
        assertThrows(
            IOException::class.java,
        ) { runBlocking { editsOf(junk).render(Edit(Size(4f, 4f)), PhotoFormat.PNG) } }
    }

    @Test
    fun `a box of the photo's pixels is made whole, inside it, on multiples of the sample`() {
        assertEquals(Rect(8, 20, 100, 48), PhotoEdits.aligned(Box(10.4f, 20.6f, 100.2f, 50.5f), Size(200f, 100f), 4))
        assertEquals(Rect(0, 0, 200, 100), PhotoEdits.aligned(Box(-3f, -3f, 300f, 300f), Size(200f, 100f), 1))
        assertEquals(Rect(196, 96, 200, 100), PhotoEdits.aligned(Box(199f, 99f, 200f, 100f), Size(200f, 100f), 4))
    }

    /** [bitmap] as a 24-bit BMP, which Android decodes whole and not a part at a time. */
    private fun bmpOf(bitmap: Bitmap): File {
        val row = (bitmap.width * 3 + 3) / 4 * 4
        val pixels = row * bitmap.height
        val bmp = ByteBuffer.allocate(54 + pixels).order(ByteOrder.LITTLE_ENDIAN)
        bmp
            .put('B'.code.toByte())
            .put('M'.code.toByte())
            .putInt(54 + pixels)
            .putInt(0)
            .putInt(54)
        bmp
            .putInt(40)
            .putInt(bitmap.width)
            .putInt(bitmap.height)
            .putShort(1)
            .putShort(24)
            .putInt(0)
            .putInt(pixels)
        bmp
            .putInt(2835)
            .putInt(2835)
            .putInt(0)
            .putInt(0)
        for (y in bitmap.height - 1 downTo 0) {
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                bmp.put(Color.blue(pixel).toByte()).put(Color.green(pixel).toByte()).put(Color.red(pixel).toByte())
            }
            repeat(row - bitmap.width * 3) { bmp.put(0) }
        }
        return File.createTempFile("photo", ".bmp").also {
            files += it
            it.writeBytes(bmp.array())
        }
    }
}
