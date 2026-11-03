/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.vault

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Option
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.engine.cache.DiskCache
import com.bumptech.glide.load.engine.cache.DiskCacheAdapter
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey
import com.systemhalted.encri.client.EncriResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Glide, with nowhere to write a picture and a way to read one from the vault (AGENTS.md I3).
 *
 * Glide keeps a disk cache by default, which here would hold plaintext pictures of the vault outside it
 * (encri D112). [DiskCacheAdapter] is its no-op: nothing is written, and the directory is never made.
 */
object VaultGlide {
    /** Ask encri for its preview, rather than reading the document and decoding it here. */
    val PREVIEW: Option<Boolean> = Option.memory("com.systemhalted.encri.images.vault.PREVIEW", false)

    /** Must run before anything else touches Glide, whose configuration is fixed on first use. */
    fun install(
        context: Context,
        link: VaultLink,
    ) {
        Glide.init(context, GlideBuilder().setDiskCache(DiskCache.Factory { DiskCacheAdapter() }))
        Glide
            .get(context)
            .registry
            .prepend(Uri::class.java, InputStream::class.java, VaultModelLoader.Factory(link))
    }

    /** Forgets every picture held in memory. Called on the main thread when the vault locks. */
    fun forget(context: Context) = Glide.get(context).clearMemory()
}

/** Reads a document named by [VaultUris]; any other [Uri] is not this loader's. */
class VaultModelLoader(
    private val link: VaultLink,
) : ModelLoader<Uri, InputStream> {
    override fun handles(model: Uri): Boolean = VaultUris.documentIdOf(model) != null

    override fun buildLoadData(
        model: Uri,
        width: Int,
        height: Int,
        options: Options,
    ): ModelLoader.LoadData<InputStream>? =
        VaultUris.documentIdOf(model)?.let { documentId ->
            val preview = options.get(VaultGlide.PREVIEW) == true
            ModelLoader.LoadData(ObjectKey(model), VaultFetcher(link, documentId, preview, width, height))
        }

    class Factory(
        private val link: VaultLink,
    ) : ModelLoaderFactory<Uri, InputStream> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<Uri, InputStream> =
            VaultModelLoader(link)

        override fun teardown() = Unit
    }
}

/**
 * One document's bytes for one load, on the Glide thread that asks. Blocking is what a [DataFetcher] is for:
 * [loadData] runs off the main thread, and [cancel] ends the request in encri as well.
 */
class VaultFetcher(
    private val link: VaultLink,
    private val documentId: String,
    private val preview: Boolean,
    private val width: Int,
    private val height: Int,
) : DataFetcher<InputStream> {
    @Volatile
    private var asking: Job? = null
    private var stream: InputStream? = null

    override fun loadData(
        priority: Priority,
        callback: DataFetcher.DataCallback<in InputStream>,
    ) {
        val answer =
            runCatching {
                runBlocking {
                    asking = coroutineContext.job
                    if (preview) previewStream() else documentStream()
                }
            }
        answer
            .onSuccess { result ->
                when (result) {
                    is EncriResult.Ok -> {
                        stream = result.value
                        callback.onDataReady(result.value)
                    }

                    // The failure alone: nothing that names the document reaches a log (encri D8, D85).
                    is EncriResult.Failed -> {
                        callback.onLoadFailed(IOException("encri answered ${result.failure}"))
                    }
                }
            }.onFailure { callback.onLoadFailed(IOException("the read was cancelled")) }
    }

    private suspend fun previewStream(): EncriResult<InputStream> =
        when (val made = link.ask { it.thumbnail(documentId, width, height) }) {
            is EncriResult.Ok -> EncriResult.Ok(ByteArrayInputStream(made.value))
            is EncriResult.Failed -> made
        }

    private suspend fun documentStream(): EncriResult<InputStream> =
        when (val opened = link.ask { it.openRead(documentId) }) {
            is EncriResult.Ok -> EncriResult.Ok(ParcelFileDescriptor.AutoCloseInputStream(opened.value))
            is EncriResult.Failed -> opened
        }

    override fun cleanup() {
        runCatching { stream?.close() }
        stream = null
    }

    override fun cancel() {
        asking?.cancel()
    }

    override fun getDataClass(): Class<InputStream> = InputStream::class.java

    // Not REMOTE: Glide writes only what is remote to its data cache, and this must never be a candidate.
    override fun getDataSource(): DataSource = DataSource.LOCAL
}
