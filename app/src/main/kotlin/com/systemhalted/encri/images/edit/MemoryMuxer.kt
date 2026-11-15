/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.edit

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Metadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.container.Mp4OrientationData
import androidx.media3.muxer.BufferInfo
import androidx.media3.muxer.Mp4Muxer
import androidx.media3.muxer.Muxer
import com.google.common.collect.ImmutableList
import java.nio.ByteBuffer

/**
 * Media3's MP4 muxer over [MemoryOutput], for Transformer (I22): its own `InAppMp4Muxer` without the file it opens.
 *
 * A video's orientation is written as its track's, which is how a quarter turn alone is saved without decoding the
 * video at all. **Nothing else of the original's metadata is carried**: no location, no time it was made. A copy says
 * no more about where it was taken than a copy of a photo does.
 */
@OptIn(UnstableApi::class)
class MemoryMuxer private constructor(
    private val muxer: Mp4Muxer,
) : Muxer {
    override fun addTrack(format: Format): Int {
        val track = muxer.addTrack(format)
        if (MimeTypes.isVideo(format.sampleMimeType)) muxer.addMetadataEntry(Mp4OrientationData(format.rotationDegrees))
        return track
    }

    override fun writeSampleData(
        trackId: Int,
        byteBuffer: ByteBuffer,
        bufferInfo: BufferInfo,
    ) = muxer.writeSampleData(trackId, byteBuffer, bufferInfo)

    override fun addMetadataEntry(metadataEntry: Metadata.Entry) = Unit

    override fun close() = muxer.close()

    /** Every muxer it makes writes to [output], whatever path Transformer names: there is no file. */
    class Factory(
        private val output: MemoryOutput,
    ) : Muxer.Factory {
        override fun create(path: String): Muxer = MemoryMuxer(Mp4Muxer.Builder(output).build())

        override fun getSupportedSampleMimeTypes(trackType: Int): ImmutableList<String> =
            when (trackType) {
                C.TRACK_TYPE_VIDEO -> Mp4Muxer.SUPPORTED_VIDEO_SAMPLE_MIME_TYPES
                C.TRACK_TYPE_AUDIO -> Mp4Muxer.SUPPORTED_AUDIO_SAMPLE_MIME_TYPES
                else -> ImmutableList.of()
            }

        override fun supportsWritingNegativeTimestampsInEditList(): Boolean = true
    }
}
