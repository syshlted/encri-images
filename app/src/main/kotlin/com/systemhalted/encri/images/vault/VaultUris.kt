/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.vault

import android.net.Uri

/**
 * The URIs a document and a folder are known by inside this app (AGENTS.md I4).
 *
 * The screens inherited from Glimpse identify everything by a [Uri]. These name a document to Glide and to
 * ExoPlayer and nothing else: no provider answers them, no other app could open one, and they carry encri's
 * document id, which says nothing about the document (encri D15).
 */
object VaultUris {
    private const val SCHEME = "encri-vault"
    private const val DOCUMENT = "document"
    private const val FOLDER = "folder"

    fun document(documentId: String): Uri = uri(DOCUMENT, documentId)

    fun folder(folderId: String): Uri = uri(FOLDER, folderId)

    /** The document [uri] names, or null when it names anything else. */
    fun documentIdOf(uri: Uri): String? =
        uri
            .takeIf { it.scheme == SCHEME && it.authority == DOCUMENT }
            ?.lastPathSegment

    private fun uri(
        kind: String,
        id: String,
    ): Uri =
        Uri
            .Builder()
            .scheme(SCHEME)
            .authority(kind)
            .appendPath(id)
            .build()
}
