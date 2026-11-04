/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.testvault

import android.app.Activity
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.systemhalted.encri.client.Encri
import com.systemhalted.encri.client.EncriConnection
import com.systemhalted.encri.client.EncriGrant
import com.systemhalted.encri.client.EncriResult
import com.systemhalted.encri.client.EncriSession
import com.systemhalted.encri.client.Failure
import com.systemhalted.encri.client.Right
import com.systemhalted.encri.client.SessionOutcome
import com.systemhalted.encri.client.VaultEvent
import com.systemhalted.encri.images.testvault.EncriScreens.Companion.ALLOW_THE_WHOLE_VAULT
import com.systemhalted.encri.images.testvault.EncriScreens.Companion.DONT_ALLOW
import com.systemhalted.encri.images.testvault.TestVault.blocking
import com.systemhalted.encri.images.testvault.TestVault.log
import com.systemhalted.encri.images.testvault.TestVault.ok
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.Test
import org.junit.runner.RunWith
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random

/**
 * What encri does to an app's session and grant when the app asks for a grant again (I12). `PROTOCOL.md` says how
 * an app asks, and not what becomes of what it already holds.
 *
 * Each step asks encri's consent screen for some rights from a session that is open, answers the screen one way,
 * and logs what the screen answered, what the old session heard and answers afterwards, and whether a new session
 * opens, with what. It logs rather than asserts, because it is asking encri a question; I12 records the answers.
 * [Right.MOVE] is asked for only to be refused or backed out of, and the package is left with [TestVault.RIGHTS],
 * as it was found.
 *
 * **And what a descriptor the old session opened does once the grant is replaced**: before each step it writes a
 * document of its own under the root, opens it and reads a little, and after the answer reads the rest through the
 * same descriptor, logs how far it got and how it ended, and deletes it (`ROADMAP.md`'s video behind the consent
 * screen). It logs sizes and outcomes, never a name.
 */
@RunWith(AndroidJUnit4::class)
class GrantChanges {
    @Test
    fun observe() {
        TestVault.connected { connection ->
            val first = TestVault.openSession(connection)
            val root = first.grant.scope.id
            log("grant: before, ${describe(first.grant, root)}")
            var session: EncriSession? = first
            STEPS.forEach { step ->
                session = ask(connection, session ?: TestVault.openSession(connection), step, root)
            }
            session?.close()
            TestVault.openSession(connection).use {
                log("grant: after, ${describe(it.grant, root)}")
                Reading.removeWritten(it)
            }
        }
    }

    /** Asks encri's consent screen for [step]'s rights while [before] is open, and logs what became of it. */
    private fun ask(
        connection: EncriConnection,
        before: EncriSession,
        step: Step,
        root: String,
    ): EncriSession? {
        val heard = CopyOnWriteArrayList<VaultEvent>()
        val listening = CoroutineScope(Dispatchers.Default)
        // The events are not replayed, so the old session is listened to before encri is asked anything.
        listening.launch(start = CoroutineStart.UNDISPATCHED) { before.events.collect { heard += it } }
        val reading = Reading.start(before, root)

        ResultHost.start(TestVault.context, Encri.authorizationIntent(step.asked))
        val shown = runCatching { TestVault.screens.whichOf(ALLOW_THE_WHOLE_VAULT) }.isSuccess
        when {
            !shown -> Unit
            step.answer == null -> TestVault.screens.back()
            else -> TestVault.screens.press(step.answer)
        }
        val answer = requireNotNull(ResultHost.awaitAnswer(ANSWER_SECONDS)) { "encri's consent screen never answered" }
        // encri's events are oneway, so an end may arrive a moment after the screen answers.
        SystemClock.sleep(SETTLE_MILLIS)

        val stat = blocking { before.stat(root) }
        val grant = blocking { before.refreshGrant() }
        listening.cancel()
        val next = blocking { connection.openSession() }
        val read = reading.finish()
        log(
            "grant: ${step.name}: asked ${names(step.asked)}${if (shown) "" else " and was shown no screen"}, " +
                "answered ${answered(answer)}; the old session heard ${heard.map(::named)}, then answered " +
                "${outcome(stat)} to a stat and ${grant.map { describe(it, root) }} to its grant; a new session " +
                opened(next, root),
        )
        log("grant: ${step.name}: the descriptor the old session opened $read")
        before.close()
        return (next as? SessionOutcome.Opened)?.session
    }

    /**
     * A document of [SIZE] bytes this probe wrote under the root, opened by the old session with [READ_FIRST] of them
     * read before encri is asked. It is deleted at the end, with the rest of what this probe wrote.
     */
    private class Reading(
        private val descriptor: ParcelFileDescriptor,
    ) {
        private val input = FileInputStream(descriptor.fileDescriptor)

        init {
            // Not readNBytes, which Android has only from API 33.
            DataInputStream(input).readFully(ByteArray(READ_FIRST))
        }

        /** Reads what is left through the old session's descriptor, and says how much came and how. */
        fun finish(): String {
            val left = SIZE - READ_FIRST
            var got = 0
            val buffer = ByteArray(BYTES_PER_KIB * BYTES_PER_KIB / 16)
            val ended =
                try {
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        got += read
                    }
                    if (got == left) "read the rest to the end" else "came to an end early"
                } catch (failed: IOException) {
                    "failed with ${failed.javaClass.simpleName}"
                } finally {
                    runCatching { input.close() }
                    runCatching { descriptor.close() }
                }
            return "$ended: $got of the $left bytes left"
        }

        companion object {
            /** Writes a document under [root] with [session] and begins reading it through [session]'s descriptor. */
            fun start(
                session: EncriSession,
                root: String,
            ): Reading {
                val name = "grant probe ${System.currentTimeMillis()}.bin"
                val made = ok(blocking { session.createFile(root, name, "application/octet-stream") })
                // Written down before anything is in it and deleted at the end by id, as a seed is (I6): a session
                // narrowed by a step may not delete, and a run that fails is cleaned up by the next.
                leftBehind.appendText(made.id + "\n")
                ok(blocking { session.write(made.id) { it.write(Random.nextBytes(SIZE)) } })
                return Reading(ok(blocking { session.openRead(made.id) }))
            }

            /** What this probe wrote, in this run and in any that failed before it, removed by the ids written down. */
            fun removeWritten(session: EncriSession) {
                if (!leftBehind.exists()) return
                leftBehind.readLines().filter { it.isNotBlank() }.forEach { id ->
                    val outcome = blocking { session.delete(id) }
                    check(outcome is EncriResult.Ok || (outcome as EncriResult.Failed).failure == Failure.NOT_FOUND) {
                        "a document this probe wrote could not be removed: $outcome"
                    }
                }
                leftBehind.delete()
            }

            private val leftBehind get() = File(TestVault.context.filesDir, "grant-probe.txt")
        }
    }

    /** One way of asking, and of answering encri's screen: [answer] pressed, or backed out of when null. */
    private class Step(
        val name: String,
        val asked: Set<Right>,
        val answer: String?,
    )

    private companion object {
        val MORE = TestVault.RIGHTS + Right.MOVE

        val STEPS =
            listOf(
                Step("narrowed", setOf(Right.READ, Right.WRITE, Right.CREATE), ALLOW_THE_WHOLE_VAULT),
                Step("widened", TestVault.RIGHTS, ALLOW_THE_WHOLE_VAULT),
                Step("the same again", TestVault.RIGHTS, ALLOW_THE_WHOLE_VAULT),
                Step("more, refused", MORE, DONT_ALLOW),
                Step("more, backed out of", MORE, null),
            )

        const val ANSWER_SECONDS = 60L
        const val SETTLE_MILLIS = 3_000L
        const val BYTES_PER_KIB = 1_024
        const val SIZE = 256 * BYTES_PER_KIB
        const val READ_FIRST = 16 * BYTES_PER_KIB

        fun names(rights: Set<Right>): String = rights.sortedBy { it.ordinal }.joinToString("+") { it.name }

        fun describe(
            grant: EncriGrant,
            root: String,
        ): String = "${names(grant.rights)} over ${if (grant.scope.id == root) "the whole vault" else "another root"}"

        fun answered(answer: ResultHost.Answer): String {
            val code = if (answer.resultCode == Activity.RESULT_OK) "OK" else "code ${answer.resultCode}"
            val rights = Encri.authorizationResult(answer.resultCode, answer.data)
            return "$code with ${rights?.let(::names) ?: "no rights"}"
        }

        fun named(event: VaultEvent): String =
            when (event) {
                is VaultEvent.FolderChanged -> "FolderChanged"
                else -> event.toString()
            }

        fun outcome(result: EncriResult<*>): String =
            when (result) {
                is EncriResult.Ok -> "ok"
                is EncriResult.Failed -> result.failure.name
            }

        fun <T> EncriResult<T>.map(describe: (T) -> String): String =
            when (this) {
                is EncriResult.Ok -> describe(value)
                is EncriResult.Failed -> failure.name
            }

        fun opened(
            outcome: SessionOutcome,
            root: String,
        ): String =
            when (outcome) {
                is SessionOutcome.Opened -> "opened with ${describe(outcome.session.grant, root)}"
                is SessionOutcome.Failed -> "failed, ${outcome.failure.name}"
                else -> "answered $outcome"
            }
    }
}
