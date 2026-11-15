/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.edit

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException

class MemoryOutputTest {
    /** Pieces of four bytes, so that every test crosses from one to the next. */
    private val output = MemoryOutput(limit = 64, piece = 4)

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private fun written(): ByteArray = ByteArrayOutputStream().also(output::writeTo).toByteArray()

    @Test
    fun `what is written across pieces reads back in order`() {
        assertEquals(10, output.write(ByteBuffer.wrap(ByteArray(10) { it.toByte() })))
        assertEquals(10L, output.position)
        assertEquals(10L, output.size)
        assertArrayEquals(ByteArray(10) { it.toByte() }, written())
    }

    @Test
    fun `a muxer goes back and writes over what it wrote`() {
        output.write(ByteBuffer.wrap(ByteArray(9)))
        output.position = 3
        output.write(ByteBuffer.wrap(bytes(7, 7, 7)))
        assertEquals(6L, output.position)
        assertEquals(9L, output.size, "the size is as far as anything was written")
        assertArrayEquals(bytes(0, 0, 0, 7, 7, 7, 0, 0, 0), written())
    }

    @Test
    fun `only what a buffer has left is written`() {
        val buffer = ByteBuffer.wrap(bytes(1, 2, 3, 4, 5, 6))
        buffer.position(2)
        buffer.limit(5)
        assertEquals(3, output.write(buffer))
        assertFalse(buffer.hasRemaining())
        assertArrayEquals(bytes(3, 4, 5), written())
    }

    @Test
    fun `a truncate cuts it short, and what was cut does not come back`() {
        output.write(ByteBuffer.wrap(ByteArray(10) { 9 }))
        output.truncate(5)
        assertEquals(5L, output.size)
        assertEquals(5L, output.position)
        output.position = 8
        output.write(ByteBuffer.wrap(bytes(1)))
        assertArrayEquals(bytes(9, 9, 9, 9, 9, 0, 0, 0, 1), written())
        output.truncate(20)
        assertEquals(9L, output.size, "a truncate past the end changes nothing")
    }

    @Test
    fun `past its limit it refuses, and says it is too large`() {
        output.write(ByteBuffer.wrap(ByteArray(60)))
        assertFalse(output.overflowed)
        assertThrows(IOException::class.java) { output.write(ByteBuffer.wrap(ByteArray(5))) }
        assertTrue(output.overflowed)
        assertEquals(60L, output.size)
    }

    @Test
    fun `closed by the muxer, it takes no more and keeps what it has`() {
        output.write(ByteBuffer.wrap(bytes(1, 2)))
        output.close()
        assertFalse(output.isOpen)
        assertThrows(ClosedChannelException::class.java) { output.write(ByteBuffer.wrap(bytes(3))) }
        assertArrayEquals(bytes(1, 2), written())
        output.release()
        assertEquals(0L, output.size)
        assertArrayEquals(ByteArray(0), written())
    }

    @Test
    fun `a position before the start is refused`() {
        assertThrows(IllegalArgumentException::class.java) { output.position = -1 }
    }
}
