/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.vault

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.systemhalted.encri.client.EncriResult
import com.systemhalted.encri.client.Failure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class VaultGlideTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val session = FakeVaultSession()
    private val link =
        VaultLink({ Opening.Open(session) }, CoroutineScope(Dispatchers.Unconfined)).apply { open() }

    @After
    fun tearDown() {
        Glide.tearDown()
    }

    private class Caught : DataFetcher.DataCallback<InputStream> {
        var data: InputStream? = null
        var failure: Exception? = null

        override fun onDataReady(data: InputStream?) {
            this.data = data
        }

        override fun onLoadFailed(e: Exception) {
            failure = e
        }
    }

    private fun fetch(
        documentId: String,
        preview: Boolean,
    ): Caught {
        val options = Options().set(VaultGlide.PREVIEW, preview)
        val fetcher = VaultModelLoader(link).buildLoadData(VaultUris.document(documentId), 64, 48, options)!!.fetcher
        return Caught().also { fetcher.loadData(Priority.NORMAL, it) }
    }

    @Test
    fun `only a document in the vault is this loader's`() {
        val loader = VaultModelLoader(link)
        assertTrue(loader.handles(VaultUris.document("d")))
        assertFalse(loader.handles(VaultUris.folder("f")))
        assertFalse(loader.handles(Uri.parse("content://media/1")))
        assertNull(loader.buildLoadData(VaultUris.folder("f"), 1, 1, Options()))
    }

    @Test
    fun `a preview is encri's, at the size asked, and never reads the document`() {
        val caught = fetch("photo", preview = true)
        assertArrayEquals(byteArrayOf(1, 2, 3), caught.data!!.readBytes())
        assertEquals(listOf(Triple("photo", 64, 48)), session.thumbnailsAsked)
        assertEquals(emptyList<String>(), session.opened)
    }

    @Test
    fun `a full image reads the document, and asks for no preview`() {
        val file = File.createTempFile("photo", null).apply { writeBytes(byteArrayOf(9, 8, 7)) }
        session.content["photo"] = file
        val caught = fetch("photo", preview = false)
        assertArrayEquals(byteArrayOf(9, 8, 7), caught.data!!.use { it.readBytes() })
        assertEquals(emptyList<Any>(), session.thumbnailsAsked)
        file.delete()
    }

    @Test
    fun `a failure says what failed and not which document`() {
        session.thumbnail = EncriResult.Failed(Failure.UNSUPPORTED, "cannot render")
        val failure = fetch("secret-holiday", preview = true).failure
        assertNotNull(failure)
        assertTrue(failure!!.message!!.contains("UNSUPPORTED"))
        assertFalse(failure.message!!.contains("secret-holiday"))
    }

    @Test
    fun `a read that fails is a failure, not an empty picture`() {
        val caught = fetch("missing", preview = false)
        assertNull(caught.data)
        assertNotNull(caught.failure)
    }

    @Test
    fun `what the vault gives is never a candidate for a data cache`() {
        val fetcher = VaultFetcher(link, "d", preview = false, width = 1, height = 1)
        assertEquals(DataSource.LOCAL, fetcher.dataSource)
        assertEquals(InputStream::class.java, fetcher.dataClass)
        fetcher.cancel()
        fetcher.cleanup()
    }

    @Test(timeout = 30_000)
    fun `a picture loaded through Glide leaves nothing on disk`() {
        val before = filesUnder(context.cacheDir) + filesUnder(context.filesDir)
        VaultGlide.install(context, link)
        session.thumbnail = EncriResult.Ok(png())
        val loading =
            Glide
                .with(context)
                .asBitmap()
                .load(VaultUris.document("photo"))
                .set(VaultGlide.PREVIEW, true)
                .override(4, 4)
                .submit()
        // Glide answers on the main looper, which is this thread under Robolectric.
        while (!loading.isDone) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        assertNotNull(loading.get())
        assertEquals(listOf(Triple("photo", 4, 4)), session.thumbnailsAsked)
        assertEquals(before, filesUnder(context.cacheDir) + filesUnder(context.filesDir))
    }

    private fun filesUnder(dir: File): Set<String> = dir.walkTopDown().map(File::getPath).toSet()

    private fun png(): ByteArray =
        ByteArrayOutputStream()
            .also { Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
            .toByteArray()
}
