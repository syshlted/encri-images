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
import com.systemhalted.encri.client.Failure
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.FileInputStream
import java.io.IOException
import kotlin.math.min

/**
 * What ExoPlayer plays a video from: a descriptor encri serves, seeking by position (AGENTS.md I4).
 *
 * Over the descriptor rather than a pipe because a player seeks. ExoPlayer calls this from its loading
 * thread, so waiting for encri here holds up nothing a user sees. It keeps nothing: a video's bytes are in the
 * player's buffers and nowhere else.
 *
 * **A video outlives the session it was opened in.** encri ends a session whenever the user allows a consent, and a
 * descriptor that session opened then fails after what it had buffered (seen on the S9, I12): the viewer's *Allow in
 * encri* does it to the video behind it (I11). So a read that fails, or an open that finds no session, waits up to
 * [nextSessionMillis] for the link's next session and goes on from the same byte through it.
 */
@OptIn(UnstableApi::class)
class VaultMediaSource(
    private val link: VaultLink,
    private val nextSessionMillis: Long = NEXT_SESSION_MILLIS,
) : BaseDataSource(false) {
    private var uri: Uri? = null
    private var documentId: String? = null
    private var descriptor: ParcelFileDescriptor? = null
    private var input: FileInputStream? = null
    private var position = 0L
    private var remaining = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val document =
            VaultUris.documentIdOf(dataSpec.uri)
                ?: throw IOException("not a document in the vault")
        documentId = document
        uri = dataSpec.uri
        val stream = openAt(document, dataSpec.position)
        val length = stream.channel.size()
        if (dataSpec.position > length) {
            throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
        }
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
        val wanted = min(length.toLong(), remaining).toInt()
        val read =
            try {
                stream.read(buffer, offset, wanted)
            } catch (failed: IOException) {
                reopened(failed).read(buffer, offset, wanted)
            }
        if (read == -1) return C.RESULT_END_OF_INPUT
        position += read
        remaining -= read
        bytesTransferred(read)
        return read
    }

    /** The document opened again where the failed read was, through whichever session the link has now. */
    private fun reopened(failed: IOException): FileInputStream {
        val document = documentId ?: throw failed
        release()
        return try {
            openAt(document, position)
        } catch (again: IOException) {
            again.addSuppressed(failed)
            throw again
        }
    }

    /** [document], opened at [at] through the link's open session, or the next one within [nextSessionMillis]. */
    private fun openAt(
        document: String,
        at: Long,
    ): FileInputStream {
        val answer =
            runBlocking { withTimeoutOrNull(nextSessionMillis) { openThroughSomeSession(document) } }
                ?: throw IOException("encri gave no session to read through")
        val held =
            when (answer) {
                is EncriResult.Ok -> answer.value
                is EncriResult.Failed -> throw IOException("encri answered ${answer.failure}")
            }
        descriptor = held
        val stream = FileInputStream(held.fileDescriptor).also { input = it }
        stream.channel.position(at)
        position = at
        return stream
    }

    /**
     * What opening [document] answers, asked of the link's open session. One that answers that it is over is passed
     * by for the session the link asks encri for next, since hearing that is what makes the link ask.
     */
    private suspend fun openThroughSomeSession(document: String): EncriResult<ParcelFileDescriptor> {
        var over: VaultSession? = null
        while (true) {
            val open = link.state.first { it is LinkState.Open && !it.locked && it.session !== over } as LinkState.Open
            val answer = link.ask(open.session) { it.openRead(document) }
            val failure = (answer as? EncriResult.Failed)?.failure
            if (failure != Failure.ENDED && failure != Failure.DISCONNECTED) return answer
            over = open.session
        }
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        documentId = null
        release()
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    private fun release() {
        runCatching { input?.close() }
        runCatching { descriptor?.close() }
        input = null
        descriptor = null
    }

    class Factory(
        private val link: VaultLink,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = VaultMediaSource(link)
    }

    internal companion object {
        /** As long as [Deletes] waits for the session encri's consent screen leads to, for the same reason. */
        const val NEXT_SESSION_MILLIS = Deletes.ALLOWING_MILLIS
    }
}
