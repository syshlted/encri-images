/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.device

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.systemhalted.encri.images.device.OnPhone.log
import com.systemhalted.encri.images.device.OnPhone.timed
import com.systemhalted.encri.images.edit.MemoryOutput
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Where an edited video is held on a phone (I22): more of it than the Java heap could hold, however empty, is written
 * and read back whole. Direct buffers, which on Android are arrays in that heap, ran out of it.
 *
 * Run by hand, `scripts/device/instrument <serial> app EditMemoryOnPhone`: it takes the heap's size and more of the
 * phone's memory for a few seconds. It reads and writes nothing of the vault.
 */
@RunWith(AndroidJUnit4::class)
class EditMemoryOnPhone {
    @Test
    fun holdsMoreThanTheJavaHeap() {
        val heap = Runtime.getRuntime().maxMemory()
        val bytes = (heap + BEYOND_HEAP) / CHUNK * CHUNK
        val output = MemoryOutput(limit = bytes)
        try {
            val written = MessageDigest.getInstance(DIGEST)
            val (_, writing) =
                timed {
                    var at = 0L
                    while (at < bytes) {
                        val chunk = chunkAt(at)
                        written.update(chunk)
                        output.write(ByteBuffer.wrap(chunk))
                        at += CHUNK
                    }
                }
            assertEquals(bytes, output.size)
            val read = MessageDigest.getInstance(DIGEST)
            val (_, reading) = timed { output.writeTo(Digesting(read)) }
            assertArrayEquals("what was written reads back whole", written.digest(), read.digest())
            log("edit memory: heap ${heap / MIB} MiB, held ${bytes / MIB} MiB")
            log("edit memory: written in $writing ms, read in $reading ms")
        } finally {
            output.release()
        }
    }

    /** A chunk that says where it is, so that pieces out of order would not read back the same. */
    private fun chunkAt(at: Long): ByteArray {
        val chunk = ByteArray(CHUNK) { (it + at / CHUNK).toByte() }
        ByteBuffer.wrap(chunk).putLong(at)
        return chunk
    }

    private class Digesting(
        private val digest: MessageDigest,
    ) : OutputStream() {
        override fun write(b: Int) = digest.update(b.toByte())

        override fun write(
            b: ByteArray,
            off: Int,
            len: Int,
        ) = digest.update(b, off, len)
    }

    private companion object {
        const val MIB = 1L shl 20
        const val CHUNK = 1 shl 20

        /** Past what the heap could hold even with nothing else in it. */
        const val BEYOND_HEAP = 64L shl 20
        const val DIGEST = "SHA-256"
    }
}
