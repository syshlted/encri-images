/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.vault

import android.app.Application
import com.systemhalted.encri.client.EncriGrant
import com.systemhalted.encri.client.EncriResult
import com.systemhalted.encri.client.Failure
import com.systemhalted.encri.client.Right
import com.systemhalted.encri.client.VaultEvent
import com.systemhalted.encri.images.models.Media
import com.systemhalted.encri.images.models.RequestStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CopiesTest {
    private val tree =
        mapOf(
            "root" to listOf(folder("trip")),
            "trip" to listOf(document("beach", modified = 2, parentId = "trip")),
        )

    private fun grantOf(vararg rights: Right) =
        EncriResult.Ok(EncriGrant(rights.toSet(), folder("root", parentId = null)))

    private class Rig(
        val session: FakeVaultSession,
        val link: VaultLink,
        val library: VaultLibrary,
        val copies: Copies,
    ) {
        fun beach(): Media =
            (library.state.value as RequestStatus.Success)
                .data.medias
                .single { it.uri == VaultUris.document("beach") }
    }

    private fun TestScope.rig(vararg sessions: FakeVaultSession = arrayOf(FakeVaultSession(tree = tree))): Rig {
        val scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
        var opened = 0
        val link = VaultLink({ Opening.Open(sessions[minOf(opened++, sessions.lastIndex)]) }, scope)
        val library = VaultLibrary(link, scope)
        link.open()
        return Rig(sessions.first(), link, library, Copies(link, library))
    }

    private val bytes = byteArrayOf(1, 2, 3, 4)

    @Test
    fun `a copy is made beside the original, written, and walked to`() =
        runTest {
            val rig = rig()
            val listedBefore = rig.session.listed.size

            val saved = rig.copies.save(rig.beach(), "beach (edited).jpg", "image/jpeg") { it.write(bytes) }

            assertEquals(Saved.Kept(VaultUris.document("made-1")), saved)
            assertEquals(listOf(Triple("trip", "beach (edited).jpg", "image/jpeg")), rig.session.created)
            assertArrayEquals(bytes, rig.session.written["made-1"])
            assertTrue("the library walks again, to find it", rig.session.listed.size > listedBefore)
            assertEquals(emptyList<String>(), rig.session.deleted)
        }

    @Test
    fun `a copy encri will not make is not allowed, or failed, and nothing is written`() =
        runTest {
            val rig = rig()
            rig.session.create = EncriResult.Failed(Failure.NOT_AUTHORIZED, "no CREATE")
            assertEquals(Saved.NotAllowed, rig.copies.save(rig.beach(), "a.jpg", "image/jpeg") { it.write(bytes) })
            rig.session.create = EncriResult.Failed(Failure.NO_SPACE, "full")
            assertEquals(Saved.Failed, rig.copies.save(rig.beach(), "a.jpg", "image/jpeg") { it.write(bytes) })
            assertTrue(rig.session.written.isEmpty())
        }

    @Test
    fun `a copy made and not filled is deleted again`() =
        runTest {
            val rig = rig()
            rig.session.writeFailure = Failure.NOT_AUTHORIZED
            assertEquals(Saved.NotAllowed, rig.copies.save(rig.beach(), "a.jpg", "image/jpeg") { it.write(bytes) })
            assertEquals(listOf("made-1"), rig.session.deleted)

            rig.session.writeFailure = null
            val saved = rig.copies.save(rig.beach(), "a.jpg", "image/jpeg") { throw IOException("no memory for it") }
            assertEquals(Saved.Failed, saved)
            assertEquals(listOf("made-1", "made-2"), rig.session.deleted)
        }

    @Test
    fun `a save stopped while it writes deletes what it made`() =
        runTest {
            val rig = rig()
            rig.session.writing = CompletableDeferred()
            val writing = launch { rig.copies.save(rig.beach(), "a.jpg", "image/jpeg") { it.write(bytes) } }
            runCurrent()
            assertEquals("made, and waiting to be written", 1, rig.session.created.size)
            writing.cancel()
            writing.join()
            assertEquals(listOf("made-1"), rig.session.deleted)
            assertNull(rig.session.written["made-1"])
        }

    @Test
    fun `what is not in a folder of the vault is never asked`() =
        runTest {
            val rig = rig()
            val elsewhere = rig.beach().copy(albumUri = VaultUris.document("not-a-folder"))
            assertEquals(Saved.Failed, rig.copies.save(elsewhere, "a.jpg", "image/jpeg") { it.write(bytes) })
            assertTrue(rig.session.created.isEmpty())
        }

    @Test
    fun `after encri's consent screen, it waits for a session that may make and write`() =
        runTest {
            val before = FakeVaultSession(tree = tree).apply { grant = grantOf(Right.READ, Right.DELETE) }
            val after =
                FakeVaultSession(tree = tree).apply {
                    grant = grantOf(Right.READ, Right.DELETE, Right.CREATE, Right.WRITE)
                }
            val rig = rig(before, after)
            assertEquals(setOf(Right.READ, Right.DELETE), rig.copies.held())

            val allowed = async { rig.copies.awaitAllowed() }
            assertFalse(allowed.isCompleted)
            before.events.emit(VaultEvent.Ended)
            assertTrue(allowed.await())
        }

    @Test
    fun `a grant that only makes, or only writes, is not enough, and is given up on`() =
        runTest {
            val rig = rig()
            rig.session.grant = grantOf(Right.READ, Right.CREATE)
            val allowed = async { rig.copies.awaitAllowed() }
            advanceTimeBy(Rights.ALLOWING_MILLIS + 1)
            assertFalse(allowed.await())
            rig.session.grant = EncriResult.Failed(Failure.LOCKED, "locked")
            assertNull(rig.copies.held())
        }

    @Test
    fun `a copy is named after the original, marked once, with its own extension`() {
        assertEquals("IMG_1 (edited).jpg", Copies.nameOf("IMG_1.jpg", "jpg"))
        assertEquals("IMG_1 (edited).jpg", Copies.nameOf("IMG_1.HEIC", "jpg"))
        assertEquals("IMG_1 (edited).jpg", Copies.nameOf("IMG_1 (edited).jpg", "jpg"))
        assertEquals("IMG_1 (edited).jpg", Copies.nameOf("IMG_1 (edited) (3).jpg", "jpg"))
        assertEquals("clip.v2 (edited).mp4", Copies.nameOf("clip.v2.mov", "mp4"))
        assertEquals("notes (edited).png", Copies.nameOf("notes", "png"))
        assertEquals(".hidden (edited).png", Copies.nameOf(".hidden", "png"))
        assertEquals("copy (edited).jpg", Copies.nameOf("", "jpg"))
    }

    @Test
    fun `a consent asks for what is wanted and every right held, reading always`() {
        assertEquals(
            setOf(Right.READ, Right.DELETE, Right.CREATE, Right.WRITE),
            Rights.toAsk(setOf(Right.READ, Right.DELETE), Copies.NEEDED),
        )
        assertEquals(setOf(Right.READ, Right.DELETE), Rights.toAsk(emptySet(), setOf(Right.DELETE)))
    }
}
