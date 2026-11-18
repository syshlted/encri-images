/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.vault

import android.os.ParcelFileDescriptor
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import com.systemhalted.encri.client.valueOrNull
import com.systemhalted.encri.images.edit.Carried
import com.systemhalted.encri.images.edit.EditStore
import com.systemhalted.encri.images.edit.Made
import com.systemhalted.encri.images.edit.Saved
import com.systemhalted.encri.images.models.Media
import java.io.IOException

/**
 * The editor's store in the vault (I23): a photo or video is read through [link], as the viewer reads it (I4), and a
 * copy is kept by [copies], beside the original, after encri's consent (I22).
 *
 * A copy carries nothing of the original's metadata. A photo's EXIF could only be written onto a file that seeks, by
 * `ExifInterface`, which copies the picture to a temporary file as it writes; encri's pipe does not seek, and no
 * picture is written to this phone's storage (I3).
 */
@OptIn(UnstableApi::class)
class VaultEditStore(
    private val link: VaultLink,
    private val copies: Copies,
) : EditStore {
    override val carried = Carried.NOTHING

    /** Nothing but the vault, as the viewer's player reads it: no cache, and nothing that can reach a network (I3). */
    override val videos: DataSource.Factory = VaultMediaSource.Factory(link)

    override suspend fun open(media: Media): ParcelFileDescriptor =
        VaultUris.documentIdOf(media.uri)?.let { id -> link.ask { it.openRead(id) }.valueOrNull() }
            ?: throw IOException("encri did not open it")

    override suspend fun keep(
        original: Media,
        made: Made,
    ): Saved {
        require(made.exif.isEmpty()) { "the vault's copies carry no EXIF" }
        return copies.save(original, made.name, made.mimeType, made.writeTo)
    }
}
