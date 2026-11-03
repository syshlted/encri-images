/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.repository

import android.net.Uri
import com.systemhalted.encri.images.datasources.MediaDataSource
import com.systemhalted.encri.images.models.MediaType

/**
 * Media repository. This class coordinates the screens and their data source.
 */
class MediaRepository(
    private val dataSource: MediaDataSource,
) {
    /**
     * @see MediaDataSource.reels
     */
    fun reels(
        mediaType: MediaType? = null,
        mimeType: String? = null,
    ) = dataSource.reels(mediaType, mimeType)

    /**
     * @see MediaDataSource.albums
     */
    fun albums(
        mediaType: MediaType? = null,
        mimeType: String? = null,
    ) = dataSource.albums(mediaType, mimeType)

    /**
     * @see MediaDataSource.album
     */
    fun album(albumUri: Uri) = dataSource.album(albumUri)
}
