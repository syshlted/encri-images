/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.vault

import android.app.Application
import com.systemhalted.encri.images.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class VaultGateTest {
    private val session = FakeVaultSession()

    @Test
    fun `an open, unlocked vault has no gate`() {
        assertNull(gateOf(LinkState.Open(session, locked = false)))
    }

    @Test
    fun `a locked session is gated though it is still open`() {
        assertEquals(GateAction.UNLOCK, gateOf(LinkState.Open(session, locked = true))?.action)
    }

    @Test
    fun `while connecting there is nothing to press`() {
        assertNull(gateOf(LinkState.Idle)!!.action)
        assertNull(gateOf(LinkState.Connecting)!!.action)
    }

    @Test
    fun `every refusal has words of its own and the action that fixes it`() {
        val expected =
            mapOf(
                Opening.Locked to GateAction.UNLOCK,
                Opening.NotAuthorized to GateAction.ALLOW,
                Opening.AccessOff to GateAction.ALLOW,
                Opening.NotInstalled to GateAction.RETRY,
                Opening.NotTrusted to GateAction.RETRY,
                Opening.TooOld to GateAction.RETRY,
                Opening.Unavailable to GateAction.RETRY,
            )
        expected.forEach { (why, action) ->
            assertEquals(why.toString(), action, gateOf(LinkState.Refused(why))?.action)
        }
        assertEquals(
            expected.size,
            expected.keys
                .map { gateOf(LinkState.Refused(it))!!.message }
                .toSet()
                .size,
        )
    }

    @Test
    fun `a session is never a refusal`() {
        assertEquals(R.string.vault_gate_connecting, gateOf(LinkState.Refused(Opening.Open(session)))?.message)
    }
}
