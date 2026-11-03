/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.vault

import android.net.Uri
import com.systemhalted.encri.images.datasources.MediaDataSource
import com.systemhalted.encri.images.datasources.MediaError
import com.systemhalted.encri.images.models.Album
import com.systemhalted.encri.images.models.Media
import com.systemhalted.encri.images.models.MediaType
import com.systemhalted.encri.images.models.RequestStatus
import com.systemhalted.encri.images.models.RequestStatus.Companion.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Glimpse's seam for where media comes from, answered from the vault rather than from MediaStore (I4). */
class VaultDataSource(
    private val library: VaultLibrary,
) : MediaDataSource {
    override fun reels(
        mediaType: MediaType?,
        mimeType: String?,
    ): Flow<RequestStatus<List<Media>, MediaError>> =
        library.state.map { status ->
            status.map { library -> library.medias.filter { it.matches(mediaType, mimeType) } }
        }

    override fun albums(
        mediaType: MediaType?,
        mimeType: String?,
    ): Flow<RequestStatus<List<Album>, MediaError>> =
        library.state.map { status ->
            status.map { library ->
                library.albums.filter { album ->
                    library.mediasIn(album.uri).any { it.matches(mediaType, mimeType) }
                }
            }
        }

    override fun album(albumUri: Uri): Flow<RequestStatus<Pair<Album, List<Media>>, MediaError>> =
        library.state.map { status ->
            when (status) {
                is RequestStatus.Loading -> {
                    RequestStatus.Loading()
                }

                is RequestStatus.Error -> {
                    RequestStatus.Error(status.error, status.throwable)
                }

                is RequestStatus.Success -> {
                    status.data.albums.firstOrNull { it.uri == albumUri }?.let {
                        RequestStatus.Success(it to status.data.mediasIn(albumUri))
                    } ?: RequestStatus.Error(MediaError.NOT_FOUND)
                }
            }
        }

    private fun Media.matches(
        mediaType: MediaType?,
        mimeType: String?,
    ): Boolean = (mediaType == null || this.mediaType == mediaType) && (mimeType == null || this.mimeType == mimeType)
}

/** Where the viewer opens in [medias]: at [initialUri] when it is there, and at the start when it is not. */
fun initialPositionIn(
    medias: List<Media>,
    initialUri: Uri?,
): Int = medias.indexOfFirst { it.uri == initialUri }.coerceAtLeast(0)
