/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.vault

import com.systemhalted.encri.client.EncriResult
import com.systemhalted.encri.client.Right
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * What encri lets this app do in the vault, and how it asks for more (AGENTS.md I11, I22).
 *
 * A right is asked for when it is first used, so the gate asks for [Right.READ] alone. But what the user allows on
 * encri's consent screen replaces the grant with exactly the rights asked for (encri-api L23), so a consent for one
 * more right asks for every right the grant already holds as well ([toAsk]): asking for `DELETE` alone would take
 * away the rights to save a copy, and asking for those alone would take away `DELETE`.
 */
class Rights(
    private val link: VaultLink,
) {
    /**
     * The rights encri's grant holds now, or null when encri did not say. Asked each time rather than remembered,
     * because the user can change them in encri.
     */
    suspend fun held(): Set<Right>? = (link.ask { it.grant() } as? EncriResult.Ok)?.value?.rights

    /**
     * Whether encri's grant holds every one of [wanted], once its consent screen said it would. encri ends the session
     * whose grant it replaces, and the link asks for the next one at once (encri-files F5), so this waits up to
     * [millis] for a session whose grant says so, rather than asking the one encri is ending.
     */
    suspend fun awaitHeld(
        wanted: Set<Right>,
        millis: Long = ALLOWING_MILLIS,
    ): Boolean =
        withTimeoutOrNull(millis) {
            var asked: VaultSession? = null
            var held = false
            while (!held) {
                val open = link.state.first { it is LinkState.Open && !it.locked && it.session !== asked }
                asked = (open as LinkState.Open).session
                val rights = (link.ask(asked) { it.grant() } as? EncriResult.Ok)?.value?.rights.orEmpty()
                held = rights.containsAll(wanted)
            }
            held
        } ?: false

    companion object {
        /** encri answers its consent screen in well under a second; this is for a phone that is slow to. */
        const val ALLOWING_MILLIS = 5_000L

        /** What to ask encri's consent screen for, so this app may do [wanted] and keeps all that [held] lets it. */
        fun toAsk(
            held: Set<Right>,
            wanted: Set<Right>,
        ): Set<Right> = held + wanted + Right.READ
    }
}
