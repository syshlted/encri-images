/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.vault

import android.net.Uri
import com.systemhalted.encri.client.EncriDocument
import com.systemhalted.encri.client.EncriResult
import com.systemhalted.encri.client.Failure
import com.systemhalted.encri.client.valueOrNull
import com.systemhalted.encri.images.datasources.MediaError
import com.systemhalted.encri.images.models.Album
import com.systemhalted.encri.images.models.Media
import com.systemhalted.encri.images.models.MediaType
import com.systemhalted.encri.images.models.RequestStatus
import com.systemhalted.encri.images.models.Thumbnail
import com.systemhalted.encri.images.utils.MimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import java.util.Date

/** Every photo and video the grant reaches, newest first, and the folders that hold them. */
data class Library(
    val medias: List<Media>,
    val albums: List<Album>,
) {
    /** The photos and videos directly in [albumUri], newest first. */
    fun mediasIn(albumUri: Uri): List<Media> = medias.filter { it.albumUri == albumUri }
}

/**
 * Lists the vault under [session]'s root, a folder at a time (AGENTS.md I4).
 *
 * encri has no query across folders, so this is the only way to know what the vault holds, and it costs one
 * request per page of every folder. A folder that went away while it was being walked is one a delete got to
 * first, which is not a failure.
 */
suspend fun walkLibrary(session: VaultSession): EncriResult<Library> {
    val medias = mutableListOf<Media>()
    val folders = ArrayDeque(listOf(session.root))
    while (folders.isNotEmpty()) {
        val folder = folders.removeFirst()
        val listed = session.list(folder.id)
        if (listed is EncriResult.Failed && listed.failure != Failure.NOT_FOUND) return listed
        val albumUri = VaultUris.folder(folder.id)
        // The root has no name worth showing: it is the vault itself, or a folder the user already chose.
        val albumName = folder.name.takeUnless { folder.id == session.root.id }
        listed.valueOrNull().orEmpty().forEach { child ->
            if (child.isFolder) folders.addLast(child) else mediaOf(child, albumUri, albumName)?.let(medias::add)
        }
    }
    val newest = medias.sortedWith(compareByDescending<Media> { it.dateModified }.thenBy { it.uri })
    return EncriResult.Ok(Library(newest, albumsOf(newest)))
}

private fun mediaOf(
    document: EncriDocument,
    albumUri: Uri,
    albumName: String?,
): Media? {
    val type =
        MimeUtils.mimeTypeToMediaType(document.mimeType)?.takeIf { it == MediaType.IMAGE || it == MediaType.VIDEO }
            ?: return null
    return Media(
        uri = VaultUris.document(document.id),
        mediaType = type,
        mimeType = document.mimeType,
        albumUri = albumUri,
        albumName = albumName,
        displayName = document.name,
        dateAdded = Date(document.createdMillis),
        dateModified = Date(document.modifiedMillis),
        sizeBytes = document.sizeBytes,
    )
}

/** One album per folder that directly holds something, led by its newest photo, the newest album first. */
private fun albumsOf(newestFirst: List<Media>): List<Album> =
    newestFirst.groupBy { it.albumUri }.map { (albumUri, medias) ->
        Album(albumUri, medias.first().albumName, Thumbnail(uri = medias.first().uri), medias.size)
    }

/** How a failed walk reads to the screens inherited from Glimpse. */
internal fun mediaErrorOf(failure: Failure): MediaError =
    when (failure) {
        Failure.NOT_FOUND -> MediaError.NOT_FOUND
        Failure.LOCKED, Failure.NOT_AUTHORIZED, Failure.ACCESS_OFF, Failure.ENDED -> MediaError.AUTHENTICATION_REQUIRED
        else -> MediaError.IO
    }

/**
 * What the vault holds, for every screen, walked once per session and again when it may have changed.
 *
 * In memory alone, and forgotten as soon as the vault locks or the session goes, so no screen can go on
 * showing what the vault held (AGENTS.md I3).
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class VaultLibrary(
    private val link: VaultLink,
    scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow<RequestStatus<Library, MediaError>>(RequestStatus.Loading())
    val state: StateFlow<RequestStatus<Library, MediaError>> = mutableState.asStateFlow()

    private val asked = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        scope.launch {
            link.state
                .map { (it as? LinkState.Open)?.takeUnless(LinkState.Open::locked)?.session }
                .distinctUntilChanged()
                .collectLatest { session ->
                    mutableState.value = RequestStatus.Loading()
                    if (session != null) walkOnEveryChange(session)
                }
        }
    }

    /** Walks again: encri's notice that a folder changed is best effort, so a screen coming back asks. */
    fun reload() {
        asked.tryEmit(Unit)
    }

    private suspend fun walkOnEveryChange(session: VaultSession) {
        merge(flowOf(Unit), asked, link.changes.debounce(CHANGES_QUIET_MILLIS).map { })
            .collectLatest {
                // Through the link, so a walk that finds the vault locked or the session over is the gate's to say,
                // and publishes nothing once the link has moved on from the session it walked.
                val walked = link.ask(session, ::walkLibrary)
                currentCoroutineContext().ensureActive()
                when (walked) {
                    is EncriResult.Ok -> publish(walked.value)
                    is EncriResult.Failed -> mutableState.value = RequestStatus.Error(mediaErrorOf(walked.failure))
                }
            }
    }

    /** Only what is different, so a screen coming back to the same vault does not redraw it. */
    private fun publish(library: Library) {
        val current = mutableState.value
        if (current is RequestStatus.Success && current.data == library) return
        mutableState.value = RequestStatus.Success(library)
    }

    private companion object {
        /** A copy into the vault tells of many changes; walking once after them is enough. */
        const val CHANGES_QUIET_MILLIS = 500L
    }
}
