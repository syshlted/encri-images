/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.vault

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.annotation.StringRes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.systemhalted.encri.client.Encri
import com.systemhalted.encri.client.EncriGrant
import com.systemhalted.encri.client.EncriResult
import com.systemhalted.encri.client.Failure
import com.systemhalted.encri.client.Right
import com.systemhalted.encri.images.R
import com.systemhalted.encri.images.models.Media
import com.systemhalted.encri.images.models.MediaType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** What became of a delete. */
enum class Deleted {
    /** It is gone from the vault, or was already. */
    GONE,

    /** encri's grant does not let this app delete. */
    NOT_ALLOWED,

    /** encri would not, or could not be asked. */
    FAILED,
}

/**
 * Deleting from the vault (AGENTS.md I11): a photo or video, never a folder, which would go with everything in it.
 *
 * The app asks encri for [Right.READ] alone until the user first deletes, and then for [Right.DELETE] as well, so
 * whether it may is asked of encri each time rather than remembered.
 */
class Deletes(
    private val link: VaultLink,
    private val library: VaultLibrary,
) {
    /** Whether encri's grant lets this app delete, as encri holds it now; null when encri did not say. */
    suspend fun allowed(): Boolean? = allowedBy(link.ask { it.grant() })

    /** Deletes [media]; once it is gone, it leaves the library at once. */
    suspend fun delete(media: Media): Deleted {
        val documentId = VaultUris.documentIdOf(media.uri) ?: return Deleted.FAILED
        val failure = (link.ask { it.delete(documentId) } as? EncriResult.Failed)?.failure
        return when (failure) {
            // Not found is a delete that got there first: either way, it is not in the vault.
            null, Failure.NOT_FOUND -> Deleted.GONE.also { library.deleted(media.uri) }

            Failure.NOT_AUTHORIZED -> Deleted.NOT_ALLOWED

            else -> Deleted.FAILED
        }
    }

    /**
     * Whether encri lets this app delete, once its consent screen said it would. encri ends the session whose grant
     * it replaces, and the link asks for the next one at once (encri-files F5), so this waits up to [ALLOWING_MILLIS]
     * for a session whose grant says so, rather than asking the one encri is ending.
     */
    suspend fun awaitAllowed(): Boolean =
        withTimeoutOrNull(ALLOWING_MILLIS) {
            var asked: VaultSession? = null
            var allowed = false
            while (!allowed) {
                val open = link.state.first { it is LinkState.Open && !it.locked && it.session !== asked }
                asked = (open as LinkState.Open).session
                allowed = allowedBy(link.ask(asked) { it.grant() }) == true
            }
            allowed
        } ?: false

    private fun allowedBy(grant: EncriResult<EncriGrant>): Boolean? =
        (grant as? EncriResult.Ok)?.value?.rights?.contains(Right.DELETE)

    internal companion object {
        /** encri answers its consent screen in well under a second; this is for a phone that is slow to. */
        const val ALLOWING_MILLIS = 5_000L
    }
}

/**
 * The viewer's *Delete* (I11): asks encri for the right the first time, says that a delete cannot be undone, and
 * only then deletes. Every dialog is kept out of screenshots, and a toast names nothing (I3).
 */
class DeleteFlow(
    private val activity: Activity,
    private val deletes: Deletes,
    private val scope: CoroutineScope,
    /** Starts encri's consent screen for a result, the only way encri can tell which app asked (encri D120). */
    private val askEncri: (Intent) -> Unit,
    /** Told of a photo or video once it is gone. */
    private val onDeleted: (Media) -> Unit,
) {
    /** What waits on encri's consent screen. */
    private var waiting: Media? = null

    /** The user pressed *Delete* on [media]. */
    fun offer(media: Media) {
        scope.launch {
            when (deletes.allowed()) {
                true -> confirm(media)
                false -> askToAllow(media)
                null -> say(R.string.delete_failed)
            }
        }
    }

    /** encri's consent screen answered [resultCode] with [data]. */
    fun answered(
        resultCode: Int,
        data: Intent?,
    ) {
        val media = waiting ?: return
        waiting = null
        if (Right.DELETE !in Encri.authorizationResult(resultCode, data).orEmpty()) return
        scope.launch {
            if (deletes.awaitAllowed()) confirm(media) else say(R.string.delete_not_allowed)
        }
    }

    private fun confirm(media: Media) {
        val title = if (media.mediaType == MediaType.VIDEO) R.string.delete_video_title else R.string.delete_photo_title
        show(
            MaterialAlertDialogBuilder(activity)
                .setTitle(title)
                .setMessage(R.string.delete_explain)
                .setPositiveButton(R.string.file_action_delete) { _, _ -> scope.launch { delete(media) } },
        )
    }

    private suspend fun delete(media: Media) {
        when (deletes.delete(media)) {
            Deleted.GONE -> onDeleted(media)
            Deleted.NOT_ALLOWED -> askToAllow(media)
            Deleted.FAILED -> say(R.string.delete_failed)
        }
    }

    private fun askToAllow(media: Media) =
        show(
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.delete_allow_title)
                .setMessage(R.string.delete_allow_explain)
                .setPositiveButton(R.string.vault_gate_allow) { _, _ ->
                    waiting = media
                    askEncri(Encri.authorizationIntent(setOf(Right.READ, Right.DELETE)))
                },
        )

    private fun show(builder: MaterialAlertDialogBuilder) =
        SecureWindows.secure(builder.setNegativeButton(android.R.string.cancel, null).create()).show()

    private fun say(
        @StringRes text: Int,
    ) = Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()
}
