/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.models

import android.net.Uri

sealed interface MediaItem<T : MediaItem<T>> : UniqueItem<T> {
    /**
     * The media type.
     */
    val mediaType: MediaType

    /**
     * A [Uri] identifying this media item.
     */
    val uri: Uri

    override fun areItemsTheSame(other: T) = this.uri == other.uri
}
