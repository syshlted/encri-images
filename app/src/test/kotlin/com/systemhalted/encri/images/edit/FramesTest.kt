/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.edit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FramesTest {
    private val landscape = Size(4000f, 3000f)

    private fun assertRatio(
        ratio: Float,
        box: Box,
    ) = assertEquals(ratio, box.width / box.height, 0.001f, "$box")

    @Test
    fun `the largest box of a shape is centred, and of no shape is the whole picture`() {
        assertEquals(Box(500f, 0f, 3500f, 3000f), Frames.largest(landscape, 1f))
        assertEquals(Box.of(landscape), Frames.largest(landscape, null))
        assertEquals(Box(0f, 375f, 4000f, 2625f), Frames.largest(landscape, 16f / 9))
        assertEquals(Box.of(landscape), Frames.largest(landscape, landscape.ratio))
    }

    @Test
    fun `centred near an edge, the largest box goes as near as it fits`() {
        assertEquals(Box(1000f, 0f, 4000f, 3000f), Frames.largest(landscape, 1f, centerX = 3900f, centerY = 100f))
    }

    @Test
    fun `a free corner drags on its own, held inside the picture and no smaller than the least`() {
        val box = Box(1000f, 1000f, 3000f, 2000f)
        assertEquals(
            Box(500f, 800f, 3000f, 2000f),
            Frames.resized(box, Handle.TOP_LEFT, 500f, 800f, landscape, null, 100f),
        )
        assertEquals(
            Box(0f, 0f, 3000f, 2000f),
            Frames.resized(box, Handle.TOP_LEFT, -50f, -50f, landscape, null, 100f),
            "not past the picture's edge",
        )
        assertEquals(
            Box(2900f, 1900f, 3000f, 2000f),
            Frames.resized(box, Handle.TOP_LEFT, 3500f, 3500f, landscape, null, 100f),
            "not past the opposite corner, less the least",
        )
        assertEquals(
            Box(1000f, 1000f, 4000f, 3000f),
            Frames.resized(box, Handle.BOTTOM_RIGHT, 4500f, 3200f, landscape, null, 100f),
        )
    }

    @Test
    fun `a free side drags that side alone`() {
        val box = Box(1000f, 1000f, 3000f, 2000f)
        assertEquals(
            Box(1000f, 1000f, 3500f, 2000f),
            Frames.resized(box, Handle.RIGHT, 3500f, 0f, landscape, null, 100f),
        )
        assertEquals(Box(1000f, 400f, 3000f, 2000f), Frames.resized(box, Handle.TOP, 0f, 400f, landscape, null, 100f))
        assertEquals(
            Box(1200f, 1000f, 3000f, 2000f),
            Frames.resized(box, Handle.LEFT, 1200f, 0f, landscape, null, 100f),
        )
        assertEquals(Box(1000f, 1000f, 3000f, 1100f), Frames.resized(box, Handle.BOTTOM, 0f, 0f, landscape, null, 100f))
    }

    @Test
    fun `a corner held to a shape keeps it, the corner opposite staying where it is`() {
        val square = Box(1000f, 1000f, 2000f, 2000f)
        val dragged = Frames.resized(square, Handle.BOTTOM_RIGHT, 2600f, 2200f, landscape, 1f, 100f)
        assertEquals(Box(1000f, 1000f, 2600f, 2600f), dragged, "the box follows the way the finger went further")
        val far = Frames.resized(square, Handle.BOTTOM_RIGHT, 9000f, 9000f, landscape, 1f, 100f)
        assertEquals(Box(1000f, 1000f, 3000f, 3000f), far, "no further than the picture's bottom")
        val small = Frames.resized(square, Handle.TOP_LEFT, 5000f, 5000f, landscape, 4f / 3, 300f)
        assertEquals(2000f, small.right)
        assertEquals(2000f, small.bottom)
        assertEquals(300f, small.height, 0.01f, "no side smaller than the least")
        assertRatio(4f / 3, small)
    }

    @Test
    fun `a side held to a shape keeps it, and its middle`() {
        val box = Frames.largest(landscape, 16f / 9).let { Box(it.left, 1000f, 1000f + 1600f, 1900f) }
        val wider = Frames.resized(box, Handle.RIGHT, 3200f, 0f, landscape, 16f / 9, 100f)
        assertEquals(box.left, wider.left)
        assertEquals(box.centerY, wider.centerY, 0.01f)
        assertRatio(16f / 9, wider)
        val taller = Frames.resized(box, Handle.TOP, 0f, 1400f, landscape, 16f / 9, 100f)
        assertEquals(box.bottom, taller.bottom)
        assertEquals(box.centerX, taller.centerX, 0.01f)
        assertRatio(16f / 9, taller)
        val tallest = Frames.resized(box, Handle.BOTTOM, 0f, 9000f, landscape, 16f / 9, 100f)
        assertEquals(3000f, tallest.bottom, 0.01f, "no further than the picture's bottom")
        val widest = Frames.resized(box, Handle.LEFT, -9000f, 0f, landscape, 16f / 9, 100f)
        assertEquals(0f, widest.left, 0.01f, "no further than the picture's left")
        assertRatio(16f / 9, widest)
    }

    @Test
    fun `a finger takes hold of a corner before a side, and of nothing inside or far away`() {
        val frame = Box(100f, 100f, 500f, 400f)
        assertEquals(Handle.TOP_LEFT, Frames.handleAt(frame, 110f, 90f, 24f))
        assertEquals(Handle.BOTTOM_RIGHT, Frames.handleAt(frame, 510f, 410f, 24f))
        assertEquals(Handle.TOP_RIGHT, Frames.handleAt(frame, 490f, 110f, 24f))
        assertEquals(Handle.BOTTOM_LEFT, Frames.handleAt(frame, 100f, 400f, 24f))
        assertEquals(Handle.TOP, Frames.handleAt(frame, 300f, 110f, 24f))
        assertEquals(Handle.BOTTOM, Frames.handleAt(frame, 300f, 390f, 24f))
        assertEquals(Handle.LEFT, Frames.handleAt(frame, 90f, 250f, 24f))
        assertEquals(Handle.RIGHT, Frames.handleAt(frame, 515f, 250f, 24f))
        assertNull(Frames.handleAt(frame, 300f, 250f, 24f), "inside")
        assertNull(Frames.handleAt(frame, 300f, 600f, 24f), "below")
        assertNull(Frames.handleAt(frame, 50f, 250f, 24f), "to the left")
    }

    @Test
    fun `on a frame smaller than the reach, the nearer side is taken`() {
        val frame = Box(100f, 100f, 120f, 120f)
        assertEquals(Handle.TOP_RIGHT, Frames.handleAt(frame, 118f, 102f, 24f))
        assertEquals(Handle.BOTTOM_LEFT, Frames.handleAt(frame, 101f, 119f, 24f))
    }

    @Test
    fun `a shape turned a quarter is its other way round`() {
        assertEquals(Aspect.THREE_BY_FOUR, Aspect.FOUR_BY_THREE.turned)
        assertEquals(Aspect.FOUR_BY_THREE, Aspect.THREE_BY_FOUR.turned)
        assertEquals(Aspect.NINE_BY_SIXTEEN, Aspect.SIXTEEN_BY_NINE.turned)
        assertEquals(Aspect.SIXTEEN_BY_NINE, Aspect.NINE_BY_SIXTEEN.turned)
        for (same in listOf(Aspect.FREE, Aspect.ORIGINAL, Aspect.SQUARE)) assertEquals(same, same.turned)
        assertNull(Aspect.FREE.ratioIn(landscape))
        assertEquals(4f / 3, Aspect.ORIGINAL.ratioIn(landscape))
        assertEquals(3f / 4, Aspect.ORIGINAL.ratioIn(landscape.turned))
        assertEquals(9f / 16, Aspect.NINE_BY_SIXTEEN.ratioIn(landscape))
    }
}
