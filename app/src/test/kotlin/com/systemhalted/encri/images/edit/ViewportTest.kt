/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.edit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ViewportTest {
    private val area = Box(0f, 0f, 1000f, 2000f)

    private fun fitted() = Viewport().apply { fit(Size(4000f, 3000f), area) }

    @Test
    fun `a picture is fitted into the area, centred`() {
        val viewport = fitted()
        assertEquals(0.25f, viewport.scale)
        assertEquals(Box(0f, 625f, 1000f, 1375f), viewport.toScreen(Box(0f, 0f, 4000f, 3000f)))
        assertEquals(2000f, viewport.toPictureX(500f))
        assertEquals(1500f, viewport.toPictureY(1000f))
    }

    @Test
    fun `a pinch keeps the point under the fingers where it is`() {
        val viewport = fitted()
        viewport.zoomBy(2f, 250f, 1000f)
        assertEquals(2f, viewport.zoom)
        assertEquals(1000f, viewport.toPictureX(250f), 0.01f)
        assertEquals(1500f, viewport.toPictureY(1000f), 0.01f)
    }

    @Test
    fun `zoom stays between fitted and eight times it`() {
        val viewport = fitted()
        viewport.zoomBy(0.5f, 500f, 1000f)
        assertEquals(1f, viewport.zoom)
        viewport.zoomBy(100f, 500f, 1000f)
        assertEquals(Viewport.MOST_ZOOM, viewport.zoom)
    }

    @Test
    fun `unzoomed, the picture stays centred, and zoomed, it still covers the area it is wider than`() {
        val viewport = fitted()
        viewport.panBy(300f, 300f)
        assertEquals(0f, viewport.dx)
        assertEquals(625f, viewport.dy)
        viewport.zoomBy(4f, 500f, 1000f)
        viewport.panBy(5000f, 0f)
        assertEquals(0f, viewport.dx, "its left edge no further right than the area's")
        viewport.panBy(-50_000f, 0f)
        assertEquals(1000f - 4000f, viewport.dx, "its right edge no further left than the area's")
        val drawnHeight = 3000f * viewport.scale
        viewport.panBy(0f, 50_000f)
        assertEquals(0f, viewport.dy)
        viewport.panBy(0f, -50_000f)
        assertEquals(2000f - drawnHeight, viewport.dy)
    }

    @Test
    fun `fitting again forgets the zoom`() {
        val viewport = fitted()
        viewport.zoomBy(3f, 0f, 0f)
        viewport.fit(Size(3000f, 4000f), area)
        assertEquals(1f, viewport.zoom)
        val drawn = viewport.toScreen(Box(0f, 0f, 3000f, 4000f))
        assertEquals(0f, drawn.left, 0.01f)
        assertEquals(1000f, drawn.right, 0.01f)
        assertEquals(1000f, drawn.centerY, 0.01f)
        assertEquals(1333.33f, drawn.height, 0.01f)
    }
}
