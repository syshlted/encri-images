/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.vault

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.bumptech.glide.Glide
import com.github.panpf.zoomimage.glide.GlideSubsamplingImageGenerator
import com.github.panpf.zoomimage.subsampling.ImageSource
import com.github.panpf.zoomimage.subsampling.SubsamplingImage
import com.github.panpf.zoomimage.subsampling.SubsamplingImageGenerateResult
import com.github.panpf.zoomimage.subsampling.toFactory
import com.systemhalted.encri.client.EncriResult
import kotlinx.coroutines.runBlocking
import okio.Source
import okio.source
import java.io.IOException
import java.util.UUID

/**
 * Zoom that reads the photo itself (AGENTS.md I15). ZoomImage cuts a large photo into tiles and decodes those on
 * screen at the zoom asked for, which it can do only from a source it can open again. It knows how to open http(s),
 * `content` and `file` and nothing else, and for any other model asked Glide for the picture as a file from a disk
 * cache that is always empty (I8), so a vault photo zoomed was its screen-sized decode, magnified.
 *
 * Here a vault document's source is a descriptor encri serves, opened through the link each time ZoomImage asks, so a
 * tile is read from the vault and decoded in memory, as the full image already is (I4). The tiles are held in Glide's
 * memory, which the app clears at every lock (I3).
 */
class VaultSubsampling(
    private val link: VaultLink,
) : GlideSubsamplingImageGenerator {
    override suspend fun generateImage(
        context: Context,
        glide: Glide,
        model: Any,
        drawable: Drawable,
    ): SubsamplingImageGenerateResult? {
        // Any other model is ZoomImage's own to read, as before.
        val documentId = (model as? Uri)?.let(VaultUris::documentIdOf) ?: return null
        return SubsamplingImageGenerateResult.Success(SubsamplingImage(VaultImageSource(link, documentId).toFactory()))
    }

    override fun equals(other: Any?): Boolean = other is VaultSubsampling && other.link === link

    override fun hashCode(): Int = System.identityHashCode(link)

    override fun toString(): String = "VaultSubsampling"
}

/**
 * One vault document, as ZoomImage reads it: opened afresh through the link at every [openSource].
 *
 * Its [key] is made up, because ZoomImage keys its tiles by it and may log it, and nothing that names a document may
 * reach a log (encri D8, D85). So a photo opened again is a new source, and its tiles are decoded again.
 */
class VaultImageSource(
    private val link: VaultLink,
    private val documentId: String,
) : ImageSource {
    override val key: String = "encri-vault-source-${UUID.randomUUID()}"

    override fun openSource(): Source {
        val descriptor =
            when (val opened = runBlocking { link.ask { it.openRead(documentId) } }) {
                is EncriResult.Ok -> opened.value

                // The failure alone: the document stays unnamed.
                is EncriResult.Failed -> throw IOException("encri answered ${opened.failure}")
            }
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).source()
    }

    override fun equals(other: Any?): Boolean = other is VaultImageSource && other.key == key

    override fun hashCode(): Int = key.hashCode()

    override fun toString(): String = "VaultImageSource"
}
