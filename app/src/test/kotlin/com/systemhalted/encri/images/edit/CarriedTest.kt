/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.edit

import android.app.Application
import android.graphics.Bitmap
import androidx.annotation.OptIn
import androidx.exifinterface.media.ExifInterface
import androidx.media3.common.util.UnstableApi
import androidx.media3.container.Mp4LocationData
import androidx.media3.container.Mp4OrientationData
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** What a copy carries of the original's metadata (I23): nothing, or all that still describes it. */
@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CarriedTest {
    private val photo = File.createTempFile("taken", ".jpg")

    @After
    fun tearDown() {
        photo.delete()
    }

    /** A JPEG with EXIF of each kind: when, where, what took it, its words, and what a copy cannot keep. */
    private fun taken(): ExifInterface {
        val bitmap = Bitmap.createBitmap(8, 6, Bitmap.Config.ARGB_8888)
        photo.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        ExifInterface(photo)
            .apply {
                setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2026:07:04 12:30:00")
                setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, "+01:00")
                setLatLong(51.5, -0.12)
                setAttribute(ExifInterface.TAG_MAKE, "Maker")
                setAttribute(ExifInterface.TAG_F_NUMBER, "1.8")
                setAttribute(ExifInterface.TAG_ARTIST, "Someone")
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                setAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION, "8")
                setAttribute(ExifInterface.TAG_SUBJECT_AREA, "4 3 2 2")
                setAttribute(ExifInterface.TAG_IMAGE_UNIQUE_ID, "0123456789abcdef0123456789abcdef")
                setAttribute(ExifInterface.TAG_SOFTWARE, "Camera")
            }.saveAttributes()
        return ExifInterface(photo)
    }

    @Test
    fun `nothing carries nothing`() {
        assertEquals(emptyMap<String, String>(), Carried.NOTHING.exifFrom(taken()))
        assertFalse(Carried.NOTHING.keeps(Mp4LocationData(51.5f, -0.12f)))
    }

    @Test
    fun `all but the orientation carries when, where, what took it and its words`() {
        val carried = Carried.ALL_BUT_ORIENTATION.exifFrom(taken())
        assertEquals("2026:07:04 12:30:00", carried[ExifInterface.TAG_DATETIME_ORIGINAL])
        assertEquals("+01:00", carried[ExifInterface.TAG_OFFSET_TIME_ORIGINAL])
        assertEquals("N", carried[ExifInterface.TAG_GPS_LATITUDE_REF])
        assertTrue(carried.containsKey(ExifInterface.TAG_GPS_LONGITUDE))
        assertEquals("Maker", carried[ExifInterface.TAG_MAKE])
        assertEquals("1.8", carried[ExifInterface.TAG_F_NUMBER])
        assertEquals("Someone", carried[ExifInterface.TAG_ARTIST])
    }

    @Test
    fun `but not what the edit changed, nor what names the original or what wrote it`() {
        val carried = Carried.ALL_BUT_ORIENTATION.exifFrom(taken())
        for (tag in listOf(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.TAG_PIXEL_X_DIMENSION,
            ExifInterface.TAG_SUBJECT_AREA,
            ExifInterface.TAG_IMAGE_UNIQUE_ID,
            ExifInterface.TAG_SOFTWARE,
        )) {
            assertFalse(tag, carried.containsKey(tag))
        }
    }

    @Test
    fun `a video keeps what Media3's muxer takes, but for the orientation, which is the copy's own`() {
        assertTrue(Carried.ALL_BUT_ORIENTATION.keeps(Mp4LocationData(51.5f, -0.12f)))
        assertFalse(Carried.ALL_BUT_ORIENTATION.keeps(Mp4OrientationData(90)))
    }
}
