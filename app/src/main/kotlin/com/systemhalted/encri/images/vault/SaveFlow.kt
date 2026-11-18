/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.vault

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.annotation.StringRes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.systemhalted.encri.client.Encri
import com.systemhalted.encri.client.Right
import com.systemhalted.encri.images.R
import com.systemhalted.encri.images.edit.EditStore
import com.systemhalted.encri.images.edit.Made
import com.systemhalted.encri.images.edit.Saving
import com.systemhalted.encri.images.models.Media
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The editor's *Save a copy*, in the vault (I22): asks encri the first time for the rights to make a document and write
 * it, with every right the grant already holds, then makes the copy and keeps it through [store] ([Saving]). The
 * rights are asked for before the copy is made, so nobody waits for a video only to be asked.
 *
 * Every dialog is kept out of screenshots, and a toast names nothing (I3).
 */
@Suppress("LongParameterList") // DeleteFlow's, with the store and what makes the copy.
class SaveFlow(
    private val activity: Activity,
    original: Media,
    private val copies: Copies,
    store: EditStore,
    private val scope: CoroutineScope,
    /** Starts encri's consent screen for a result, the only way encri can tell which app asked (encri D120). */
    private val askEncri: (Intent) -> Unit,
    /** Makes the copy, telling how far along it is in percent where it knows. */
    make: suspend (onProgress: (Int) -> Unit) -> Made,
    /** Told of the copy once it is kept. */
    onSaved: (Uri) -> Unit,
) {
    private var asking = false
    private val saving =
        Saving(activity, original, store, scope, make, onSaved, onNotAllowed = {
            // The grant was narrowed between asking and keeping.
            askToAllow(copies.held().orEmpty())
        }, secure = SecureWindows::secure)

    /** The user pressed *Save a copy*. */
    fun offer() {
        if (saving.active) return
        scope.launch {
            val held = copies.held()
            when {
                held == null -> say(R.string.edit_failed)
                held.containsAll(Copies.NEEDED) -> saving.start()
                else -> askToAllow(held)
            }
        }
    }

    /** encri's consent screen answered [resultCode] with [data]. */
    fun answered(
        resultCode: Int,
        data: Intent?,
    ) {
        if (!asking) return
        asking = false
        if (!Encri.authorizationResult(resultCode, data).orEmpty().containsAll(Copies.NEEDED)) return
        scope.launch {
            if (copies.awaitAllowed()) saving.start() else say(R.string.edit_not_allowed)
        }
    }

    /** Offers encri's consent screen, for making and writing documents and every right [held] already. */
    private fun askToAllow(held: Set<Right>) =
        SecureWindows
            .secure(
                MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.edit_allow_title)
                    .setMessage(R.string.edit_allow_explain)
                    .setPositiveButton(R.string.vault_gate_allow) { _, _ ->
                        asking = true
                        askEncri(Encri.authorizationIntent(Rights.toAsk(held, Copies.NEEDED)))
                    }.setNegativeButton(android.R.string.cancel, null)
                    .create(),
            ).show()

    /** A toast, which the system draws outside this app's windows, so it never names a document. */
    private fun say(
        @StringRes text: Int,
    ) = Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()
}
