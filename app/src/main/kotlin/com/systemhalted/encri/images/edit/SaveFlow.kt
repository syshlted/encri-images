/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.edit

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.annotation.StringRes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.systemhalted.encri.client.Encri
import com.systemhalted.encri.client.Right
import com.systemhalted.encri.images.R
import com.systemhalted.encri.images.models.Media
import com.systemhalted.encri.images.vault.Copies
import com.systemhalted.encri.images.vault.Rights
import com.systemhalted.encri.images.vault.Saved
import com.systemhalted.encri.images.vault.SecureWindows
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.OutputStream

/** A copy, made and waiting to be kept: what it is to be called, its type, and how it is written. */
class Made(
    val name: String,
    val mimeType: String,
    val writeTo: (OutputStream) -> Unit,
    val release: () -> Unit,
)

/**
 * The editor's *Save a copy* (I22): asks encri the first time for the rights to make a document and write it, with
 * every right the grant already holds; then makes the copy, saying how far along it is, and keeps it beside
 * [original]. The rights are asked for before the copy is made, so nobody waits for a video only to be asked.
 *
 * Every dialog is kept out of screenshots, and a toast names nothing (I3).
 */
@Suppress("LongParameterList") // DeleteFlow's, with what makes the copy.
class SaveFlow(
    private val activity: Activity,
    private val original: Media,
    private val copies: Copies,
    private val scope: CoroutineScope,
    /** Starts encri's consent screen for a result, the only way encri can tell which app asked (encri D120). */
    private val askEncri: (Intent) -> Unit,
    /** Makes the copy, telling how far along it is in percent where it knows. */
    private val make: suspend (onProgress: (Int) -> Unit) -> Made,
    /** Told of the copy once it is kept. */
    private val onSaved: (Uri) -> Unit,
) {
    private var asking = false
    private var saving: Job? = null

    /** The user pressed *Save a copy*. */
    fun offer() {
        if (saving?.isActive == true) return
        scope.launch {
            val held = copies.held()
            when {
                held == null -> say(R.string.edit_failed)
                held.containsAll(Copies.NEEDED) -> save()
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
            if (copies.awaitAllowed()) save() else say(R.string.edit_not_allowed)
        }
    }

    private fun save() {
        val progress = LinearProgressIndicator(activity).apply { isIndeterminate = true }
        val dialog =
            SecureWindows.secure(
                MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.edit_saving)
                    .setView(progress)
                    .setCancelable(false)
                    .setNegativeButton(android.R.string.cancel) { _, _ -> saving?.cancel() }
                    .create(),
            )
        dialog.show()
        saving =
            scope.launch {
                var made: Made? = null
                try {
                    made = make { percent -> progress.setProgressCompat(percent, true) }
                    keep(made)
                } catch (_: TooLargeToEdit) {
                    say(R.string.edit_too_large)
                } catch (_: IOException) {
                    say(R.string.edit_failed)
                } finally {
                    made?.release?.invoke()
                    dialog.dismiss()
                }
            }
    }

    private suspend fun keep(made: Made) {
        when (val saved = copies.save(original, made.name, made.mimeType, made.writeTo)) {
            is Saved.Kept -> {
                say(R.string.edit_saved)
                onSaved(saved.uri)
            }

            Saved.NotAllowed -> {
                askToAllow(copies.held().orEmpty())
            }

            Saved.Failed -> {
                say(R.string.edit_failed)
            }
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
