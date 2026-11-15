/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.edit

import android.app.Application
import androidx.media3.effect.Crop
import androidx.media3.effect.ScaleAndRotateTransformation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class VideoEditsTest {
    private val video = Edit(Size(1920f, 1080f))

    /** One of [crop]'s sides, which it keeps to itself. */
    private fun Crop.side(name: String): Float =
        Crop::class.java
            .getDeclaredField(name)
            .apply { isAccessible = true }
            .getFloat(this)

    @Test
    fun `nothing asked is nothing for Transformer to do`() {
        val effects = VideoEdits.effectsOf(video)
        assertTrue(effects.videoEffects.isEmpty())
        assertTrue(effects.audioProcessors.isEmpty())
    }

    @Test
    fun `a turn alone is a rotation alone, anticlockwise, which Transformer writes as the orientation`() {
        for ((turns, degrees) in listOf(1 to 270f, 2 to 180f, 3 to 90f)) {
            var edit = video
            repeat(turns) { edit = edit.turnedClockwise() }
            val only = VideoEdits.effectsOf(edit).videoEffects.single() as ScaleAndRotateTransformation
            assertEquals("$turns turns", degrees, only.rotationDegrees, 0f)
            assertEquals(1f, only.scaleX, 0f)
            assertEquals(1f, only.scaleY, 0f)
        }
    }

    @Test
    fun `a crop is in the frame's own coordinates, upward, on whole even pixels`() {
        val edit = video.copy(crop = Box(480.4f, 270f, 1440.9f, 811f))
        val crop = VideoEdits.effectsOf(edit).videoEffects.single() as Crop
        // 480 to 1440 across, 270 to 810 down, of 1920 by 1080.
        assertEquals(-0.5f, crop.side("left"), 0.0001f)
        assertEquals(0.5f, crop.side("right"), 0.0001f)
        assertEquals(0.5f, crop.side("top"), 0.0001f)
        assertEquals(-0.5f, crop.side("bottom"), 0.0001f)
    }

    @Test
    fun `a crop of a turned video is made before the turn, on the video as it was shown`() {
        val turned = video.turnedClockwise()
        // The top half of the turned video is the left half of the video as it was.
        val edit = turned.copy(crop = Box(0f, 0f, 1080f, 960f))
        val (crop, turn) = VideoEdits.effectsOf(edit).videoEffects
        crop as Crop
        assertEquals(-1f, crop.side("left"), 0.0001f)
        assertEquals(0f, crop.side("right"), 0.0001f)
        assertEquals(1f, crop.side("top"), 0.0001f)
        assertEquals(-1f, crop.side("bottom"), 0.0001f)
        assertEquals(270f, (turn as ScaleAndRotateTransformation).rotationDegrees, 0f)
    }
}
