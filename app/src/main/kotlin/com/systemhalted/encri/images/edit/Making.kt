/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.edit

import android.content.Context
import androidx.exifinterface.media.ExifInterface
import com.systemhalted.encri.images.models.Media
import com.systemhalted.encri.images.models.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream

/**
 * How the copy of [media] is made (I22): a photo by [PhotoEdits], a video by [VideoEdits], each read from [store]
 * and carrying what [store] says of the original's metadata (I23).
 */
class Making(
    private val context: Context,
    private val store: EditStore,
    private val media: Media,
) {
    private val photos = PhotoEdits { store.open(media) }

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
            val exif = exifToCarry()
            val photo = photos.render(edit, format)
            val name = Made.nameOf(original, format.extension)
            return Made(name, format.mimeType, photo::writeTo, photo::release, exif)
        }
        val limit = MemoryOutput.limitOn(context)
        // A turn alone is the video as it was, its size and all, so one too large to hold is known before it is made.
        if (edit.cropOfSource.near(Box.of(edit.source), 1f) && media.sizeBytes > limit) throw TooLargeToEdit()
        val video = VideoEdits(context, store.videos, store.carried).render(media.uri, edit, limit, onProgress)
        return Made(Made.nameOf(original, VIDEO_EXTENSION), VIDEO_TYPE, video::writeTo, video::release)
    }

    /** The photo's EXIF tags its copy carries; none where the store carries none, or the photo's cannot be read. */
    private suspend fun exifToCarry(): Map<String, String> {
        if (store.carried == Carried.NOTHING) return emptyMap()
        return withContext(Dispatchers.IO) {
            store.open(media).use { descriptor ->
                runCatching { store.carried.exifFrom(ExifInterface(FileInputStream(descriptor.fileDescriptor))) }
                    .getOrDefault(emptyMap())
            }
        }
    }

    private companion object {
        const val VIDEO_TYPE = "video/mp4"
        const val VIDEO_EXTENSION = "mp4"
    }
}
