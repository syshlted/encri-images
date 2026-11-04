/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.vault

import android.app.Application
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.concurrent.thread

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class VaultMediaSourceTest {
    private val bytes = ByteArray(1000) { it.toByte() }
    private val file = File.createTempFile("film", null).apply { writeBytes(bytes) }
    private val session = FakeVaultSession().apply { content["film"] = file }
    private val link =
        VaultLink({ Opening.Open(session) }, CoroutineScope(Dispatchers.Unconfined)).apply { open() }
    private val source = VaultMediaSource(link)
    private val uri = VaultUris.document("film")

    @After
    fun tearDown() {
        source.close()
        file.delete()
    }

    private fun readAll(): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(128)
        while (true) {
            val read = source.read(buffer, 0, buffer.size)
            if (read == C.RESULT_END_OF_INPUT) return out.toByteArray()
            out.write(buffer, 0, read)
        }
    }

    @Test
    fun `it plays from the start, to the end, and says how long`() {
        assertEquals(1000, source.open(DataSpec(uri)))
        assertEquals(uri, source.uri)
        assertArrayEquals(bytes, readAll())
    }

    @Test
    fun `it seeks to where the player asks`() {
        assertEquals(300, source.open(DataSpec(uri, 700, C.LENGTH_UNSET.toLong())))
        assertArrayEquals(bytes.copyOfRange(700, 1000), readAll())
    }

    @Test
    fun `it stops at the length the player asks for`() {
        assertEquals(50, source.open(DataSpec(uri, 100, 50)))
        assertArrayEquals(bytes.copyOfRange(100, 150), readAll())
    }

    @Test
    fun `a read of nothing is nothing`() {
        source.open(DataSpec(uri))
        assertEquals(0, source.read(ByteArray(1), 0, 0))
    }

    @Test
    fun `a position past the end is refused`() {
        assertThrows(IOException::class.java) { source.open(DataSpec(uri, 1001, C.LENGTH_UNSET.toLong())) }
    }

    @Test
    fun `a document that cannot be opened is a failure that names no document`() {
        val thrown =
            assertThrows(IOException::class.java) { source.open(DataSpec(VaultUris.document("secret-holiday"))) }
        assertFalse(thrown.message!!.contains("secret-holiday"))
    }

    @Test
    fun `a uri that is not the vault's is refused`() {
        assertThrows(IOException::class.java) { source.open(DataSpec(Uri.parse("https://example.com/film"))) }
        assertTrue(session.opened.isEmpty())
    }

    @Test
    fun `closing lets go of the descriptor and the uri`() {
        source.open(DataSpec(uri))
        source.close()
        assertNull(source.uri)
        assertThrows(IOException::class.java) { source.read(ByteArray(1), 0, 1) }
    }

    /** Reads [count] bytes, or to the end if there are fewer. */
    private fun VaultMediaSource.readSome(count: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(count)
        while (out.size() < count) {
            val read = read(buffer, 0, count - out.size())
            if (read == C.RESULT_END_OF_INPUT) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    /** A link that gives [sessions] one after another, each time encri is asked again. */
    private fun linkOver(vararg sessions: FakeVaultSession): VaultLink {
        val next = ArrayDeque(sessions.toList())
        return VaultLink(
            { next.removeFirstOrNull()?.let { Opening.Open(it) } ?: Opening.Locked },
            CoroutineScope(Dispatchers.Unconfined),
        ).apply { open() }
    }

    private fun sessionWithFilm() = FakeVaultSession().apply { content["film"] = file }

    @Test
    fun `a read cut off by the session's end goes on from the same byte through the next session`() {
        val ending = sessionWithFilm()
        val next = sessionWithFilm()
        val source = VaultMediaSource(linkOver(ending, next))
        source.open(DataSpec(uri))
        val before = source.readSome(300)

        // encri ends the session whose grant it replaced, and what it served fails (I12).
        ending.over = true
        ending.descriptors.forEach { it.close() }
        val after = source.readSome(1000)

        assertArrayEquals(bytes, before + after)
        assertEquals(listOf("film"), next.opened)
        assertTrue(ending.closed)
        source.close()
    }

    @Test
    fun `a read that fails while its session goes on is opened again through it`() {
        val going = sessionWithFilm()
        val source = VaultMediaSource(linkOver(going))
        source.open(DataSpec(uri, 100, C.LENGTH_UNSET.toLong()))
        val before = source.readSome(200)

        going.descriptors.forEach { it.close() }
        val after = source.readSome(1000)

        assertArrayEquals(bytes.copyOfRange(100, 1000), before + after)
        assertEquals(listOf("film", "film"), going.opened)
        source.close()
    }

    @Test
    fun `a read cut off with no session to follow fails, once the wait is over`() {
        val ending = sessionWithFilm()
        val source = VaultMediaSource(linkOver(ending), nextSessionMillis = 50)
        source.open(DataSpec(uri))
        source.readSome(300)

        ending.over = true
        ending.descriptors.forEach { it.close() }

        assertThrows(IOException::class.java) { source.readSome(1000) }
        source.close()
    }

    @Test
    fun `an open between one session and the next waits for the next`() {
        val coming = CompletableDeferred<Opening>()
        val link = VaultLink({ coming.await() }, CoroutineScope(Dispatchers.Unconfined)).apply { open() }
        thread {
            Thread.sleep(50)
            coming.complete(Opening.Open(sessionWithFilm()))
        }
        val source = VaultMediaSource(link)

        assertEquals(1000, source.open(DataSpec(uri)))
        assertArrayEquals(bytes, source.readSome(1000))
        source.close()
    }

    @Test
    fun `the factory makes a source over this link`() {
        val made = VaultMediaSource.Factory(link).createDataSource()
        assertEquals(1000, made.open(DataSpec(uri)))
        made.close()
    }
}
