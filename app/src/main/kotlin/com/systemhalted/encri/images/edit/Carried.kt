/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.edit

import androidx.annotation.OptIn
import androidx.exifinterface.media.ExifInterface
import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.container.Mp4OrientationData
import androidx.media3.muxer.MuxerUtil

/**
 * What a copy carries of the original's metadata, which its store says (I23).
 *
 * A video's is written by its muxer, [MemoryMuxer], which [keeps] asks. A photo's EXIF can only be written onto a
 * file that seeks, which `ExifInterface` copies to a temporary file of its own as it writes, so the store writes it
 * where it can: [exifFrom] says which tags, and `Made.exif` carries them to the store.
 */
@OptIn(UnstableApi::class)
enum class Carried {
    /**
     * Nothing: no time taken, place, camera or words, so a copy says no more than its picture. The vault's, which
     * keeps a copy through a pipe that does not seek, and never on this phone's storage (I3, I22).
     */
    NOTHING,

    /**
     * All that still describes the copy: when and where it was taken, what took it and how, and its words. Not its
     * orientation, size or thumbnail, which are the copy's own, nor where a subject was in it, which a crop moves. For
     * a gallery whose copy belongs beside the original in time, as Glimpse's would.
     */
    ALL_BUT_ORIENTATION,
    ;

    /** The EXIF tags a photo's copy is given from [original]'s, by name: none for [NOTHING]. */
    fun exifFrom(original: ExifInterface): Map<String, String> =
        if (this == NOTHING) {
            emptyMap()
        } else {
            KEPT_TAGS.mapNotNull { tag -> original.getAttribute(tag)?.let { tag to it } }.toMap()
        }

    /** Whether a video's copy is given [entry] of the original's metadata. Its orientation is always the copy's own. */
    fun keeps(entry: Metadata.Entry): Boolean =
        this == ALL_BUT_ORIENTATION && entry !is Mp4OrientationData && MuxerUtil.isMetadataSupported(entry)

    private companion object {
        /**
         * Every tag [ALL_BUT_ORIENTATION] carries. Left out: the orientation, the sizes and the thumbnail, the
         * subject's area and place, which are in the original's pixels, the original's unique id, the software that
         * wrote it, and XMP.
         */
        val KEPT_TAGS =
            listOf(
                // When.
                ExifInterface.TAG_DATETIME,
                ExifInterface.TAG_DATETIME_ORIGINAL,
                ExifInterface.TAG_DATETIME_DIGITIZED,
                ExifInterface.TAG_OFFSET_TIME,
                ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
                ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
                ExifInterface.TAG_SUBSEC_TIME,
                ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
                ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
                // Where.
                ExifInterface.TAG_GPS_VERSION_ID,
                ExifInterface.TAG_GPS_LATITUDE_REF,
                ExifInterface.TAG_GPS_LATITUDE,
                ExifInterface.TAG_GPS_LONGITUDE_REF,
                ExifInterface.TAG_GPS_LONGITUDE,
                ExifInterface.TAG_GPS_ALTITUDE_REF,
                ExifInterface.TAG_GPS_ALTITUDE,
                ExifInterface.TAG_GPS_TIMESTAMP,
                ExifInterface.TAG_GPS_DATESTAMP,
                ExifInterface.TAG_GPS_PROCESSING_METHOD,
                ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
                ExifInterface.TAG_GPS_IMG_DIRECTION,
                ExifInterface.TAG_GPS_SPEED_REF,
                ExifInterface.TAG_GPS_SPEED,
                ExifInterface.TAG_GPS_TRACK_REF,
                ExifInterface.TAG_GPS_TRACK,
                ExifInterface.TAG_GPS_MAP_DATUM,
                ExifInterface.TAG_GPS_DOP,
                ExifInterface.TAG_GPS_MEASURE_MODE,
                ExifInterface.TAG_GPS_SATELLITES,
                ExifInterface.TAG_GPS_STATUS,
                ExifInterface.TAG_GPS_AREA_INFORMATION,
                ExifInterface.TAG_GPS_DIFFERENTIAL,
                ExifInterface.TAG_GPS_H_POSITIONING_ERROR,
                ExifInterface.TAG_GPS_DEST_LATITUDE_REF,
                ExifInterface.TAG_GPS_DEST_LATITUDE,
                ExifInterface.TAG_GPS_DEST_LONGITUDE_REF,
                ExifInterface.TAG_GPS_DEST_LONGITUDE,
                ExifInterface.TAG_GPS_DEST_BEARING_REF,
                ExifInterface.TAG_GPS_DEST_BEARING,
                ExifInterface.TAG_GPS_DEST_DISTANCE_REF,
                ExifInterface.TAG_GPS_DEST_DISTANCE,
                // What took it.
                ExifInterface.TAG_MAKE,
                ExifInterface.TAG_MODEL,
                ExifInterface.TAG_LENS_MAKE,
                ExifInterface.TAG_LENS_MODEL,
                ExifInterface.TAG_LENS_SPECIFICATION,
                ExifInterface.TAG_LENS_SERIAL_NUMBER,
                ExifInterface.TAG_BODY_SERIAL_NUMBER,
                ExifInterface.TAG_CAMERA_OWNER_NAME,
                // How.
                ExifInterface.TAG_EXPOSURE_TIME,
                ExifInterface.TAG_F_NUMBER,
                ExifInterface.TAG_APERTURE_VALUE,
                ExifInterface.TAG_MAX_APERTURE_VALUE,
                ExifInterface.TAG_SHUTTER_SPEED_VALUE,
                ExifInterface.TAG_BRIGHTNESS_VALUE,
                ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
                ExifInterface.TAG_EXPOSURE_PROGRAM,
                ExifInterface.TAG_EXPOSURE_MODE,
                ExifInterface.TAG_EXPOSURE_INDEX,
                ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
                ExifInterface.TAG_SENSITIVITY_TYPE,
                ExifInterface.TAG_METERING_MODE,
                ExifInterface.TAG_LIGHT_SOURCE,
                ExifInterface.TAG_FLASH,
                ExifInterface.TAG_FLASH_ENERGY,
                ExifInterface.TAG_FOCAL_LENGTH,
                ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
                ExifInterface.TAG_DIGITAL_ZOOM_RATIO,
                ExifInterface.TAG_WHITE_BALANCE,
                ExifInterface.TAG_SCENE_CAPTURE_TYPE,
                ExifInterface.TAG_SCENE_TYPE,
                ExifInterface.TAG_SUBJECT_DISTANCE,
                ExifInterface.TAG_SUBJECT_DISTANCE_RANGE,
                ExifInterface.TAG_CONTRAST,
                ExifInterface.TAG_SATURATION,
                ExifInterface.TAG_SHARPNESS,
                ExifInterface.TAG_GAIN_CONTROL,
                ExifInterface.TAG_SENSING_METHOD,
                ExifInterface.TAG_FILE_SOURCE,
                ExifInterface.TAG_CUSTOM_RENDERED,
                // Its words.
                ExifInterface.TAG_IMAGE_DESCRIPTION,
                ExifInterface.TAG_ARTIST,
                ExifInterface.TAG_COPYRIGHT,
                ExifInterface.TAG_USER_COMMENT,
            )
    }
}
