/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.edit

import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.annotation.OptIn
import androidx.exifinterface.media.ExifInterface
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.FileDataSource
import com.systemhalted.encri.images.models.Media
import java.io.File

/**
 * A store that is not the vault (I23): photos and their copies as files in [folder], as a gallery over MediaStore
 * would keep them, carrying what [carried] says. That the editor works over it is what lets the editor go to Glimpse
 * with a store of its own.
 */
@OptIn(UnstableApi::class)
class FolderStore(
    private val folder: File,
    override val carried: Carried = Carried.NOTHING,
) : EditStore {
    val kept = mutableListOf<File>()

    /** What [keep] answers instead of keeping, when set. */
    var answer: Saved? = null

    override val videos: DataSource.Factory = FileDataSource.Factory()

    override suspend fun open(media: Media): ParcelFileDescriptor =
        ParcelFileDescriptor.open(File(folder, requireNotNull(media.displayName)), ParcelFileDescriptor.MODE_READ_ONLY)

    override suspend fun keep(
        original: Media,
        made: Made,
    ): Saved {
        answer?.let { return it }
        val file = File(folder, made.name)
        file.outputStream().use(made.writeTo)
        // A file seeks, so a photo's tags are written onto it once it is whole, as MediaStore's would be.
        if (made.exif.isNotEmpty()) ExifInterface(file).apply { made.exif.forEach(::setAttribute) }.saveAttributes()
        kept += file
        return Saved.Kept(Uri.fromFile(file))
    }
}
