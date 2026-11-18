/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.edit

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import com.systemhalted.encri.images.R
import com.systemhalted.encri.images.models.Media
import com.systemhalted.encri.images.models.MediaType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast
import java.io.File
import java.nio.file.Files
import java.util.Date

/** The editor over a store that is not the vault (I23), and what every store shares: a copy's name, and [Saving]. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditStoreTest {
    private val folder = Files.createTempDirectory("store").toFile()
    private val store = FolderStore(folder)
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @After
    fun tearDown() {
        folder.deleteRecursively()
    }

    /** A photo 40 by 30 in the folder, red on its left half and blue on its right. */
    private fun beach(): Media {
        val bitmap = Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.RED)
            clipRect(20, 0, 40, 30)
            drawColor(Color.BLUE)
        }
        val file = File(folder, "beach.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return Media(
            uri = Uri.fromFile(file),
            mediaType = MediaType.IMAGE,
            mimeType = "image/png",
            albumUri = Uri.fromFile(folder),
            albumName = null,
            displayName = file.name,
            dateAdded = Date(0),
            dateModified = Date(0),
            sizeBytes = file.length(),
        )
    }

    /** A JPEG 40 by 30, taken turned a quarter (EXIF orientation 6), with a time and a place. */
    private fun taken(): Media {
        val file = File(folder, "taken.jpg")
        file.outputStream().use {
            Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, it)
        }
        ExifInterface(file)
            .apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2026:07:04 12:30:00")
                setLatLong(51.5, -0.12)
            }.saveAttributes()
        return beach().copy(uri = Uri.fromFile(file), mimeType = "image/jpeg", displayName = file.name)
    }

    private fun activity(): Activity =
        Robolectric
            .buildActivity(AppCompatActivity::class.java)
            .apply { get().setTheme(R.style.Theme_Images_MediaViewer) }
            .setup()
            .get()

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun text(id: Int): String = ApplicationProvider.getApplicationContext<Application>().getString(id)

    @Test
    fun `a photo is read from a store that is not the vault, turned, and its copy kept there`() {
        val beach = beach()
        val making = Making(ApplicationProvider.getApplicationContext(), store, beach)
        val made = runBlocking { making.make(Edit(Size(40f, 30f)).turnedClockwise()) {} }
        val saved = runBlocking { store.keep(beach, made) }
        made.release()

        val copy = File(folder, "beach (edited).png")
        assertEquals(Saved.Kept(Uri.fromFile(copy)), saved)
        val turned = BitmapFactory.decodeFile(copy.path)
        assertEquals(30, turned.width)
        assertEquals(40, turned.height)
        // Turned clockwise, the left half is on top.
        assertEquals(Color.RED, turned.getPixel(15, 5))
        assertEquals(Color.BLUE, turned.getPixel(15, 35))
    }

    @Test
    fun `a store that carries the original's metadata gives the copy its time and place, but not its orientation`() {
        for (carried in Carried.entries) {
            val store = FolderStore(folder, carried)
            val taken = taken()
            // Shown upright, the photo is 30 wide and 40 high, and so is its copy.
            val making = Making(ApplicationProvider.getApplicationContext(), store, taken)
            val made = runBlocking { making.make(Edit(Size(30f, 40f))) {} }
            runBlocking { store.keep(taken, made) }
            made.release()

            val copy = store.kept.single()
            assertEquals(30, BitmapFactory.decodeFile(copy.path).width)
            val exif = ExifInterface(copy)
            assertEquals("$carried: upright already, and not to be turned again", 0, exif.rotationDegrees)
            if (carried == Carried.NOTHING) {
                assertNull(exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
                assertNull(exif.latLong)
            } else {
                assertEquals("2026:07:04 12:30:00", exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
                assertNotNull(exif.latLong)
            }
            copy.delete()
        }
    }

    @Test
    fun `saving keeps the copy in the store, says so, and tells who asked`() {
        val beach = beach()
        val savedAs = mutableListOf<Uri>()
        var released = 0
        Saving(activity(), beach, store, scope, {
            Made("beach (edited).png", "image/png", { it.write(byteArrayOf(4, 2)) }, { released++ })
        }, savedAs::add, onNotAllowed = { error("a store that keeps it is not refused") }).start()
        idle()

        assertEquals(listOf(Uri.fromFile(File(folder, "beach (edited).png"))), savedAs)
        assertArrayEquals(byteArrayOf(4, 2), store.kept.single().readBytes())
        assertEquals(text(R.string.edit_saved), ShadowToast.getTextOfLatestToast())
        assertEquals(1, released)
    }

    @Test
    fun `a store that will not keep it is told to whoever may ask leave, and one that fails is said`() {
        var refused = 0
        store.answer = Saved.NotAllowed
        val saving =
            Saving(activity(), beach(), store, scope, {
                Made("beach (edited).png", "image/png", {}, {})
            }, { error("nothing is kept") }, onNotAllowed = { refused++ })
        saving.start()
        idle()
        assertEquals(1, refused)
        assertEquals(0, ShadowToast.shownToastCount())

        store.answer = Saved.Failed
        saving.start()
        idle()
        assertEquals(text(R.string.edit_failed), ShadowToast.getTextOfLatestToast())
        assertTrue(store.kept.isEmpty())
    }

    @Test
    fun `a copy is named after the original, marked once, with its own extension`() {
        assertEquals("IMG_1 (edited).jpg", Made.nameOf("IMG_1.jpg", "jpg"))
        assertEquals("IMG_1 (edited).jpg", Made.nameOf("IMG_1.HEIC", "jpg"))
        assertEquals("IMG_1 (edited).jpg", Made.nameOf("IMG_1 (edited).jpg", "jpg"))
        assertEquals("IMG_1 (edited).jpg", Made.nameOf("IMG_1 (edited) (3).jpg", "jpg"))
        assertEquals("clip.v2 (edited).mp4", Made.nameOf("clip.v2.mov", "mp4"))
        assertEquals("notes (edited).png", Made.nameOf("notes", "png"))
        assertEquals(".hidden (edited).png", Made.nameOf(".hidden", "png"))
        assertEquals("copy (edited).jpg", Made.nameOf("", "jpg"))
    }
}
