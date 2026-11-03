/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.vault

import android.net.Uri
import android.os.ParcelFileDescriptor
import com.systemhalted.encri.client.EncriDocument
import com.systemhalted.encri.client.EncriGrant
import com.systemhalted.encri.client.EncriResult
import com.systemhalted.encri.client.Failure
import com.systemhalted.encri.client.VaultEvent
import kotlinx.coroutines.flow.Flow

/**
 * The part of an encri session this app uses (AGENTS.md I3).
 *
 * Narrower than the client's session on purpose, so this interface is the list of what the gallery asks
 * encri for: it reads, hands a document to another app only through encri's handoff once the user has agreed
 * (I10), and deletes a photo or video the user chose, once encri has let it (I11).
 */
interface VaultSession : AutoCloseable {
    /** The folder the user let this app into, above which it cannot go. */
    val root: EncriDocument

    /** Locks, unlocks, folders that changed, and the session ending. */
    val events: Flow<VaultEvent>

    /** Every child of [folderId], in encri's order. */
    suspend fun list(folderId: String): EncriResult<List<EncriDocument>>

    /**
     * A preview of [documentId] that encri made and encoded, no larger than [width] by [height] (encri D112,
     * D121). Only for a document encri marks thumbnailable.
     */
    suspend fun thumbnail(
        documentId: String,
        width: Int,
        height: Int,
    ): EncriResult<ByteArray>

    /** A read-only descriptor over [documentId] that seeks, which the caller owns and closes. */
    suspend fun openRead(documentId: String): EncriResult<ParcelFileDescriptor>

    /**
     * `content://` URIs another app can read [documentIds] through, granted to this app to pass on (encri D117).
     * They name neither the document's id nor its name, and stop working when the vault locks or the session ends.
     */
    suspend fun handOff(documentIds: List<String>): EncriResult<List<Uri>>

    /** Deletes [documentId]. A folder would go with everything in it, which this app never asks. */
    suspend fun delete(documentId: String): EncriResult<Unit>

    /**
     * The grant as encri holds it now. One small read, which a locked vault answers [Failure.LOCKED] like any
     * other, so it is how the link asks whether a vault it believes locked still is.
     */
    suspend fun grant(): EncriResult<EncriGrant>
}
