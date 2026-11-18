/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.edit

import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import com.systemhalted.encri.images.models.Media
import java.io.OutputStream

/**
 * Where the editor reads the photo or video it edits, where it keeps the copy, and what the copy carries of the
 * original (I23).
 *
 * Nothing else in the `edit` package knows where a picture lives. This app's store is the vault's,
 * `VaultEditStore`; a gallery over MediaStore, Glimpse's, would have one of its own. Asking the user's leave to keep
 * copies is not part of it: only the vault needs it, and `SaveFlow` asks it before the copy is made.
 */
@OptIn(UnstableApi::class)
interface EditStore {
    /** What a copy carries of the original's metadata. */
    val carried: Carried

    /** Where a video is read from, by the player under the frame and by Transformer. */
    val videos: DataSource.Factory

    /** A new descriptor on [media]'s photo: each reader gets one of its own, as a descriptor's position is shared. */
    suspend fun open(media: Media): ParcelFileDescriptor

    /** Keeps [made] as a new photo or video beside [original], which is never changed, with its [Made.exif]. */
    suspend fun keep(
        original: Media,
        made: Made,
    ): Saved
}

/**
 * A copy, made and waiting to be kept: what it is to be called, its type, how it is written, and the EXIF tags a
 * photo's copy is to be given ([Carried.exifFrom]), which the store writes once the copy is kept.
 */
class Made(
    val name: String,
    val mimeType: String,
    val writeTo: (OutputStream) -> Unit,
    val release: () -> Unit,
    val exif: Map<String, String> = emptyMap(),
) {
    companion object {
        private const val EDITED = " (edited)"

        /** `IMG_1 (edited) (1)`: a copy's own mark, and the number a store gave a name that was taken. */
        private val MARKED = Regex("""${Regex.escape(EDITED)}( \(\d+\))?$""")

        /**
         * The name a copy of [original] is asked for: the original's, marked as edited, with [extension]. A copy of a
         * copy is not marked twice; the store numbers a name already taken (`IMG_1 (edited) (1).jpg`) (I22).
         */
        fun nameOf(
            original: String,
            extension: String,
        ): String {
            val dot = original.lastIndexOf('.').takeIf { it > 0 } ?: original.length
            val base = original.substring(0, dot).replace(MARKED, "").ifBlank { "copy" }
            return "$base$EDITED.$extension"
        }
    }
}

/** What became of a copy. */
sealed interface Saved {
    /** It is kept, beside the original, as [uri]. */
    data class Kept(
        val uri: Uri,
    ) : Saved

    /** The store does not let this app keep a copy there: in the vault, encri's grant lacks a right. */
    data object NotAllowed : Saved

    /** The store would not, or could not be asked. */
    data object Failed : Saved
}
