/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.vault

import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import com.systemhalted.encri.client.EncriResult
import kotlinx.coroutines.runBlocking
import java.io.FileInputStream
import java.io.IOException
import kotlin.math.min

/**
 * What ExoPlayer plays a video from: a descriptor encri serves, seeking by position (AGENTS.md I4).
 *
 * Over the descriptor rather than a pipe because a player seeks. ExoPlayer calls this from its loading
 * thread, so waiting for encri here holds up nothing a user sees. It keeps nothing: a video's bytes are in the
 * player's buffers and nowhere else.
 */
@OptIn(UnstableApi::class)
class VaultMediaSource(
    private val link: VaultLink,
) : BaseDataSource(false) {
    private var uri: Uri? = null
    private var descriptor: ParcelFileDescriptor? = null
    private var input: FileInputStream? = null
    private var remaining = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val documentId =
            VaultUris.documentIdOf(dataSpec.uri)
                ?: throw IOException("not a document in the vault")
        val held =
            when (val answer = runBlocking { link.ask { it.openRead(documentId) } }) {
                is EncriResult.Ok -> answer.value
                is EncriResult.Failed -> throw IOException("encri answered ${answer.failure}")
            }
        uri = dataSpec.uri
        descriptor = held
        val stream = FileInputStream(held.fileDescriptor).also { input = it }
        val channel = stream.channel
        val length = channel.size()
        if (dataSpec.position > length) {
            throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
        }
        channel.position(dataSpec.position)
        remaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) length - dataSpec.position else dataSpec.length
        opened = true
        transferStarted(dataSpec)
        return remaining
    }

    @Suppress("ReturnCount") // One per way a read ends: nothing asked, nothing left, the end, or some bytes.
    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val stream = input ?: throw IOException("read before open")
        val read = stream.read(buffer, offset, min(length.toLong(), remaining).toInt())
        if (read == -1) return C.RESULT_END_OF_INPUT
        remaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        runCatching { input?.close() }
        runCatching { descriptor?.close() }
        input = null
        descriptor = null
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    class Factory(
        private val link: VaultLink,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = VaultMediaSource(link)
    }
}
