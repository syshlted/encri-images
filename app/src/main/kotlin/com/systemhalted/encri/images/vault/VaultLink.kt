/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.vault

import com.systemhalted.encri.client.EncriResult
import com.systemhalted.encri.client.Failure
import com.systemhalted.encri.client.VaultEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch

/** Where the app stands with encri. */
sealed interface LinkState {
    /** Nothing has asked encri yet. */
    data object Idle : LinkState

    /** Asking encri for a session. */
    data object Connecting : LinkState

    /** encri gave no session, and [why] says why. Never [Opening.Open]. */
    data class Refused(
        val why: Opening,
    ) : LinkState

    /** A session, which outlives a lock (encri D120): while [locked], every request answers that it is locked. */
    data class Open(
        val session: VaultSession,
        val locked: Boolean,
    ) : LinkState
}

/**
 * The app's one session with encri, for the life of the process (encri-files F2's arrangement).
 *
 * Held by the application rather than by a screen, so a rotation does not end the session. A session encri
 * ends is asked for again at once, so the gate says why there is none rather than showing a vault that has
 * gone.
 */
class VaultLink(
    private val opener: Opener,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow<LinkState>(LinkState.Idle)
    val state: StateFlow<LinkState> = mutableState.asStateFlow()

    // Best effort, as encri's own notice of a change is, so a slow collector loses the oldest.
    private val mutableChanges =
        MutableSharedFlow<String>(extraBufferCapacity = CHANGES_BUFFERED, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Folders whose children encri said changed. */
    val changes: SharedFlow<String> = mutableChanges.asSharedFlow()

    private var asking: Job? = null

    /** Asks encri for a session, unless one is open or already being asked for. */
    fun open() {
        if (mutableState.value is LinkState.Open || asking?.isActive == true) return
        mutableState.value = LinkState.Connecting
        asking =
            scope.launch {
                when (val outcome = opener.open()) {
                    is Opening.Open -> adopt(outcome.session)
                    else -> mutableState.value = LinkState.Refused(outcome)
                }
            }
    }

    /** Whatever [request] answers, asked of the open session, or why there is no session to ask. */
    suspend fun <T> ask(request: suspend (VaultSession) -> EncriResult<T>): EncriResult<T> =
        (mutableState.value as? LinkState.Open)?.session?.let { request(it) }
            ?: EncriResult.Failed(Failure.ENDED, "no session")

    private fun adopt(session: VaultSession) {
        mutableState.value = LinkState.Open(session, locked = false)
        scope.launch {
            session.events.takeWhile { it != VaultEvent.Ended }.collect { event ->
                when (event) {
                    VaultEvent.Locked -> setLocked(session, true)
                    VaultEvent.Unlocked -> setLocked(session, false)
                    is VaultEvent.FolderChanged -> mutableChanges.tryEmit(event.folderId)
                    VaultEvent.Ended -> Unit
                }
            }
            ended(session)
        }
    }

    private fun setLocked(
        session: VaultSession,
        locked: Boolean,
    ) {
        val open = mutableState.value as? LinkState.Open ?: return
        if (open.session === session) mutableState.value = open.copy(locked = locked)
    }

    private fun ended(session: VaultSession) {
        session.close()
        if ((mutableState.value as? LinkState.Open)?.session !== session) return
        mutableState.value = LinkState.Idle
        open()
    }

    private companion object {
        const val CHANGES_BUFFERED = 16
    }
}
