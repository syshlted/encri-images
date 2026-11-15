/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.vault

import android.net.Uri
import com.systemhalted.encri.client.EncriDocument
import com.systemhalted.encri.client.EncriResult
import com.systemhalted.encri.client.Failure
import com.systemhalted.encri.client.Right
import com.systemhalted.encri.images.models.Media
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.OutputStream

/** What became of a copy. */
sealed interface Saved {
    /** It is in the vault, beside the original, as [uri]. */
    data class Kept(
        val uri: Uri,
    ) : Saved

    /** encri's grant does not let this app make a document, or write one. */
    data object NotAllowed : Saved

    /** encri would not, or could not be asked. */
    data object Failed : Saved
}

/**
 * A copy of a photo or video, kept in the vault beside the original (AGENTS.md I22). The original is never changed.
 *
 * Making a document is [Right.CREATE] and filling it is [Right.WRITE], which the app asks encri for the first time a
 * copy is saved, with every right the grant already holds ([Rights.toAsk]).
 */
class Copies(
    private val link: VaultLink,
    private val library: VaultLibrary,
) {
    private val rights = Rights(link)

    /** The rights encri's grant holds now; null when encri did not say. */
    suspend fun held(): Set<Right>? = rights.held()

    /** Whether encri lets this app save copies, once its consent screen said it would (`Rights.awaitHeld`). */
    suspend fun awaitAllowed(): Boolean = rights.awaitHeld(NEEDED)

    /**
     * Keeps what [write] writes as a new document named [name], in the folder that holds [original]. What is written
     * is made before encri is asked, so a copy that cannot be made leaves nothing behind (I22).
     *
     * A document encri made and this app could not fill, or was stopped filling, is deleted again, where the grant lets
     * this app delete. Where it does not, it stays in the vault, empty, and the walk shows it as a photo or video that
     * will not open.
     */
    suspend fun save(
        original: Media,
        name: String,
        mimeType: String,
        write: (OutputStream) -> Unit,
    ): Saved {
        val folderId = VaultUris.folderIdOf(original.albumUri)
        val created =
            if (folderId == null) {
                EncriResult.Failed(Failure.INVALID, "not in a folder of the vault")
            } else {
                link.ask { it.create(folderId, name, mimeType) }
            }
        return when (created) {
            is EncriResult.Ok -> filled(created.value, write)
            is EncriResult.Failed -> savedOf(created.failure)
        }
    }

    /** [made], filled by [write]; or, where it could not be, deleted again. */
    private suspend fun filled(
        made: EncriDocument,
        write: (OutputStream) -> Unit,
    ): Saved {
        val written =
            try {
                link.ask { it.write(made.id) { stream -> write(stream) } }
            } catch (stopped: CancellationException) {
                withContext(NonCancellable) { link.ask { it.delete(made.id) } }
                throw stopped
            }
        return when (written) {
            is EncriResult.Ok -> {
                library.reload()
                Saved.Kept(VaultUris.document(made.id))
            }

            is EncriResult.Failed -> {
                link.ask { it.delete(made.id) }
                savedOf(written.failure)
            }
        }
    }

    private fun savedOf(failure: Failure): Saved =
        if (failure == Failure.NOT_AUTHORIZED) Saved.NotAllowed else Saved.Failed

    companion object {
        /** What saving a copy needs of encri's grant: making a document, and writing it. */
        val NEEDED: Set<Right> = setOf(Right.CREATE, Right.WRITE)

        private const val EDITED = " (edited)"

        /** `IMG_1 (edited) (1)`: a copy's own mark, and the number encri gave a name that was taken. */
        private val MARKED = Regex("""${Regex.escape(EDITED)}( \(\d+\))?$""")

        /**
         * The name a copy of [original] is asked for: the original's, marked as edited, with [extension]. A copy of a
         * copy is not marked twice; encri numbers a name already taken (`IMG_1 (edited) (1).jpg`).
         */
        fun nameOf(
            original: String,
            extension: String,
        ): String {
            val dot = original.lastIndexOf('.').takeIf { it > 0 } ?: original.length
            val base = original.substring(0, dot).replace(MARKED, "").ifBlank { "copy" }
            return "$base$EDITED.$extension"
        }
    }
}
