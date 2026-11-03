/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.vault

import android.app.Application
import com.systemhalted.encri.client.EncriResult
import com.systemhalted.encri.client.Failure
import com.systemhalted.encri.client.VaultEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class VaultLinkTest {
    private fun TestScope.linkTo(vararg openings: Opening): VaultLink {
        val queue = ArrayDeque(openings.toList())
        return VaultLink(
            { queue.removeFirstOrNull() ?: Opening.Unavailable },
            CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
        )
    }

    @Test
    fun `nothing is asked until something asks`() =
        runTest {
            assertEquals(LinkState.Idle, linkTo(Opening.Locked).state.value)
        }

    @Test
    fun `a refusal is kept, with its reason`() =
        runTest {
            val link = linkTo(Opening.NotAuthorized)
            link.open()
            assertEquals(LinkState.Refused(Opening.NotAuthorized), link.state.value)
        }

    @Test
    fun `asking again after a refusal asks encri again`() =
        runTest {
            val opened = FakeVaultSession()
            val link = linkTo(Opening.Locked, Opening.Open(opened))
            link.open()
            link.open()
            assertEquals(LinkState.Open(opened, locked = false), link.state.value)
        }

    @Test
    fun `an open session is not asked for twice`() =
        runTest {
            val first = FakeVaultSession()
            val link = linkTo(Opening.Open(first), Opening.Locked)
            link.open()
            link.open()
            assertEquals(LinkState.Open(first, locked = false), link.state.value)
        }

    @Test
    fun `a lock and an unlock are reflected without a new session`() =
        runTest {
            val opened = FakeVaultSession()
            val link = linkTo(Opening.Open(opened))
            link.open()
            opened.events.emit(VaultEvent.Locked)
            assertEquals(LinkState.Open(opened, locked = true), link.state.value)
            opened.events.emit(VaultEvent.Unlocked)
            assertEquals(LinkState.Open(opened, locked = false), link.state.value)
        }

    @Test
    fun `a session encri ends is closed and asked for again at once`() =
        runTest {
            val opened = FakeVaultSession()
            val link = linkTo(Opening.Open(opened), Opening.AccessOff)
            link.open()
            opened.events.emit(VaultEvent.Ended)
            assertTrue(opened.closed)
            assertEquals(LinkState.Refused(Opening.AccessOff), link.state.value)
        }

    @Test
    fun `a folder that changed is passed on`() =
        runTest {
            val opened = FakeVaultSession()
            val link = linkTo(Opening.Open(opened))
            val heard = mutableListOf<String>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { link.changes.collect(heard::add) }
            link.open()
            opened.events.emit(VaultEvent.FolderChanged("trip"))
            assertEquals(listOf("trip"), heard)
        }

    @Test
    fun `a request with no session says so`() =
        runTest {
            val answer = linkTo().ask { EncriResult.Ok(1) }
            assertEquals(Failure.ENDED, (answer as EncriResult.Failed).failure)
        }

    @Test
    fun `a request with a session reaches it`() =
        runTest {
            val opened = FakeVaultSession()
            val link = linkTo(Opening.Open(opened))
            link.open()
            assertEquals(EncriResult.Ok("root"), link.ask { EncriResult.Ok(it.root.id) })
        }
}
