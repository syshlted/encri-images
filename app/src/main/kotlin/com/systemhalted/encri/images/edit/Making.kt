/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.edit

import android.content.Context
import android.os.ParcelFileDescriptor
import com.systemhalted.encri.client.valueOrNull
import com.systemhalted.encri.images.models.Media
import com.systemhalted.encri.images.models.MediaType
import com.systemhalted.encri.images.vault.Copies
import com.systemhalted.encri.images.vault.VaultLink
import com.systemhalted.encri.images.vault.VaultUris
import java.io.IOException

/** How the copy of [media] is made (I22): a photo by [PhotoEdits], a video by [VideoEdits], each through [link]. */
class Making(
    private val context: Context,
    private val link: VaultLink,
    private val media: Media,
) {
    private val photos = PhotoEdits(::open)

    /** The photo as the editor shows it. */
    suspend fun preview(): Preview = photos.preview()

    /** The copy [edit] asks for, telling [onProgress] how far along a video is, in percent. */
    suspend fun make(
        edit: Edit,
        onProgress: (Int) -> Unit,
    ): Made {
        val original = media.displayName.orEmpty()
        if (media.mediaType != MediaType.VIDEO) {
            val format = PhotoFormat.of(media.mimeType)
            val photo = photos.render(edit, format)
            return Made(Copies.nameOf(original, format.extension), format.mimeType, photo::writeTo, photo::release)
        }
        val limit = MemoryOutput.limitOn(context)
        // A turn alone is the video as it was, its size and all, so one too large to hold is known before it is made.
        if (edit.cropOfSource.near(Box.of(edit.source), 1f) && media.sizeBytes > limit) throw TooLargeToEdit()
        val video = VideoEdits(context, link).render(media.uri, edit, limit, onProgress)
        return Made(Copies.nameOf(original, VIDEO_EXTENSION), VIDEO_TYPE, video::writeTo, video::release)
    }

    /** A new descriptor on the photo, through the link (I7): each reader seeks on its own. */
    private suspend fun open(): ParcelFileDescriptor =
        VaultUris.documentIdOf(media.uri)?.let { id -> link.ask { it.openRead(id) }.valueOrNull() }
            ?: throw IOException("encri did not open it")

    private companion object {
        const val VIDEO_TYPE = "video/mp4"
        const val VIDEO_EXTENSION = "mp4"
    }
}
