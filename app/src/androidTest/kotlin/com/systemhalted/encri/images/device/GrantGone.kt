/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.device

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.systemhalted.encri.client.ConnectResult
import com.systemhalted.encri.client.Encri
import com.systemhalted.encri.client.EncriResult
import com.systemhalted.encri.client.SessionOutcome
import com.systemhalted.encri.client.VaultEvent
import com.systemhalted.encri.images.device.OnPhone.context
import com.systemhalted.encri.images.device.OnPhone.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/**
 * What a session hears when another app deletes the folder its grant is rooted at (I9's question for encri-api,
 * answered in I12).
 *
 * Run with this app let into one folder only. It opens a session of its own, as any app does, and waits
 * `waitSeconds` (60) while that folder is deleted from elsewhere (`:testvault`'s `RemoveLibrary`), asking encri
 * nothing, so whatever it hears encri said unasked. Then it logs what the session heard, what it answers about
 * its own root and grant, and whether a new session opens. It reads and asks nothing more, as the app's device
 * tests do (I6), and names no document.
 */
@RunWith(AndroidJUnit4::class)
class GrantGone {
    @Test
    fun observe() {
        val connected = runBlocking { Encri.connect(context) }
        assumeTrue("not connected to encri: $connected", connected is ConnectResult.Connected)
        val connection = (connected as ConnectResult.Connected).connection
        try {
            val opened = runBlocking { connection.openSession() }
            assumeTrue("no session: $opened", opened is SessionOutcome.Opened)
            val session = (opened as SessionOutcome.Opened).session
            val root = session.grant.scope
            log("gone: ${session.grant.rights.sortedBy { it.ordinal }} over a root of ${root.childCount} entries")

            val heard = CopyOnWriteArrayList<VaultEvent>()
            val listening = CoroutineScope(Dispatchers.Default)
            listening.launch(start = CoroutineStart.UNDISPATCHED) { session.events.collect { heard += it } }
            val seconds = OnPhone.int("waitSeconds", WAIT_SECONDS)
            log("gone: waiting $seconds s for the root to be deleted")
            SystemClock.sleep(seconds * MILLIS_PER_SECOND)
            val events = heard.map { if (it is VaultEvent.FolderChanged) changed(it, root.id) else it.toString() }
            log("gone: heard $events, and the session is ${if (session.isOpen) "open" else "over"}")

            val stat = runBlocking { session.stat(root.id) }
            val list = runBlocking { session.list(root.id) }
            val grant = runBlocking { session.refreshGrant() }
            listening.cancel()
            val again = runBlocking { connection.openSession() }
            log(
                "gone: its root answers ${outcome(stat)} to a stat and ${outcome(list)} to a list, its grant " +
                    "${outcome(grant)}; a new session ${if (again is SessionOutcome.Opened) "opened" else "$again"}",
            )
            (again as? SessionOutcome.Opened)?.session?.close()
            session.close()
        } finally {
            connection.close()
        }
    }

    private companion object {
        const val WAIT_SECONDS = 60
        const val MILLIS_PER_SECOND = 1_000L

        fun changed(
            event: VaultEvent.FolderChanged,
            root: String,
        ): String = if (event.folderId == root) "FolderChanged(the root)" else "FolderChanged(another)"

        fun outcome(result: EncriResult<*>): String =
            when (result) {
                is EncriResult.Ok -> "ok"
                is EncriResult.Failed -> result.failure.name
            }
    }
}
