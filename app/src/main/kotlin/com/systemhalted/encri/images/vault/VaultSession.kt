/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.vault

import android.os.ParcelFileDescriptor
import com.systemhalted.encri.client.EncriDocument
import com.systemhalted.encri.client.EncriResult
import com.systemhalted.encri.client.VaultEvent
import kotlinx.coroutines.flow.Flow

/**
 * The part of an encri session this app uses (AGENTS.md I3).
 *
 * Narrower than the client's session on purpose, so this interface is the list of what the gallery asks
 * encri for: it reads, and nothing here changes the vault or hands a document to another app.
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
}
