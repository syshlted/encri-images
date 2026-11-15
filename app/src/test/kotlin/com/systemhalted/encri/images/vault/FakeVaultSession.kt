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
import com.systemhalted.encri.client.Right
import com.systemhalted.encri.client.VaultEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream

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

    /** Every descriptor [openRead] gave, which a test may close to stand for encri ending what it served. */
    val descriptors = mutableListOf<ParcelFileDescriptor>()

    /** Ended, as encri ends a session whose grant it replaced: [openRead] answers [Failure.ENDED]. */
    var over = false
    var thumbnail: EncriResult<ByteArray> = EncriResult.Ok(byteArrayOf(1, 2, 3))
    var grant: EncriResult<EncriGrant> = EncriResult.Ok(EncriGrant(setOf(Right.READ), root))
    var grantsAsked = 0
    val handedOff = mutableListOf<List<String>>()

    /** What a handoff answers; null hands one made-up URI per document. */
    var handOff: EncriResult<List<Uri>>? = null
    val deleted = mutableListOf<String>()
    var delete: EncriResult<Unit> = EncriResult.Ok(Unit)

    /** What a delete of one document in particular answers, ahead of [delete]. */
    val deleteFailures = mutableMapOf<String, Failure>()
    var closed = false

    /** Every document asked for: its folder, its name and its type. */
    val created = mutableListOf<Triple<String, String, String>>()

    /** What a create answers; null makes a document, `made-1`, `made-2`, …, with the name asked. */
    var create: EncriResult<EncriDocument>? = null

    /** What each write wrote, by document. */
    val written = mutableMapOf<String, ByteArray>()

    /** What a write answers instead of writing, when not null. */
    var writeFailure: Failure? = null

    /** What a write waits for before it writes, when not null: a write a test can stop part way. */
    var writing: CompletableDeferred<Unit>? = null

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
        val file = content[documentId]
        return when {
            over -> {
                EncriResult.Failed(Failure.ENDED, "the session is over")
            }

            file == null -> {
                EncriResult.Failed(Failure.NOT_FOUND, "no such document")
            }

            else -> {
                EncriResult.Ok(
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).also(descriptors::add),
                )
            }
        }
    }

    override suspend fun handOff(documentIds: List<String>): EncriResult<List<Uri>> {
        handedOff += documentIds
        return handOff ?: EncriResult.Ok(documentIds.map { Uri.parse("content://handoff.test/$it") })
    }

    override suspend fun delete(documentId: String): EncriResult<Unit> {
        deleted += documentId
        return deleteFailures[documentId]?.let { EncriResult.Failed(it, "told to fail") } ?: delete
    }

    override suspend fun create(
        folderId: String,
        name: String,
        mimeType: String,
    ): EncriResult<EncriDocument> {
        created += Triple(folderId, name, mimeType)
        return create
            ?: EncriResult.Ok(
                EncriDocument("made-${created.size}", folderId, name, mimeType, false, 0, 0, 0, null, true),
            )
    }

    /** As the client writes: a writer that fails saves nothing (encri-api L11). */
    override suspend fun write(
        documentId: String,
        writer: suspend (OutputStream) -> Unit,
    ): EncriResult<EncriDocument> {
        val failure = writeFailure
        if (failure != null) return EncriResult.Failed(failure, "told to fail")
        writing?.await()
        val out = ByteArrayOutputStream()
        val stopped =
            try {
                writer(out)
                null
            } catch (stopped: IOException) {
                stopped
            }
        if (stopped == null) written[documentId] = out.toByteArray()
        return stopped?.let { EncriResult.Failed(Failure.CANCELLED, it.message ?: "the writer stopped") }
            ?: EncriResult.Ok(document(documentId, size = out.size().toLong()))
    }

    override suspend fun grant(): EncriResult<EncriGrant> {
        grantsAsked++
        return grant
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
