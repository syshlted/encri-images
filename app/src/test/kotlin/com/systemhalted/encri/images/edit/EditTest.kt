/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.edit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EditTest {
    private val photo = Edit(Size(4000f, 3000f))

    @Test
    fun `nothing is asked until something is`() {
        assertFalse(photo.changed)
        assertEquals(Box(0f, 0f, 4000f, 3000f), photo.crop)
        assertFalse(photo.copy(crop = Box(0.2f, 0f, 4000f, 2999.7f)).changed, "half a pixel is nothing")
        assertTrue(photo.copy(crop = Box(1f, 0f, 4000f, 3000f)).changed)
        assertTrue(photo.turnedClockwise().changed)
    }

    @Test
    fun `a turn takes the crop and its shape with the picture`() {
        val leftHalf = photo.copy(crop = Box(0f, 0f, 2000f, 3000f), aspect = Aspect.FOUR_BY_THREE)
        val turned = leftHalf.turnedClockwise()
        assertEquals(1, turned.turns)
        assertEquals(Size(3000f, 4000f), turned.picture)
        assertEquals(Box(0f, 0f, 3000f, 2000f), turned.crop, "the left half is the top half once turned clockwise")
        assertEquals(Aspect.THREE_BY_FOUR, turned.aspect)
        assertEquals(Box(0f, 0f, 2000f, 3000f), turned.cropOfSource)
    }

    @Test
    fun `four turns are where it started`() {
        val cropped = photo.copy(crop = Box(100f, 200f, 1300f, 900f))
        val round =
            cropped
                .turnedClockwise()
                .turnedClockwise()
                .turnedClockwise()
                .turnedClockwise()
        assertEquals(cropped, round)
        assertEquals(cropped.crop, cropped.turnedClockwise().turnedClockwise().cropOfSource)
    }

    @Test
    fun `a shape chosen is the largest box of it, where the crop was`() {
        val square = photo.copy(crop = Box(3000f, 0f, 4000f, 1000f)).heldTo(Aspect.SQUARE)
        assertEquals(Aspect.SQUARE, square.aspect)
        assertEquals(Box(1000f, 0f, 4000f, 3000f), square.crop)
        val original = square.heldTo(Aspect.ORIGINAL)
        assertEquals(Box(0f, 0f, 4000f, 3000f), original.crop)
    }

    @Test
    fun `free keeps the crop as it is`() {
        val cropped = photo.copy(crop = Box(10f, 20f, 300f, 400f), aspect = Aspect.SQUARE)
        val free = cropped.heldTo(Aspect.FREE)
        assertEquals(Aspect.FREE, free.aspect)
        assertEquals(cropped.crop, free.crop)
    }

    @Test
    fun `reset asks nothing`() {
        val asked = photo.turnedClockwise().heldTo(Aspect.SQUARE)
        assertEquals(photo, asked.reset())
    }
}
