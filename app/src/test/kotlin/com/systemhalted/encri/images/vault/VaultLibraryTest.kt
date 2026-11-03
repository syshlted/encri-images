/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.vault

import android.app.Application
import com.systemhalted.encri.client.Failure
import com.systemhalted.encri.client.VaultEvent
import com.systemhalted.encri.images.datasources.MediaError
import com.systemhalted.encri.images.models.MediaType
import com.systemhalted.encri.images.models.RequestStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class VaultLibraryTest {
    private val tree =
        mapOf(
            "root" to
                listOf(document("photo", modified = 2), document("film", "video/mp4", modified = 1), folder("sub")),
            "sub" to listOf(document("other", modified = 3, parentId = "sub")),
        )

    private class Rig(
        val session: FakeVaultSession,
        val link: VaultLink,
        val library: VaultLibrary,
    )

    private fun TestScope.rig(session: FakeVaultSession = FakeVaultSession(tree = tree)): Rig {
        val scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
        val link = VaultLink({ Opening.Open(session) }, scope)
        return Rig(session, link, VaultLibrary(link, scope))
    }

    private fun Rig.medias() = (library.state.value as RequestStatus.Success).data.medias

    @Test
    fun `nothing is known until a session opens`() =
        runTest {
            val rig = rig()
            assertTrue(rig.library.state.value is RequestStatus.Loading)
            assertEquals(emptyList<String>(), rig.session.listed)
        }

    @Test
    fun `an open session is walked`() =
        runTest {
            val rig = rig()
            rig.link.open()
            assertEquals(3, rig.medias().size)
        }

    @Test
    fun `a lock forgets everything, and an unlock walks again`() =
        runTest {
            val rig = rig()
            rig.link.open()
            rig.session.events.emit(VaultEvent.Locked)
            assertTrue(rig.library.state.value is RequestStatus.Loading)
            rig.session.events.emit(VaultEvent.Unlocked)
            assertEquals(3, rig.medias().size)
        }

    @Test
    fun `a walk that finds the vault locked locks the link, and forgets everything`() =
        runTest {
            val rig = rig(FakeVaultSession(tree = tree).apply { listFailures["sub"] = Failure.LOCKED })
            rig.link.open()
            assertEquals(LinkState.Open(rig.session, locked = true), rig.link.state.value)
            assertTrue(rig.library.state.value is RequestStatus.Loading)
        }

    @Test
    fun `a session that ends forgets everything`() =
        runTest {
            val session = FakeVaultSession(tree = tree)
            val scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
            val openings = ArrayDeque(listOf(Opening.Open(session), Opening.AccessOff))
            val link = VaultLink({ openings.removeFirst() }, scope)
            val library = VaultLibrary(link, scope)
            link.open()
            session.events.emit(VaultEvent.Ended)
            assertTrue(library.state.value is RequestStatus.Loading)
        }

    @Test
    fun `reload walks again`() =
        runTest {
            val rig = rig()
            rig.link.open()
            rig.session.listed.clear()
            rig.library.reload()
            assertEquals(listOf("root", "sub"), rig.session.listed)
        }

    @Test
    fun `a walk that finds the same vault does not redraw it`() =
        runTest {
            val rig = rig()
            rig.link.open()
            val before = rig.library.state.value
            rig.library.reload()
            assertSame(before, rig.library.state.value)
        }

    @Test
    fun `a walk that finds a different vault does`() =
        runTest {
            val changing = mutableMapOf("root" to listOf(document("a")))
            val rig = rig(FakeVaultSession(tree = changing))
            rig.link.open()
            val before = rig.library.state.value
            changing["root"] = listOf(document("a"), document("b"))
            rig.library.reload()
            assertNotSame(before, rig.library.state.value)
            assertEquals(2, rig.medias().size)
        }

    @Test
    fun `changes in a burst are walked once, after they stop`() =
        runTest {
            val rig = rig()
            rig.link.open()
            rig.session.listed.clear()
            repeat(5) {
                rig.session.events.emit(VaultEvent.FolderChanged("root"))
                advanceTimeBy(100)
            }
            assertEquals(emptyList<String>(), rig.session.listed)
            advanceTimeBy(500)
            runCurrent()
            assertEquals(listOf("root", "sub"), rig.session.listed)
        }

    @Test
    fun `a failed walk reads as an error`() =
        runTest {
            val rig = rig(FakeVaultSession(tree = tree).apply { listFailures["root"] = Failure.FAILED })
            rig.link.open()
            assertEquals(MediaError.IO, (rig.library.state.value as RequestStatus.Error).error)
        }

    @Test
    fun `a walk that finds the session gone ends it, and the gate says why there is none`() =
        runTest {
            val session = FakeVaultSession(tree = tree).apply { listFailures["root"] = Failure.DISCONNECTED }
            val scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
            val openings = ArrayDeque(listOf(Opening.Open(session), Opening.Unavailable))
            val link = VaultLink({ openings.removeFirst() }, scope)
            val library = VaultLibrary(link, scope)
            link.open()
            assertTrue(session.closed)
            assertEquals(LinkState.Refused(Opening.Unavailable), link.state.value)
            assertTrue(library.state.value is RequestStatus.Loading)
        }

    @Test
    fun `the data source filters by type and finds an album`() =
        runTest {
            val rig = rig()
            rig.link.open()
            val source = VaultDataSource(rig.library)

            val videos = source.reels(MediaType.VIDEO, null).first() as RequestStatus.Success
            assertEquals(listOf("video/mp4"), videos.data.map { it.mimeType })

            val jpegs = source.reels(null, "image/jpeg").first() as RequestStatus.Success
            assertEquals(2, jpegs.data.size)

            val albums = source.albums(null, null).first() as RequestStatus.Success
            assertEquals(2, albums.data.size)
            val withVideo = source.albums(MediaType.VIDEO, null).first() as RequestStatus.Success
            assertEquals(listOf(VaultUris.folder("root")), withVideo.data.map { it.uri })

            val album = source.album(VaultUris.folder("sub")).first() as RequestStatus.Success
            assertEquals(1, album.data.second.size)
        }

    @Test
    fun `the viewer opens at the media that was tapped`() =
        runTest {
            val rig = rig()
            rig.link.open()
            val medias = rig.medias()
            assertEquals(2, initialPositionIn(medias, VaultUris.document("film")))
        }

    @Test
    fun `the viewer opens at the start when what was tapped has gone`() =
        runTest {
            val rig = rig()
            rig.link.open()
            assertEquals(0, initialPositionIn(rig.medias(), VaultUris.document("deleted")))
            assertEquals(0, initialPositionIn(rig.medias(), null))
        }

    @Test
    fun `an album that is gone reads as not found`() =
        runTest {
            val rig = rig()
            rig.link.open()
            val answer = VaultDataSource(rig.library).album(VaultUris.folder("nowhere")).first()
            assertEquals(MediaError.NOT_FOUND, (answer as RequestStatus.Error).error)
        }

    @Test
    fun `an album asked for before anything is known waits`() =
        runTest {
            val answer = VaultDataSource(rig().library).album(VaultUris.folder("sub")).first()
            assertTrue(answer is RequestStatus.Loading)
        }

    @Test
    fun `an album asked for after a failure carries the failure`() =
        runTest {
            val rig = rig(FakeVaultSession(tree = tree).apply { listFailures["root"] = Failure.FAILED })
            rig.link.open()
            val answer = VaultDataSource(rig.library).album(VaultUris.folder("sub")).first()
            assertEquals(MediaError.IO, (answer as RequestStatus.Error).error)
        }
}
