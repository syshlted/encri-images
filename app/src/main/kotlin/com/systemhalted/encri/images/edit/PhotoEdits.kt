/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.edit

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.IOException
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A photo as the editor shows it: [bitmap], upright and smaller than the photo if need be, of a photo [size] big. */
class Preview(
    val bitmap: Bitmap,
    val size: Size,
)

/** How a photo's copy is written: in the photo's own format where Android can write it, JPEG where it cannot. */
enum class PhotoFormat(
    val mimeType: String,
    val extension: String,
) {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp"),
    ;

    @Suppress("DEPRECATION") // WEBP is WEBP_LOSSY on API 30 and later, which this app runs below.
    internal val compressed: Bitmap.CompressFormat
        get() =
            when (this) {
                JPEG -> {
                    Bitmap.CompressFormat.JPEG
                }

                PNG -> {
                    Bitmap.CompressFormat.PNG
                }

                WEBP -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Bitmap.CompressFormat.WEBP_LOSSY
                    } else {
                        Bitmap.CompressFormat.WEBP
                    }
                }
            }

    companion object {
        /** HEIC, AVIF and the rest become JPEG: Android writes none of them. */
        fun of(mimeType: String): PhotoFormat =
            when (mimeType.lowercase()) {
                PNG.mimeType -> PNG
                WEBP.mimeType -> WEBP
                else -> JPEG
            }
    }
}

/** A photo's copy, made, and ready to be written to encri. */
class EditedPhoto(
    private val bitmap: Bitmap,
    val format: PhotoFormat,
) {
    val width: Int get() = bitmap.width
    val height: Int get() = bitmap.height

    /** The copy, encoded as it is written, so no whole encoded copy is held anywhere. */
    fun writeTo(out: OutputStream) {
        if (!bitmap.compress(format.compressed, QUALITY, out)) throw IOException("Android did not encode the copy")
    }

    fun release() = bitmap.recycle()

    private companion object {
        /** Near enough to the original that a crop does not show as a loss; PNG ignores it. */
        const val QUALITY = 95
    }
}

/**
 * A photo cropped or turned (I22), decoded here from a descriptor [open] gives, as the viewer decodes a photo (I4).
 *
 * The photo's own EXIF orientation is applied as it is shown, so the crop the user chose on the photo they saw is the
 * crop they get. **Only the part kept is decoded**, a band of rows at a time, each drawn straight into the copy,
 * turned: one bitmap the size of the copy is all that is held, sampled down where it would be larger than
 * [MOST_PIXELS] or where this phone has no memory for it. The copy carries no EXIF: no location, no camera, no time it
 * was taken.
 *
 * [open] gives a new descriptor each time, which the caller's seeking would otherwise share.
 */
class PhotoEdits(
    private val open: suspend () -> ParcelFileDescriptor,
) {
    /** The photo upright, no larger than [longest] on its longer side, as long as that is a power of two smaller. */
    suspend fun preview(longest: Int = PREVIEW_LONGEST): Preview =
        withContext(Dispatchers.IO) {
            val orientation = orientation()
            val raw = rawSize()
            var sample = 1
            while (max(raw.width, raw.height) / sample > longest) sample *= 2
            val decoded =
                open().use { descriptor ->
                    val options = BitmapFactory.Options().apply { inSampleSize = sample }
                    BitmapFactory.decodeFileDescriptor(descriptor.fileDescriptor, null, options)
                } ?: throw IOException("Android could not decode the photo")
            val placed = Placement.ofExif(orientation, Size(decoded.width.toFloat(), decoded.height.toFloat()))
            val upright = if (placed == Placement.NONE) decoded else placedCopy(decoded, placed)
            Preview(upright, Placement.ofExif(orientation, raw).map(Box.of(raw)).let { Size(it.width, it.height) })
        }

    /** The copy [edit] asks for, in [format]. */
    suspend fun render(
        edit: Edit,
        format: PhotoFormat,
    ): EditedPhoto =
        withContext(Dispatchers.IO) {
            val orientation = orientation()
            val raw = rawSize()
            val placement = Placement.ofExif(orientation, raw).then(edit.turning)
            val kept = placement.inverse.map(edit.crop)
            var sample = 1
            while ((kept.width / sample) * (kept.height / sample) > MOST_PIXELS) sample *= 2
            var made: Bitmap? = null
            while (made == null) {
                made =
                    try {
                        rendered(raw, placement, kept, sample)
                    } catch (full: OutOfMemoryError) {
                        // No room for the copy at this size: half as wide and half as high, up to a point.
                        if (sample >= MOST_SAMPLE) throw TooLargeToEdit(full)
                        sample *= 2
                        null
                    }
            }
            EditedPhoto(made, format)
        }

    /** [kept], a box of the photo's own pixels, decoded at 1/[sample] of its size, placed by [placement]. */
    private suspend fun rendered(
        raw: Size,
        placement: Placement,
        kept: Box,
        sample: Int,
    ): Bitmap {
        val region = aligned(kept, raw, sample)
        val across = region.width() / sample
        val down = region.height() / sample
        val copy = Copy(if (placement.swapsSides) down else across, if (placement.swapsSides) across else down)
        copy.place(placement, region, sample)
        var made = false
        try {
            open().use { descriptor ->
                val decoder = regionDecoderOf(descriptor)
                if (decoder == null) copy.draw(whole(sample), 0, 0) else drawBands(decoder, region, sample, copy)
            }
            made = true
        } finally {
            // Whatever stopped it, a copy half made is not kept.
            if (!made) copy.release()
        }
        return copy.bitmap ?: throw IOException("nothing of the photo was decoded")
    }

    /** [region] of the photo, a band of rows at a time, each drawn into [copy] as it is decoded. */
    private fun drawBands(
        decoder: BitmapRegionDecoder,
        region: Rect,
        sample: Int,
        copy: Copy,
    ) {
        try {
            var top = region.top
            while (top < region.bottom) {
                val bottom = min(top + BAND_ROWS * sample, region.bottom)
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                val band =
                    decoder.decodeRegion(Rect(region.left, top, region.right, bottom), options)
                        ?: throw IOException("Android could not decode the photo")
                copy.draw(band, region.left, top)
                top = bottom
            }
        } finally {
            decoder.recycle()
        }
    }

    /** All of the photo, at 1/[sample] of its size, for a format Android cannot decode a part of. */
    private suspend fun whole(sample: Int): Bitmap =
        open().use { descriptor ->
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFileDescriptor(descriptor.fileDescriptor, null, options)
        } ?: throw IOException("Android could not decode the photo")

    private suspend fun orientation(): Int =
        open().use { descriptor ->
            runCatching {
                ExifInterface(FileInputStream(descriptor.fileDescriptor))
                    .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        }

    private suspend fun rawSize(): Size =
        open().use { descriptor ->
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFileDescriptor(descriptor.fileDescriptor, null, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Android could not read the photo")
            Size(bounds.outWidth.toFloat(), bounds.outHeight.toFloat())
        }

    /**
     * The copy, made when its first band is decoded, in that band's colour space, so a photo in Display P3 is not
     * flattened to sRGB on the way.
     */
    private class Copy(
        private val width: Int,
        private val height: Int,
    ) {
        var bitmap: Bitmap? = null
            private set
        private var canvas: Canvas? = null
        private val exact = Paint()
        private val matrix = Matrix()
        private val placed = Matrix()
        private var sample = 1
        private var origin = Box(0f, 0f, 0f, 0f)

        /** Each band is of [region] of the photo's own pixels, at 1/[sample], and goes where [placement] puts it. */
        fun place(
            placement: Placement,
            region: Rect,
            sample: Int,
        ) {
            placed.setValues(placement.values())
            this.sample = sample
            origin =
                placement.map(
                    Box(region.left.toFloat(), region.top.toFloat(), region.right.toFloat(), region.bottom.toFloat()),
                )
        }

        /** [band], whose top-left is at ([left], [top]) of the photo's own pixels, drawn where it goes. */
        fun draw(
            band: Bitmap,
            left: Int,
            top: Int,
        ) {
            val into = canvas ?: start(band)
            matrix.setScale(sample.toFloat(), sample.toFloat())
            matrix.postTranslate(left.toFloat(), top.toFloat())
            matrix.postConcat(placed)
            matrix.postTranslate(-origin.left, -origin.top)
            matrix.postScale(1f / sample, 1f / sample)
            into.drawBitmap(band, matrix, exact)
            band.recycle()
        }

        fun release() {
            bitmap?.recycle()
            bitmap = null
            canvas = null
        }

        private fun start(band: Bitmap): Canvas {
            val space = band.colorSpace ?: ColorSpace.get(ColorSpace.Named.SRGB)
            val made = createBitmap(width, height, Bitmap.Config.ARGB_8888, band.hasAlpha(), space)
            bitmap = made
            return Canvas(made).also { canvas = it }
        }
    }

    companion object {
        /** No larger on its longer side than a phone's GPU is sure to draw. */
        const val PREVIEW_LONGEST = 4096

        /** About 50 megapixels, 200 MB as a bitmap: a 50 MP camera's photo is kept whole, and a 200 MP one halved. */
        const val MOST_PIXELS = 52_000_000f

        /** A photo that will not fit at a sixteenth of its size is not edited here. */
        private const val MOST_SAMPLE = 16
        private const val BAND_ROWS = 1024

        /** [kept] in whole pixels inside [raw], its corner and its sides whole multiples of [sample]. */
        internal fun aligned(
            kept: Box,
            raw: Size,
            sample: Int,
        ): Rect {
            val right = kept.right.roundToInt().coerceIn(1, raw.width.toInt())
            val bottom = kept.bottom.roundToInt().coerceIn(1, raw.height.toInt())
            val left = kept.left.roundToInt().coerceIn(0, right - 1) / sample * sample
            val top = kept.top.roundToInt().coerceIn(0, bottom - 1) / sample * sample
            val width = max(sample, (right - left) / sample * sample).coerceAtMost(raw.width.toInt() - left)
            val height = max(sample, (bottom - top) / sample * sample).coerceAtMost(raw.height.toInt() - top)
            return Rect(left, top, left + width, top + height)
        }

        private fun regionDecoderOf(descriptor: ParcelFileDescriptor): BitmapRegionDecoder? =
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    BitmapRegionDecoder.newInstance(descriptor)
                } else {
                    @Suppress("DEPRECATION") // Its replacement is API 31's.
                    BitmapRegionDecoder.newInstance(descriptor.fileDescriptor, false)
                }
            } catch (_: IOException) {
                // A format Android decodes only whole.
                null
            }

        /** [bitmap] placed by [placement], which is for a picture its size. */
        private fun placedCopy(
            bitmap: Bitmap,
            placement: Placement,
        ): Bitmap {
            val size = placement.map(Box(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()))
            val placed = createBitmap(size.width.toInt(), size.height.toInt())
            Canvas(placed).drawBitmap(bitmap, Matrix().apply { setValues(placement.values()) }, Paint())
            bitmap.recycle()
            return placed
        }
    }
}
