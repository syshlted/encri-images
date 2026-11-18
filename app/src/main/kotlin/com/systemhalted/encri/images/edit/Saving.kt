/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.edit

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.systemhalted.encri.images.R
import com.systemhalted.encri.images.models.Media
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * A copy made and kept (I22, I23): a dialog says how far along it is and offers *Cancel*; then [store] keeps it beside
 * [original], and a toast says so, naming nothing (I3). The same for every store; leave to keep copies, where a store
 * needs it, is asked before this starts (`SaveFlow`, the vault's).
 */
@Suppress("LongParameterList") // What it makes, where it keeps it, and what it tells.
class Saving(
    private val activity: Activity,
    private val original: Media,
    private val store: EditStore,
    private val scope: CoroutineScope,
    /** Makes the copy, telling how far along it is in percent where it knows. */
    private val make: suspend (onProgress: (Int) -> Unit) -> Made,
    /** Told of the copy once it is kept. */
    private val onSaved: (Uri) -> Unit,
    /** Told when the store would not let the copy be kept, which may ask leave to keep it. */
    private val onNotAllowed: suspend () -> Unit,
    /** What the dialog goes through before it shows: the vault keeps it out of screenshots (I3). */
    private val secure: (AlertDialog) -> AlertDialog = { it },
) {
    private var job: Job? = null

    /** Whether a copy is being made or kept now. */
    val active: Boolean get() = job?.isActive == true

    /** Makes the copy and keeps it. */
    fun start() {
        val progress = LinearProgressIndicator(activity).apply { isIndeterminate = true }
        val dialog =
            secure(
                MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.edit_saving)
                    .setView(progress)
                    .setCancelable(false)
                    .setNegativeButton(android.R.string.cancel) { _, _ -> job?.cancel() }
                    .create(),
            )
        dialog.show()
        job =
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
        when (val saved = store.keep(original, made)) {
            is Saved.Kept -> {
                say(R.string.edit_saved)
                onSaved(saved.uri)
            }

            Saved.NotAllowed -> {
                onNotAllowed()
            }

            Saved.Failed -> {
                say(R.string.edit_failed)
            }
        }
    }

    /** A toast, which the system draws outside this app's windows, so it never names a document. */
    private fun say(
        @StringRes text: Int,
    ) = Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()
}
