/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.vault

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.annotation.StringRes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.systemhalted.encri.client.Encri
import com.systemhalted.encri.client.EncriResult
import com.systemhalted.encri.images.R
import com.systemhalted.encri.images.models.Media
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** The ways a photo or video goes to another app (AGENTS.md I10), and what the screen says before each. */
enum class Handoff(
    @param:StringRes val title: Int,
    @param:StringRes val explain: Int,
    @param:StringRes val confirm: Int,
) {
    /** Through the share sheet, as `ACTION_SEND`. */
    SHARE(R.string.handoff_share_title, R.string.handoff_share_explain, R.string.file_action_share),

    /** To whichever app the user picks to look at it or edit it, as `ACTION_VIEW`. */
    OPEN_WITH(R.string.handoff_open_title, R.string.handoff_open_explain, R.string.file_action_open_with),
}

/**
 * Hands a photo or video to another app through a handoff encri makes for it (I10, encri D117), as encri-files
 * shares a document (encri-files F7).
 *
 * The other app is given a `content://` URI on encri's handoff provider, granted to this app and passed on with
 * read permission. It names neither the document's id nor its name, and it stops working when the vault locks or
 * this app's session ends. Nothing is handed over until the user has read what it costs, every time, because
 * plaintext given to another app is plaintext gone.
 */
class Handoffs(
    private val activity: Activity,
    private val link: VaultLink,
    private val scope: CoroutineScope,
) {
    /** Says what [way] costs, and hands [media] over only once the user agrees. */
    fun offer(
        media: Media,
        way: Handoff,
    ) {
        val dialog =
            MaterialAlertDialogBuilder(activity)
                .setTitle(way.title)
                .setMessage(way.explain)
                .setPositiveButton(way.confirm) { _, _ -> scope.launch { handOver(media, way) } }
                .setNegativeButton(android.R.string.cancel, null)
                .create()
        SecureWindows.secure(dialog).show()
    }

    /** Asks encri for a handoff of [media], and passes it to the app the user picks. */
    internal suspend fun handOver(
        media: Media,
        way: Handoff,
    ) {
        val documentId = VaultUris.documentIdOf(media.uri) ?: return
        val handed = link.ask { it.handOff(listOf(documentId)) }
        val uri = (handed as? EncriResult.Ok)?.value?.singleOrNull()
        if (uri == null) {
            say(R.string.handoff_failed)
        } else {
            choose(chooserFor(way, uri, media.mimeType))
        }
    }

    /**
     * A chooser over [uri], with the read grant in the target's flags and in its clip, which is what carries the
     * grant through the chooser to the app the user picks. The clip's label is empty, so the document's name goes
     * only where an app asks encri's handoff provider for it.
     *
     * **encri is left out**: its share screen refuses a URI on its own authorities (encri-files F7), so a photo
     * offered back to it is a choice certain to fail, and the vault already holds it. Its components are asked of the
     * package manager rather than named here, so this holds whatever encri calls them.
     */
    internal fun chooserFor(
        way: Handoff,
        uri: Uri,
        mimeType: String,
    ): Intent {
        val target =
            when (way) {
                Handoff.SHARE -> Intent(Intent.ACTION_SEND).setType(mimeType).putExtra(Intent.EXTRA_STREAM, uri)
                Handoff.OPEN_WITH -> Intent(Intent.ACTION_VIEW).setDataAndType(uri, mimeType)
            }.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        target.clipData = ClipData.newRawUri("", uri)
        // The share sheet has a title of its own; a chooser for anything else does not.
        val title = activity.getString(R.string.file_action_open_with).takeIf { way == Handoff.OPEN_WITH }
        val chooser = Intent.createChooser(target, title)
        val encri = encriTargetsOf(target)
        if (encri.isNotEmpty()) chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, encri.toTypedArray())
        return chooser
    }

    @Suppress("SwallowedException") // A phone with nothing to take it is an answer for the user, not a fault.
    private fun choose(chooser: Intent) {
        try {
            activity.startActivity(chooser)
        } catch (none: ActivityNotFoundException) {
            say(R.string.handoff_no_app)
        }
    }

    /** encri's own activities that would take [intent]. */
    @Suppress("DEPRECATION") // The flags overload is API 33; this one answers the same at every level.
    private fun encriTargetsOf(intent: Intent): List<ComponentName> =
        activity.packageManager
            .queryIntentActivities(intent, 0)
            .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
            .filter { it.packageName == Encri.PACKAGE }

    /** A toast, which the system draws outside this app's windows, so it never names a document. */
    private fun say(
        @StringRes text: Int,
    ) = Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()
}
