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
import com.systemhalted.encri.images.models.Thumbnail
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
import org.robolectric.shadows.ShadowLog
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
    fun `with previews off, a preview is not asked for at all, and a full image still is`() {
        val loader = VaultModelLoader(link) { false }
        val preview = Options().set(VaultGlide.PREVIEW, true)
        assertNull(loader.buildLoadData(VaultUris.document("photo"), 64, 48, preview))
        assertNotNull(loader.buildLoadData(VaultUris.document("photo"), 64, 48, Options()))
        assertEquals(emptyList<Any>(), session.thumbnailsAsked)
    }

    @Test(timeout = 30_000)
    fun `with previews off, a grid's load through Glide fails before encri hears of it`() {
        VaultGlide.install(context, link) { false }
        val loading =
            Glide
                .with(context)
                .asBitmap()
                .load(VaultUris.document("photo"))
                .set(VaultGlide.PREVIEW, true)
                .override(4, 4)
                .submit()
        while (!loading.isDone) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        assertTrue("the load did not fail", runCatching { loading.get() }.isFailure)
        assertEquals(emptyList<Any>(), session.thumbnailsAsked)
    }

    @Test(timeout = 30_000)
    fun `with previews turned off, one Glide already holds is not shown either`() {
        var shown = true
        VaultGlide.install(context, link) { shown }
        session.thumbnail = EncriResult.Ok(png())
        val cell = {
            Glide
                .with(context)
                .asBitmap()
                .load(VaultUris.document("photo"))
                .apply(previewOptions())
                .submit()
        }
        val first = cell()
        while (!first.isDone) idle()
        assertNotNull("drawn with previews on, and held in memory", first.get())

        shown = false
        val again = cell()
        while (!again.isDone) idle()
        assertTrue("shown from memory after previews went off", runCatching { again.get() }.isFailure)
        assertEquals(1, session.thumbnailsAsked.size)
    }

    private fun previewOptions() = Thumbnail.previewOptions().override(4, 4)

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(5)
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

    @Test(timeout = 30_000)
    fun `a load that fails is not logged with the document it was for`() {
        VaultGlide.install(context, link)
        session.thumbnail = EncriResult.Failed(Failure.UNSUPPORTED, "cannot render")
        ShadowLog.clear()
        val loading =
            Glide
                .with(context)
                .asBitmap()
                .load(VaultUris.document("a-document-id"))
                .set(VaultGlide.PREVIEW, true)
                .override(4, 4)
                .submit()
        while (!loading.isDone) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        assertTrue("the load did not fail", runCatching { loading.get() }.isFailure)
        val named = ShadowLog.getLogs().filter { "a-document-id" in it.msg.orEmpty() }
        assertEquals(emptyList<String>(), named.map { "${it.tag}: ${it.msg}" })
    }

    private fun filesUnder(dir: File): Set<String> = dir.walkTopDown().map(File::getPath).toSet()

    private fun png(): ByteArray =
        ByteArrayOutputStream()
            .also { Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
            .toByteArray()
}
