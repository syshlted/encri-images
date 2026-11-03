/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.utils

import com.systemhalted.encri.images.models.MediaType

object MimeUtils {
    fun mimeTypeToMediaType(mimeType: String) = when (mimeType) {
        "vnd.android.cursor.dir/image" -> MediaType.ALBUM
        "vnd.android.cursor.dir/video" -> MediaType.ALBUM

        "vnd.android.cursor.item/image" -> MediaType.IMAGE
        "vnd.android.cursor.item/video" -> MediaType.VIDEO

        else -> when {
            mimeType.startsWith("image/") -> MediaType.IMAGE
            mimeType.startsWith("video/") -> MediaType.VIDEO
            else -> null
        }
    }
}
