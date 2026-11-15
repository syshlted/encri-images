/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.edit

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Crop
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultAssetLoaderFactory
import androidx.media3.transformer.DefaultDecoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.systemhalted.encri.images.vault.VaultLink
import com.systemhalted.encri.images.vault.VaultMediaSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.math.floor

/** A photo or video was too large to hold in memory while its copy was made (I22). */
class TooLargeToEdit(
    cause: Throwable? = null,
) : IOException("too large to edit here", cause)

/**
 * A video cropped or turned (I22), made by Media3's Transformer into memory.
 *
 * It reads the video through [VaultMediaSource], as the viewer's player does, decodes and encodes it on this phone,
 * and muxes it into a [MemoryOutput], so nothing of it reaches a file. A turn and nothing else is not decoded at all:
 * Transformer writes the turn into the track's orientation. Transformer's reports to the platform, which Google may
 * collect, are off.
 */
@OptIn(UnstableApi::class)
class VideoEdits(
    private val context: Context,
    private val link: VaultLink,
) {
    /**
     * The video [uri] names, edited as [edit] asks, held in memory no larger than [limit]; [onProgress] is told how far
     * along it is, in percent. Cancelling stops Transformer and drops what it made.
     */
    suspend fun render(
        uri: Uri,
        edit: Edit,
        limit: Long,
        onProgress: (Int) -> Unit,
    ): MemoryOutput =
        withContext(Dispatchers.Main) {
            val output = MemoryOutput(limit)
            val done = CompletableDeferred<Unit>()
            val transformer = transformerInto(output, done)
            transformer.start(EditedMediaItem.Builder(MediaItem.fromUri(uri)).setEffects(effectsOf(edit)).build(), PATH)
            val progress =
                launch {
                    val holder = ProgressHolder()
                    while (isActive) {
                        if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                            onProgress(holder.progress)
                        }
                        delay(PROGRESS_MILLIS)
                    }
                }
            try {
                done.await()
                output
            } catch (stopped: CancellationException) {
                transformer.cancel()
                output.release()
                throw stopped
            } catch (failed: IOException) {
                output.release()
                throw if (output.overflowed) TooLargeToEdit(failed) else failed
            } finally {
                progress.cancel()
            }
        }

    private fun transformerInto(
        output: MemoryOutput,
        done: CompletableDeferred<Unit>,
    ): Transformer =
        Transformer
            .Builder(context)
            .setUsePlatformDiagnostics(false)
            .setMuxerFactory(MemoryMuxer.Factory(output))
            .setAssetLoaderFactory(
                DefaultAssetLoaderFactory(
                    context,
                    DefaultDecoderFactory.Builder(context).build(),
                    Clock.DEFAULT,
                    // Nothing but the vault, as the viewer's player reads it (I3, I4).
                    ProgressiveMediaSource.Factory(VaultMediaSource.Factory(link)),
                    NoPictures,
                ),
            ).addListener(
                object : Transformer.Listener {
                    override fun onCompleted(
                        composition: Composition,
                        exportResult: ExportResult,
                    ) {
                        done.complete(Unit)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        done.completeExceptionally(IOException("Transformer: ${exportException.errorCodeName}"))
                    }
                },
            ).build()

    /** A video's pictures come from the video; Transformer is never asked to load one by itself. */
    private object NoPictures : BitmapLoader {
        override fun supportsMimeType(mimeType: String): Boolean = false

        override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
            Futures.immediateFailedFuture(UnsupportedOperationException("no pictures here"))

        override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
            Futures.immediateFailedFuture(UnsupportedOperationException("no pictures here"))
    }

    companion object {
        /** What Transformer is told it writes to. Nothing opens it: [MemoryMuxer.Factory] ignores it. */
        private const val PATH = "encri-images-memory"
        private const val PROGRESS_MILLIS = 250L

        /**
         * What Transformer is asked to do for [edit]: the crop, on the video as it is shown, then the turns. A turn
         * alone is a [ScaleAndRotateTransformation] and nothing else, which Transformer writes as the track's
         * orientation rather than decoding the video.
         */
        fun effectsOf(edit: Edit): Effects {
            val video = mutableListOf<Effect>()
            val whole = Box.of(edit.source)
            val crop = evenly(edit.cropOfSource, edit.source)
            if (!crop.near(whole, SLACK)) {
                // Crop's coordinates are the frame's, -1 to 1, with y upward.
                video +=
                    Crop(
                        crop.left / whole.width * 2 - 1,
                        crop.right / whole.width * 2 - 1,
                        1 - crop.bottom / whole.height * 2,
                        1 - crop.top / whole.height * 2,
                    )
            }
            if (edit.turns != 0) {
                // ScaleAndRotateTransformation turns anticlockwise.
                val degrees = (Placement.QUARTERS - edit.turns) * QUARTER_DEGREES
                video += ScaleAndRotateTransformation.Builder().setRotationDegrees(degrees.toFloat()).build()
            }
            return Effects(listOf(), video)
        }

        /** [box] moved to whole pixels, an even number of them on each side, as a video encoder asks. */
        private fun evenly(
            box: Box,
            frame: Size,
        ): Box {
            val left = even(box.left).coerceAtMost(even(frame.width) - 2)
            val top = even(box.top).coerceAtMost(even(frame.height) - 2)
            val width = maxOf(2f, even(box.width)).coerceAtMost(even(frame.width - left))
            val height = maxOf(2f, even(box.height)).coerceAtMost(even(frame.height - top))
            return Box(left, top, left + width, top + height)
        }

        private fun even(value: Float): Float = floor(value / 2 + ROUNDING) * 2

        /** A crop within a pixel of the whole frame keeps all of it. */
        private const val SLACK = 1f

        /** How near a whole pixel counts as one, so that 1279.99 is 1280. */
        private const val ROUNDING = 0.001f
        private const val QUARTER_DEGREES = 90
    }
}
