/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.vault

import android.content.Context
import android.os.ParcelFileDescriptor
import com.systemhalted.encri.client.ConnectResult
import com.systemhalted.encri.client.Encri
import com.systemhalted.encri.client.EncriConnection
import com.systemhalted.encri.client.EncriDocument
import com.systemhalted.encri.client.EncriResult
import com.systemhalted.encri.client.EncriSession
import com.systemhalted.encri.client.SessionOutcome
import com.systemhalted.encri.client.VaultEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The [Opener] that asks the encri installed on this phone, through encri-client.
 *
 * One connection for the life of the process, opened on first use and opened again when encri's process
 * has gone. Trust is the client's default: encri's release key, or this app's own, which is how a debug build
 * finds a debug encri made on the same machine.
 */
class ClientOpener(
    private val context: Context,
) : Opener {
    private val guard = Mutex()
    private var connection: EncriConnection? = null

    /** Why the last [connect] gave no connection. */
    private var refusal: Opening = Opening.Unavailable

    override suspend fun open(): Opening =
        guard.withLock {
            val held = connection?.takeIf { it.isConnected } ?: connect() ?: return@withLock refusal
            when (val outcome = held.openSession()) {
                is SessionOutcome.Opened -> Opening.Open(ClientSession(outcome.session))
                else -> Opening.refusalOf(outcome) ?: Opening.Unavailable
            }
        }

    private suspend fun connect(): EncriConnection? =
        when (val result = Encri.connect(context.applicationContext)) {
            is ConnectResult.Connected -> {
                result.connection.also { connection = it }
            }

            else -> {
                refusal = Opening.refusalOf(result) ?: Opening.Unavailable
                null
            }
        }
}

/** A real session, as this app uses it. */
private class ClientSession(
    private val session: EncriSession,
) : VaultSession {
    override val root: EncriDocument get() = session.grant.scope

    override val events: Flow<VaultEvent> get() = session.events

    override suspend fun list(folderId: String): EncriResult<List<EncriDocument>> = session.list(folderId)

    override suspend fun thumbnail(
        documentId: String,
        width: Int,
        height: Int,
    ): EncriResult<ByteArray> = session.thumbnail(documentId, width, height)

    override suspend fun openRead(documentId: String): EncriResult<ParcelFileDescriptor> = session.openRead(documentId)

    override fun close() = session.close()
}
