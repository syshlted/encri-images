/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.ui.dialogs

import android.content.Context
import android.text.InputType
import android.text.format.Formatter
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.exifinterface.media.ExifInterface
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.systemhalted.encri.images.R
import com.systemhalted.encri.images.ext.apertureValue
import com.systemhalted.encri.images.ext.artist
import com.systemhalted.encri.images.ext.copyright
import com.systemhalted.encri.images.ext.exposureTime
import com.systemhalted.encri.images.ext.focalLength
import com.systemhalted.encri.images.ext.isoSpeed
import com.systemhalted.encri.images.ext.make
import com.systemhalted.encri.images.ext.model
import com.systemhalted.encri.images.ext.round
import com.systemhalted.encri.images.ext.software
import com.systemhalted.encri.images.ext.toFraction
import com.systemhalted.encri.images.ext.userComment
import com.systemhalted.encri.images.models.Media
import com.systemhalted.encri.images.models.MediaType
import com.systemhalted.encri.images.ui.views.ListItem
import java.text.SimpleDateFormat

class MediaInfoBottomSheetDialog(
    context: Context,
    media: Media,
    exifInterface: ExifInterface?,
) : BottomSheetDialog(context) {
    // Views
    private val artistInfoListItem by lazy { findViewById<ListItem>(R.id.artistInfoListItem)!! }
    private val cameraInfoListItem by lazy { findViewById<ListItem>(R.id.cameraInfoListItem)!! }
    private val contentView by lazy { findViewById<View>(android.R.id.content)!! }
    private val dateTextView by lazy { findViewById<TextView>(R.id.dateTextView)!! }
    private val descriptionEditText by lazy { findViewById<EditText>(R.id.descriptionEditText)!! }
    private val locationInfoListItem by lazy { findViewById<ListItem>(R.id.locationInfoListItem)!! }
    private val mediaInfoListItem by lazy { findViewById<ListItem>(R.id.mediaInfoListItem)!! }
    private val timeTextView by lazy { findViewById<TextView>(R.id.timeTextView)!! }

    private val unknownString: String
        get() = context.resources.getString(R.string.media_info_unknown)

    init {
        setContentView(R.layout.media_info_bottom_sheet_dialog)

        ViewCompat.setOnApplyWindowInsetsListener(contentView) { _, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )

            contentView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                rightMargin = insets.right
            }

            windowInsets
        }

        val unknownString = unknownString

        dateTextView.text = dateFormatter.format(media.dateModified)
        timeTextView.text = timeFormatter.format(media.dateModified)

        mediaInfoListItem.leadingIconImage = ResourcesCompat.getDrawable(
            context.resources,
            when (media.mediaType) {
                MediaType.ALBUM -> R.drawable.ic_albums
                MediaType.IMAGE -> R.drawable.ic_image
                MediaType.VIDEO -> R.drawable.ic_video_camera_back
            },
            null
        )
        mediaInfoListItem.headlineText = media.displayName ?: unknownString

        mediaInfoListItem.supportingText = listOfNotNull(
            media.mimeType,
            exifInterface?.let { dimensionsOf(it) },
            Formatter.formatFileSize(context, media.sizeBytes),
        ).joinToString(SEPARATOR)

        exifInterface?.let { exif ->
            val userComment = exif.userComment?.takeIf { it.isNotBlank() }
            descriptionEditText.setText(userComment ?: "")
            descriptionEditText.inputType = InputType.TYPE_NULL
            descriptionEditText.isVisible = userComment != null

            artistInfoListItem.headlineText = exif.artist ?: unknownString

            artistInfoListItem.supportingText = listOfNotNull(
                exif.software,
                exif.copyright,
            ).joinToString(SEPARATOR)

            artistInfoListItem.isVisible = listOf(
                artistInfoListItem.headlineText,
                artistInfoListItem.supportingText,
            ).any { !it.isNullOrBlank() && it != unknownString }

            cameraInfoListItem.headlineText = listOfNotNull(
                exif.make,
                exif.model,
            ).joinToString(" ").takeIf { it.isNotBlank() } ?: unknownString

            cameraInfoListItem.supportingText = listOfNotNull(
                exif.exposureTime?.let { "${it.toFraction()}s" },
                exif.apertureValue?.let { "ƒ/${it.round(2)}" },
                exif.isoSpeed?.let { "ISO $it" },
                exif.focalLength?.let { "${it.round(2)}mm" },
            ).joinToString(SEPARATOR)

            cameraInfoListItem.isVisible = listOf(
                cameraInfoListItem.headlineText,
                cameraInfoListItem.supportingText,
            ).any { !it.isNullOrBlank() && it != unknownString }

            // The numbers alone. No geocoder and no map: either would send where this photo was taken
            // out of the vault, and the geocoder would do it without asking (I3).
            exif.latLong?.let {
                val (lat, long) = it

                locationInfoListItem.headlineText = listOf(
                    lat.round(8),
                    long.round(8),
                ).joinToString(SEPARATOR)

                locationInfoListItem.isVisible = true
            }
        }
    }

    private fun dimensionsOf(exif: ExifInterface): String? {
        val width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
        val height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)
        return "$width x $height".takeIf { width > 0 && height > 0 }
    }

    companion object {
        private const val SEPARATOR = " • "

        private val dateFormatter = SimpleDateFormat.getDateInstance()
        private val timeFormatter = SimpleDateFormat.getTimeInstance()
    }
}
