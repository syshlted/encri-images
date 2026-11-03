/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.testvault

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.systemhalted.encri.client.Availability
import com.systemhalted.encri.client.ConnectResult
import com.systemhalted.encri.client.Encri
import com.systemhalted.encri.client.EncriConnection
import com.systemhalted.encri.client.EncriResult
import com.systemhalted.encri.client.EncriSession
import com.systemhalted.encri.client.Right
import com.systemhalted.encri.client.SessionOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import java.io.File

/**
 * A session with the encri on this phone for the device tests, holding the rights they write with (I6).
 *
 * Asks the way any app asks: encri's consent screen, pressed by [EncriScreens], and its unlock screen when the
 * vault is locked, which needs the vault's passphrase as the instrumentation argument `encriPassphrase`. A phone
 * without encri, or a locked vault and no passphrase, is an assumption that fails, saying which.
 */
internal object TestVault {
    /** Everything the seed and its removal do: write a library, date it, and delete it. Never [Right.MOVE]. */
    val RIGHTS = setOf(Right.READ, Right.WRITE, Right.CREATE, Right.DELETE)

    const val TAG = "EncriImagesDevice"

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context: Context get() = instrumentation.targetContext
    val arguments get() = InstrumentationRegistry.getArguments()
    private val passphrase: String? get() = arguments.getString("encriPassphrase")
    val screens = EncriScreens(instrumentation)

    /** The top folders this package has seeded and not yet removed, one id a line, in its own files. */
    val seeded: File get() = File(context.filesDir, "seeded-folders.txt")

    /** A connection and a session with [RIGHTS], which [use] closes. */
    fun <T> use(use: (EncriSession) -> T): T = connected { use(openSession(it)) }

    /** A connection to encri, which [use] closes. */
    fun <T> connected(use: (EncriConnection) -> T): T {
        assumeTrue("encri is not installed here", Encri.availability(context) != Availability.NotInstalled)
        val connection =
            when (val connected = blocking { Encri.connect(context) }) {
                is ConnectResult.Connected -> connected.connection
                else -> throw AssertionError("not connected to encri: $connected")
            }
        return try {
            use(connection)
        } finally {
            connection.close()
        }
    }

    fun log(line: String) {
        Log.i(TAG, line)
    }

    fun <T> ok(result: EncriResult<T>): T =
        when (result) {
            is EncriResult.Ok -> result.value
            is EncriResult.Failed -> throw AssertionError("failed: $result")
        }

    fun <T> blocking(
        millis: Long = TIMEOUT_MILLIS,
        block: suspend CoroutineScope.() -> T,
    ): T = runBlocking { withTimeout(millis, block) }

    /** A session over [connection] with [RIGHTS], asking encri's screens for them if it has not. */
    fun openSession(connection: EncriConnection): EncriSession {
        repeat(ATTEMPTS) {
            when (val outcome = blocking { connection.openSession() }) {
                is SessionOutcome.Opened -> {
                    if (outcome.session.grant.rights
                            .containsAll(RIGHTS)
                    ) {
                        return outcome.session
                    }
                    outcome.session.close()
                    authorize()
                }

                SessionOutcome.Locked -> {
                    unlock()
                }

                SessionOutcome.NotAuthorized, SessionOutcome.AccessOff -> {
                    authorize()
                }

                is SessionOutcome.Failed -> {
                    throw AssertionError("encri would not open a session: $outcome")
                }
            }
        }
        throw AssertionError("no session after $ATTEMPTS tries")
    }

    private fun unlock() {
        val typed = needPassphrase()
        ResultHost.start(context, Encri.unlockIntent())
        screens.unlockWith(typed)
        val answer = requireNotNull(ResultHost.awaitAnswer(ANSWER_SECONDS)) { "encri's unlock screen never answered" }
        check(answer.resultCode == Activity.RESULT_OK) { "the vault did not unlock" }
    }

    private fun authorize() {
        ResultHost.start(context, Encri.authorizationIntent(RIGHTS))
        while (true) {
            when (
                screens.whichOf(
                    EncriScreens.UNLOCK_THE_VAULT,
                    EncriScreens.TURN_ACCESS_ON,
                    EncriScreens.ALLOW_THE_WHOLE_VAULT,
                )
            ) {
                EncriScreens.UNLOCK_THE_VAULT -> {
                    val typed = needPassphrase()
                    screens.press(EncriScreens.UNLOCK_THE_VAULT)
                    screens.unlockWith(typed)
                }

                EncriScreens.TURN_ACCESS_ON -> {
                    screens.press(EncriScreens.TURN_ACCESS_ON)
                }

                else -> {
                    screens.press(EncriScreens.ALLOW_THE_WHOLE_VAULT)
                    break
                }
            }
        }
        val answer = requireNotNull(ResultHost.awaitAnswer(ANSWER_SECONDS)) { "encri's consent screen never answered" }
        val allowed = Encri.authorizationResult(answer.resultCode, answer.data).orEmpty()
        check(allowed.containsAll(RIGHTS)) { "encri allowed $allowed, not $RIGHTS" }
    }

    private fun needPassphrase(): String {
        assumeTrue("encri's vault needs unlocking, and no encriPassphrase was given", passphrase != null)
        return requireNotNull(passphrase)
    }

    private const val ATTEMPTS = 4
    private const val ANSWER_SECONDS = 60L
    private const val TIMEOUT_MILLIS = 60_000L
}
