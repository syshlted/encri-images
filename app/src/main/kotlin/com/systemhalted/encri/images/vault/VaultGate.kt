/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.vault

import androidx.annotation.StringRes
import com.systemhalted.encri.images.R

/** What the user can do about a [Gate]. */
enum class GateAction(
    @param:StringRes val label: Int,
) {
    /** encri's consent screen, which also offers to turn access for other apps back on. */
    ALLOW(R.string.vault_gate_allow),

    /** encri's unlock screen. */
    UNLOCK(R.string.vault_gate_unlock),

    /** Ask encri again. */
    RETRY(R.string.vault_gate_retry),
}

/** The screen in front of the gallery while there is nothing it may show. */
data class Gate(
    @param:StringRes val message: Int,
    val action: GateAction?,
)

/** The gate [state] calls for, or null when the vault is open and the gallery may show it. */
fun gateOf(state: LinkState): Gate? =
    when (state) {
        LinkState.Idle, LinkState.Connecting -> Gate(R.string.vault_gate_connecting, null)
        is LinkState.Open -> if (state.locked) Gate(R.string.vault_gate_locked, GateAction.UNLOCK) else null
        is LinkState.Refused -> gateOf(state.why)
    }

private fun gateOf(why: Opening): Gate =
    when (why) {
        is Opening.Open -> Gate(R.string.vault_gate_connecting, null)
        Opening.Locked -> Gate(R.string.vault_gate_locked, GateAction.UNLOCK)
        Opening.NotAuthorized -> Gate(R.string.vault_gate_not_authorized, GateAction.ALLOW)
        Opening.AccessOff -> Gate(R.string.vault_gate_access_off, GateAction.ALLOW)
        Opening.NotInstalled -> Gate(R.string.vault_gate_not_installed, GateAction.RETRY)
        Opening.NotTrusted -> Gate(R.string.vault_gate_not_trusted, GateAction.RETRY)
        Opening.TooOld -> Gate(R.string.vault_gate_too_old, GateAction.RETRY)
        Opening.Unavailable -> Gate(R.string.vault_gate_unavailable, GateAction.RETRY)
    }
