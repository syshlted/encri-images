/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.edit

import android.app.ActivityManager
import android.content.Context
import android.os.SharedMemory
import android.system.ErrnoException
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.muxer.SeekableMuxerOutput
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import kotlin.math.min

/**
 * Where an edited video is muxed (I22): memory, in pieces of [piece] bytes, and never a file.
 *
 * An MP4 is written, then gone back over, so the muxer needs somewhere that seeks, and encri's pipe does not; a file
 * would put the video on this phone's storage in plaintext (I3). So the whole video is held here, up to [limit] bytes,
 * and then written to encri in order ([writeTo]).
 *
 * The pieces are [SharedMemory], outside the Java heap, and are dropped by [release]. Not direct buffers: on Android
 * those are arrays in the Java heap, which a video ran out of long before [limit], and Transformer catches no
 * `OutOfMemoryError`. A piece is memory only where it has been written, so the last costs no more than what is in it.
 */
@OptIn(UnstableApi::class)
class MemoryOutput(
    private val limit: Long,
    private val piece: Int = PIECE,
) : SeekableMuxerOutput {
    private val pieces = ArrayList<Piece>()
    private var position = 0L
    private var size = 0L
    private var open = true

    /** Whether a write was refused for going past [limit]: the video is too large to edit here. */
    var overflowed = false
        private set

    override fun write(src: ByteBuffer): Int {
        if (!open) throw ClosedChannelException()
        val count = src.remaining()
        if (position + count > limit) {
            overflowed = true
            throw IOException("an edited video past $limit bytes")
        }
        while (src.hasRemaining()) {
            val at = (position % piece).toInt()
            val taken = min(src.remaining(), piece - at)
            val into = pieceAt(position).duplicate()
            into.position(at)
            val from = src.duplicate()
            from.limit(from.position() + taken)
            into.put(from)
            src.position(src.position() + taken)
            position += taken
            size = maxOf(size, position)
        }
        return count
    }

    override fun getPosition(): Long = position

    override fun setPosition(position: Long) {
        require(position >= 0) { "a position before the start" }
        this.position = position
    }

    override fun getSize(): Long = size

    override fun truncate(size: Long) {
        if (size >= this.size) return
        // What is cut off is cleared, so that a write past it later does not bring it back.
        var at = size
        while (at < this.size) {
            val offset = (at % piece).toInt()
            val cleared = min((this.size - at), (piece - offset).toLong()).toInt()
            val into = pieceAt(at).duplicate()
            into.position(offset)
            into.put(ByteArray(cleared))
            at += cleared
        }
        this.size = size
        position = min(position, size)
    }

    override fun isOpen(): Boolean = open

    /** Closed by the muxer when it is done; what it wrote stays, to be written to encri. */
    override fun close() {
        open = false
    }

    /** Everything written, in order, to [out]. */
    fun writeTo(out: OutputStream) {
        val chunk = ByteArray(COPY)
        var at = 0L
        while (at < size) {
            val offset = (at % piece).toInt()
            val taken = min(min(size - at, (piece - offset).toLong()), COPY.toLong()).toInt()
            val from = pieceAt(at).duplicate()
            from.position(offset)
            from.get(chunk, 0, taken)
            out.write(chunk, 0, taken)
            at += taken
        }
    }

    /** Drops every piece. */
    fun release() {
        open = false
        pieces.forEach(Piece::release)
        pieces.clear()
        size = 0
        position = 0
    }

    private fun pieceAt(at: Long): ByteBuffer {
        val index = (at / piece).toInt()
        while (pieces.size <= index) {
            // The phone would not give memory for the next piece: as good as past the limit.
            pieces += Piece.of(piece) ?: throw IOException("no memory for a video past $size bytes").also {
                overflowed = true
            }
        }
        return pieces[index].buffer
    }

    /** [buffer], mapped from [memory] into this process. */
    private class Piece(
        private val memory: SharedMemory,
        val buffer: ByteBuffer,
    ) {
        fun release() {
            SharedMemory.unmap(buffer)
            memory.close()
        }

        companion object {
            /** A piece [size] bytes long, or null when the phone has no memory for it. */
            fun of(size: Int): Piece? {
                val memory =
                    try {
                        SharedMemory.create(null, size)
                    } catch (_: ErrnoException) {
                        return null
                    }
                return try {
                    Piece(memory, memory.mapReadWrite())
                } catch (_: ErrnoException) {
                    memory.close()
                    null
                }
            }
        }
    }

    companion object {
        /** 1 GiB is 64 of them, a descriptor each, of the 32,768 an app may hold on the G6 and the A20. */
        private const val PIECE = 16 shl 20
        private const val COPY = 64 * 1024

        /** No more than an eighth of this phone's memory, and no more than 1 GiB, is held for one video. */
        private const val SHARE_OF_MEMORY = 8
        private const val MOST = 1L shl 30

        /** How large an edited video this phone can hold: an eighth of its memory, at most 1 GiB. */
        fun limitOn(context: Context): Long {
            val memory = ActivityManager.MemoryInfo()
            context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(memory)
            return min(memory.totalMem / SHARE_OF_MEMORY, MOST)
        }
    }
}
