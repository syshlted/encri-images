/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.vault

import android.app.Application
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.bumptech.glide.Glide
import com.github.panpf.zoomimage.subsampling.ImageSource
import com.github.panpf.zoomimage.subsampling.SubsamplingImageGenerateResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okio.buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class VaultSubsamplingTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val bytes = ByteArray(300) { it.toByte() }
    private val file = File.createTempFile("photo", null).apply { writeBytes(bytes) }
    private val session = FakeVaultSession().apply { content["a-photo-id"] = file }
    private val link = VaultLink({ Opening.Open(session) }, CoroutineScope(Dispatchers.Unconfined)).apply { open() }
    private val generator = VaultSubsampling(link)

    @After
    fun tearDown() {
        file.delete()
        Glide.tearDown()
    }

    private fun generated(model: Any): SubsamplingImageGenerateResult? =
        runBlocking { generator.generateImage(context, Glide.get(context), model, ColorDrawable()) }

    private fun sourceOf(model: Any): ImageSource {
        val image = (generated(model) as SubsamplingImageGenerateResult.Success).subsamplingImage
        return runBlocking { image.imageSource.create() }
    }

    @Test
    fun `a model that is not the vault's is left to ZoomImage`() {
        assertNull(generated(Uri.parse("content://media/1")))
        assertNull(generated(VaultUris.folder("a-folder")))
        assertNull(generated("a string"))
    }

    @Test
    fun `a vault photo's source is read through encri, afresh each time it is opened`() {
        val source = sourceOf(VaultUris.document("a-photo-id"))
        assertArrayEquals(bytes, source.openSource().buffer().use { it.readByteArray() })
        assertArrayEquals(bytes, source.openSource().buffer().use { it.readByteArray() })
        assertEquals(listOf("a-photo-id", "a-photo-id"), session.opened)
    }

    @Test
    fun `its key, which ZoomImage keys tiles by and may log, names no document`() {
        val source = sourceOf(VaultUris.document("a-photo-id"))
        assertFalse(source.key.contains("a-photo-id"))
        assertNotEquals(
            "each opening is a source of its own",
            source.key,
            sourceOf(VaultUris.document("a-photo-id")).key,
        )
    }

    @Test
    fun `a photo encri will not open fails, and names nothing`() {
        val source = sourceOf(VaultUris.document("a-missing-id"))
        val thrown = assertThrows(IOException::class.java) { source.openSource() }
        assertFalse(thrown.message!!.contains("a-missing-id"))
        assertTrue(thrown.message!!.contains("NOT_FOUND"))
    }

    @Test
    fun `a generator is the same as another over the same link, which ZoomImage compares`() {
        assertEquals(VaultSubsampling(link), generator)
        assertEquals(VaultSubsampling(link).hashCode(), generator.hashCode())
        assertNotEquals(
            VaultSubsampling(VaultLink({ Opening.Locked }, CoroutineScope(Dispatchers.Unconfined))),
            generator,
        )
    }
}
