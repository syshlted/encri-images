/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.edit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeometryTest {
    private val photo = Size(4f, 3f)

    /** The pixel at ([x], [y]), as a box one pixel big. */
    private fun pixel(
        x: Int,
        y: Int,
    ) = Box(x.toFloat(), y.toFloat(), x + 1f, y + 1f)

    @Test
    fun `a quarter turn clockwise takes the top-left pixel to the top-right`() {
        assertEquals(pixel(2, 0), Placement.turn(photo).map(pixel(0, 0)))
        assertEquals(pixel(0, 0), Placement.turn(photo).map(pixel(0, 2)), "the bottom-left goes to the top-left")
    }

    @Test
    fun `four quarter turns put every pixel back`() {
        assertEquals(Placement.NONE, Placement.turns(4, photo))
        assertEquals(Placement.turns(1, photo), Placement.turns(5, photo))
        assertEquals(Placement.turns(3, photo), Placement.turns(-1, photo))
    }

    @Test
    fun `two quarter turns are a half turn`() {
        assertEquals(pixel(3, 2), Placement.turns(2, photo).map(pixel(0, 0)))
        assertEquals(Placement.ofExif(3, photo), Placement.turns(2, photo))
    }

    @Test
    fun `each of EXIF's eight orientations shows the photo upright`() {
        // Where the pixel at the top-left of the photo's own pixels is shown, for each orientation.
        val shown =
            mapOf(
                1 to pixel(0, 0),
                2 to pixel(3, 0),
                3 to pixel(3, 2),
                4 to pixel(0, 2),
                5 to pixel(0, 0),
                6 to pixel(2, 0),
                7 to pixel(2, 3),
                8 to pixel(0, 3),
            )
        for ((orientation, where) in shown) {
            val placed = Placement.ofExif(orientation, photo)
            assertEquals(where, placed.map(pixel(0, 0)), "orientation $orientation")
            assertEquals(orientation >= 5, placed.swapsSides, "orientation $orientation")
            val upright = placed.map(Box.of(photo))
            assertEquals(Box(0f, 0f, if (orientation >= 5) 3f else 4f, if (orientation >= 5) 4f else 3f), upright)
        }
        assertEquals(Placement.NONE, Placement.ofExif(0, photo), "an orientation not set is upright")
    }

    @Test
    fun `an inverse puts back what a placement moved`() {
        for (orientation in 1..8) {
            for (turns in 0..3) {
                val placed = Placement.ofExif(orientation, photo)
                val shown = placed.map(Box.of(photo))
                val both = placed.then(Placement.turns(turns, Size(shown.width, shown.height)))
                val box = Box(1f, 0f, 3f, 2f)
                assertEquals(box, both.inverse.map(both.map(box)), "orientation $orientation, $turns turns")
                assertEquals(Placement.NONE, both.then(both.inverse))
            }
        }
    }

    @Test
    fun `its values are a matrix's`() {
        val values = Placement.turn(photo).values()
        assertEquals(listOf(0f, -1f, 3f, 1f, 0f, 0f, 0f, 0f, 1f), values.toList())
    }

    @Test
    fun `a box moves no further than keeps it inside the picture`() {
        val box = Box(1f, 1f, 2f, 2f)
        assertEquals(Box(2f, 1.5f, 3f, 2.5f), box.movedWithin(1f, 0.5f, photo))
        assertEquals(Box(3f, 0f, 4f, 1f), box.movedWithin(10f, -10f, photo))
        assertEquals(Box(0f, 2f, 1f, 3f), box.movedWithin(-10f, 10f, photo))
    }

    @Test
    fun `a box is near another to within the slack on every side`() {
        val box = Box(0f, 0f, 4f, 3f)
        assertTrue(box.near(Box(0.4f, -0.4f, 4.4f, 2.6f), 0.5f))
        assertFalse(box.near(Box(0f, 0f, 4f, 2f), 0.5f))
        assertTrue(box.contains(4f, 3f))
        assertFalse(box.contains(4.1f, 3f))
    }

    @Test
    fun `a value held between bounds with no room between is halfway`() {
        assertEquals(2f, 5f.within(0f, 2f))
        assertEquals(1.5f, 5f.within(2f, 1f))
    }
}
