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
 *
 * **An answer is heard as well as encri's events** (I7). The client drops its oldest events when they come faster
 * than they are read (encri-api L12), so a lock, an unlock or the end could go unsaid. Every request asked through
 * the link says which of those it found: [Failure.LOCKED] is the lock, any other answer is a vault that is open,
 * and [Failure.ENDED] or [Failure.DISCONNECTED] is the end. An answer counts only if nothing changed while it was
 * on its way, so a request asked before a lock cannot undo it.
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
    private var probing: Job? = null

    /** How many times the link's state has changed: what an answer is checked against. Changed in [scope]. */
    @Volatile
    private var generation = 0

    /**
     * Asks encri for a session, unless one is open or already being asked for. A session believed locked is
     * asked whether it still is, because the unlock may have gone unsaid; this is called on every return to the
     * gallery and after encri's unlock screen answers.
     */
    fun open() {
        val current = mutableState.value
        if (current is LinkState.Open) {
            if (current.locked && probing?.isActive != true) {
                probing = scope.launch { ask(current.session) { it.grant() } }
            }
            return
        }
        if (asking?.isActive == true) return
        set(LinkState.Connecting)
        asking =
            scope.launch {
                when (val outcome = opener.open()) {
                    is Opening.Open -> adopt(outcome.session)
                    else -> set(LinkState.Refused(outcome))
                }
            }
    }

    /** Whatever [request] answers, asked of the open session, or why there is no session to ask. */
    suspend fun <T> ask(request: suspend (VaultSession) -> EncriResult<T>): EncriResult<T> {
        val open = mutableState.value as? LinkState.Open ?: return EncriResult.Failed(Failure.ENDED, "no session")
        return ask(open.session, request)
    }

    /** [request], asked of [session] in particular, and heard as [ask] hears it: for a walk begun for one session. */
    suspend fun <T> ask(
        session: VaultSession,
        request: suspend (VaultSession) -> EncriResult<T>,
    ): EncriResult<T> {
        val before = generation
        val answer = request(session)
        scope.launch { if (generation == before) heard(session, answer) }
        return answer
    }

    /** What [answer] says about [session]: locked, open, or over. */
    private fun heard(
        session: VaultSession,
        answer: EncriResult<*>,
    ) {
        when ((answer as? EncriResult.Failed)?.failure) {
            Failure.LOCKED -> setLocked(session, true)

            Failure.ENDED, Failure.DISCONNECTED -> ended(session)

            // Failed in some other way, or answered: either way the vault was open to answer it.
            else -> setLocked(session, false)
        }
    }

    private fun set(state: LinkState) {
        if (mutableState.value == state) return
        mutableState.value = state
        generation++
    }

    private fun adopt(session: VaultSession) {
        set(LinkState.Open(session, locked = false))
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
        if (open.session === session) set(open.copy(locked = locked))
    }

    private fun ended(session: VaultSession) {
        session.close()
        if ((mutableState.value as? LinkState.Open)?.session !== session) return
        set(LinkState.Idle)
        open()
    }

    private companion object {
        const val CHANGES_BUFFERED = 16
    }
}
