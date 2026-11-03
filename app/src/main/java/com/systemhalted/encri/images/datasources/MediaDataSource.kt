/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.datasources

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import com.systemhalted.encri.images.models.Album
import com.systemhalted.encri.images.models.Media
import com.systemhalted.encri.images.models.MediaType
import com.systemhalted.encri.images.models.RequestStatus

typealias MediaRequestStatus<T> = RequestStatus<T, MediaError>

/**
 * A data source for media.
 */
interface MediaDataSource {
    /**
     * Get all the media.
     *
     * @param mediaType The file type to filter for
     * @param mimeType The MIME type to filter for
     */
    fun reels(
        mediaType: MediaType?,
        mimeType: String?,
    ): Flow<MediaRequestStatus<List<Media>>>

    /**
     * Get all the albums.
     *
     * @param mediaType The file type to filter for
     * @param mimeType The MIME type to filter for
     */
    fun albums(
        mediaType: MediaType?,
        mimeType: String?,
    ): Flow<MediaRequestStatus<List<Album>>>

    /**
     * Get the album information and all the medias of the given album.
     */
    fun album(albumUri: Uri): Flow<MediaRequestStatus<Pair<Album, List<Media>>>>
}
