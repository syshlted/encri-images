/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.edit

import android.app.Application
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Metadata
import androidx.media3.common.MimeTypes
import androidx.media3.container.Mp4LocationData
import androidx.media3.container.Mp4OrientationData
import androidx.media3.muxer.BufferInfo
import androidx.media3.muxer.Mp4Muxer
import androidx.media3.muxer.Muxer
import com.systemhalted.encri.images.edit.Carried.ALL_BUT_ORIENTATION
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MemoryMuxerTest {
    /** H.263, which needs no codec data of its own, turned [rotation] degrees. */
    private fun h263(rotation: Int = 0): Format =
        Format
            .Builder()
            .setSampleMimeType(MimeTypes.VIDEO_H263)
            .setWidth(176)
            .setHeight(144)
            .setRotationDegrees(rotation)
            .build()

    /** A video of three frames muxed by [muxer], with [metadata] offered it. */
    private fun mux(
        muxer: Muxer,
        rotation: Int = 0,
        metadata: List<Metadata.Entry> = emptyList(),
    ) {
        val track = muxer.addTrack(h263(rotation))
        metadata.forEach(muxer::addMetadataEntry)
        repeat(3) {
            val sample = ByteBuffer.allocateDirect(16).put(ByteArray(16) { 1 }).flip() as ByteBuffer
            val flags = if (it == 0) C.BUFFER_FLAG_KEY_FRAME else 0
            muxer.writeSampleData(track, sample, BufferInfo(it * 33_333L, 16, flags))
        }
        muxer.close()
    }

    /** What [MemoryMuxer] muxes into memory, from a muxer made for [path]. */
    private fun muxed(
        rotation: Int = 0,
        metadata: List<Metadata.Entry> = emptyList(),
        path: String = "unused",
        carried: Carried = Carried.NOTHING,
    ): ByteArray {
        val output = MemoryOutput(limit = 1L shl 20)
        mux(MemoryMuxer.Factory(output, carried).create(path), rotation, metadata)
        return ByteArrayOutputStream().also(output::writeTo).toByteArray()
    }

    /** A track header's matrix, as ISO/IEC 14496-12 writes it, turned [degrees] clockwise. */
    private fun matrixFor(degrees: Int): ByteArray {
        val one = 0x00010000
        val turned =
            when (degrees) {
                90 -> listOf(0, one, -one, 0)
                180 -> listOf(-one, 0, 0, -one)
                270 -> listOf(0, -one, one, 0)
                else -> listOf(one, 0, 0, one)
            }
        val matrix = ByteBuffer.allocate(36)
        listOf(turned[0], turned[1], 0, turned[2], turned[3], 0, 0, 0, 0x40000000).forEach(matrix::putInt)
        return matrix.array()
    }

    private fun ByteArray.holds(part: ByteArray): Boolean =
        (0..size - part.size).any { at -> part.indices.all { this[at + it] == part[it] } }

    @Test
    fun `an MP4 is muxed into memory, with no file opened at the path it is given`() {
        val mp4 = muxed(path = File("nowhere", "never-opened.mp4").path)
        assertEquals("ftyp", String(mp4, 4, 4))
        assertTrue(mp4.holds("moov".toByteArray()))
        assertTrue(mp4.holds("mdat".toByteArray()))
        assertFalse(File("nowhere").exists())
    }

    @Test
    fun `a video's turn is written as its track's orientation`() {
        for (degrees in listOf(0, 90, 180, 270)) {
            assertTrue("$degrees degrees", muxed(rotation = degrees).holds(matrixFor(degrees)))
        }
    }

    @Test
    fun `nothing else of the original's metadata is carried`() {
        val place = Mp4LocationData(51.5f, -0.12f)
        val written = "+51.5000-0.1200/".toByteArray()
        assertFalse(muxed(metadata = listOf(place)).holds(written))
        // Media3's own muxer writes a place it is given, so the line above can fail.
        val plain = MemoryOutput(limit = 1L shl 20)
        mux(Mp4Muxer.Builder(plain).build(), metadata = listOf(place))
        assertTrue(ByteArrayOutputStream().also(plain::writeTo).toByteArray().holds(written))
    }

    @Test
    fun `a store that carries all but the orientation gets the place, and the turn is the copy's own`() {
        val place = Mp4LocationData(51.5f, -0.12f)
        val mp4 = muxed(rotation = 90, metadata = listOf(place, Mp4OrientationData(180)), carried = ALL_BUT_ORIENTATION)
        assertTrue(mp4.holds("+51.5000-0.1200/".toByteArray()))
        assertTrue("the track's own turn, not the one offered", mp4.holds(matrixFor(90)))
        assertFalse(mp4.holds(matrixFor(180)))
    }

    @Test
    fun `it takes what Media3's MP4 muxer takes, and edit lists that start before zero`() {
        val factory = MemoryMuxer.Factory(MemoryOutput(limit = 1), Carried.NOTHING)
        assertEquals(
            Mp4Muxer.SUPPORTED_VIDEO_SAMPLE_MIME_TYPES,
            factory.getSupportedSampleMimeTypes(C.TRACK_TYPE_VIDEO),
        )
        assertEquals(
            Mp4Muxer.SUPPORTED_AUDIO_SAMPLE_MIME_TYPES,
            factory.getSupportedSampleMimeTypes(C.TRACK_TYPE_AUDIO),
        )
        assertTrue(factory.getSupportedSampleMimeTypes(C.TRACK_TYPE_TEXT).isEmpty())
        assertTrue(factory.supportsWritingNegativeTimestampsInEditList())
    }
}
