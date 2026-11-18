/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.vault

import android.app.Application
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.systemhalted.encri.client.EncriGrant
import com.systemhalted.encri.client.EncriResult
import com.systemhalted.encri.client.Right
import com.systemhalted.encri.images.edit.Made
import com.systemhalted.encri.images.edit.Saved
import com.systemhalted.encri.images.models.Media
import com.systemhalted.encri.images.models.MediaType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.Date

/** The editor's store in the vault (I23): it reads through the link and keeps through [Copies]. */
@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class VaultEditStoreTest {
    private val session = FakeVaultSession(tree = mapOf("root" to listOf(document("beach"))))
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val link = VaultLink({ Opening.Open(session) }, scope).apply { open() }
    private val store = VaultEditStore(link, Copies(link, VaultLibrary(link, scope)))
    private val photo = File.createTempFile("beach", ".jpg")

    @After
    fun tearDown() {
        photo.delete()
    }

    private fun media(id: String = "beach") =
        Media(
            uri = VaultUris.document(id),
            mediaType = MediaType.IMAGE,
            mimeType = "image/jpeg",
            albumUri = VaultUris.folder("root"),
            albumName = null,
            displayName = "$id.jpg",
            dateAdded = Date(0),
            dateModified = Date(0),
            sizeBytes = 3,
        )

    @Test
    fun `a photo is read through the link, a new descriptor each time`() {
        photo.writeBytes(byteArrayOf(1, 2, 3))
        session.content["beach"] = photo
        repeat(2) {
            val read = runBlocking { store.open(media()) }.use { FileInputStream(it.fileDescriptor).readBytes() }
            assertArrayEquals(byteArrayOf(1, 2, 3), read)
        }
        assertEquals(listOf("beach", "beach"), session.opened)
        assertThrows(IOException::class.java) { runBlocking { store.open(media("nowhere")) } }
    }

    @Test
    fun `a video is read from the vault and nowhere else`() {
        assertTrue(store.videos.createDataSource() is VaultMediaSource)
    }

    @Test
    fun `a copy is kept beside the original, by Copies`() {
        session.grant = EncriResult.Ok(EncriGrant(setOf(Right.READ, Right.CREATE, Right.WRITE), session.root))
        val made = Made("beach (edited).jpg", "image/jpeg", { it.write(byteArrayOf(9)) }, {})
        assertEquals(Saved.Kept(VaultUris.document("made-1")), runBlocking { store.keep(media(), made) })
        assertEquals(listOf(Triple("root", "beach (edited).jpg", "image/jpeg")), session.created)
        assertArrayEquals(byteArrayOf(9), session.written["made-1"])
    }
}
