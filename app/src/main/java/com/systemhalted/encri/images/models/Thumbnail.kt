/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.models

import android.graphics.Bitmap
import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import com.bumptech.glide.request.RequestOptions
import com.systemhalted.encri.images.ext.px
import com.systemhalted.encri.images.ext.readParcelable
import com.systemhalted.encri.images.vault.VaultGlide

/**
 * A thumbnail for a media item. It can be a URI or a bitmap. Both can be defined, in that case the
 * URI should take precedence. At least one of the two should be non-null.
 *
 * @param uri The URI of the thumbnail
 * @param bitmap The bitmap of the thumbnail
 */
data class Thumbnail(
    val uri: Uri? = null,
    val bitmap: Bitmap? = null,
) : Comparable<Thumbnail>, Parcelable {
    init {
        require(uri != null || bitmap != null) {
            "At least one of the fields should be non-null"
        }
    }

    override fun compareTo(other: Thumbnail) = compareValuesBy(
        this, other,
        Thumbnail::uri,
    ).let {
        if (it == 0) {
            return@let when (this.bitmap?.sameAs(other.bitmap)) {
                true -> 0
                false -> 1
                null -> when (other.bitmap == null) {
                    true -> 0
                    false -> -1
                }
            }
        } else {
            it
        }
    }

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeParcelable(uri, 0)
        dest.writeParcelable(bitmap, flags)
    }

    companion object CREATOR : Parcelable.Creator<Thumbnail> {
        /**
         * Maximum thumbnail size, useful for high density screens.
         */
        val MAX_THUMBNAIL_SIZE = 128.px

        /**
         * A grid cell: encri's own preview, so a thumbnail never decodes an original here, and
         * none at all while the user has previews off (I13).
         */
        fun previewOptions() = RequestOptions()
            .override(MAX_THUMBNAIL_SIZE, MAX_THUMBNAIL_SIZE)
            .centerCrop()
            .set(VaultGlide.PREVIEW, true)
            .skipMemoryCache(!VaultGlide.previewsShown())

        override fun createFromParcel(parcel: Parcel) = Thumbnail(
            uri = parcel.readParcelable(Uri::class),
            bitmap = parcel.readParcelable(Bitmap::class),
        )

        override fun newArray(size: Int) = arrayOfNulls<Thumbnail>(size)
    }
}
