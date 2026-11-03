/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.vault

import android.os.ParcelFileDescriptor
import com.systemhalted.encri.client.EncriDocument
import com.systemhalted.encri.client.EncriResult
import com.systemhalted.encri.client.Failure
import com.systemhalted.encri.client.VaultEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import java.io.File

/** A stand-in for encri: a tree of documents, and a record of what was asked of it. */
class FakeVaultSession(
    override val root: EncriDocument = folder("root", "vault", parentId = null),
    private val tree: Map<String, List<EncriDocument>> = emptyMap(),
) : VaultSession {
    override val events = MutableSharedFlow<VaultEvent>(extraBufferCapacity = 16)
    val listed = mutableListOf<String>()
    val listFailures = mutableMapOf<String, Failure>()
    val thumbnailsAsked = mutableListOf<Triple<String, Int, Int>>()
    val opened = mutableListOf<String>()
    val content = mutableMapOf<String, File>()
    var thumbnail: EncriResult<ByteArray> = EncriResult.Ok(byteArrayOf(1, 2, 3))
    var closed = false

    override suspend fun list(folderId: String): EncriResult<List<EncriDocument>> {
        listed += folderId
        listFailures[folderId]?.let { return EncriResult.Failed(it, "told to fail") }
        return EncriResult.Ok(tree[folderId].orEmpty())
    }

    override suspend fun thumbnail(
        documentId: String,
        width: Int,
        height: Int,
    ): EncriResult<ByteArray> {
        thumbnailsAsked += Triple(documentId, width, height)
        return thumbnail
    }

    override suspend fun openRead(documentId: String): EncriResult<ParcelFileDescriptor> {
        opened += documentId
        val file = content[documentId] ?: return EncriResult.Failed(Failure.NOT_FOUND, "no such document")
        return EncriResult.Ok(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))
    }

    override fun close() {
        closed = true
    }
}

fun folder(
    id: String,
    name: String = id,
    parentId: String? = "root",
): EncriDocument = EncriDocument(id, parentId, name, "vnd.android.document/directory", true, 0, 0, 0, 0, false)

fun document(
    id: String,
    mimeType: String = "image/jpeg",
    modified: Long = 0,
    parentId: String = "root",
    size: Long = 10,
): EncriDocument = EncriDocument(id, parentId, "$id.name", mimeType, false, size, modified, modified, null, true)
