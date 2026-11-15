/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.edit

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
import android.view.View
import androidx.activity.result.ActivityResult
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.ChipGroup
import com.systemhalted.encri.client.EncriGrant
import com.systemhalted.encri.client.EncriResult
import com.systemhalted.encri.client.Right
import com.systemhalted.encri.client.VaultEvent
import com.systemhalted.encri.images.R
import com.systemhalted.encri.images.models.Media
import com.systemhalted.encri.images.models.MediaType
import com.systemhalted.encri.images.vault.FakeVaultSession
import com.systemhalted.encri.images.vault.Opening
import com.systemhalted.encri.images.vault.VaultHost
import com.systemhalted.encri.images.vault.VaultLibrary
import com.systemhalted.encri.images.vault.VaultLink
import com.systemhalted.encri.images.vault.VaultUris
import com.systemhalted.encri.images.vault.document
import com.systemhalted.encri.images.vault.folder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast
import java.io.File
import java.util.Date

/** An application whose one link reaches a stand-in encri, as `ImagesApplication`'s reaches the real one. */
class EditorHost :
    Application(),
    VaultHost {
    val session = FakeVaultSession(tree = mapOf("root" to listOf(document("beach", "image/png"))))
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    override val link = VaultLink({ Opening.Open(session) }, scope).apply { open() }
    override val library = VaultLibrary(link, scope)
}

@RunWith(RobolectricTestRunner::class)
@Config(application = EditorHost::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditActivityTest {
    private val host: EditorHost = ApplicationProvider.getApplicationContext()
    private val photo = File.createTempFile("beach", ".png")

    @After
    fun tearDown() {
        photo.delete()
    }

    private fun media(
        id: String = "beach",
        type: MediaType = MediaType.IMAGE,
        mimeType: String = "image/png",
    ) = Media(
        uri = VaultUris.document(id),
        mediaType = type,
        mimeType = mimeType,
        albumUri = VaultUris.folder("root"),
        albumName = null,
        displayName = "$id.png",
        dateAdded = Date(0),
        dateModified = Date(0),
        sizeBytes = photo.length(),
    )

    /** A photo 40 by 30, red on its left half and blue on its right, in the stand-in vault. */
    private fun inTheVault() {
        val bitmap = Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.RED)
            clipRect(20, 0, 40, 30)
            drawColor(Color.BLUE)
        }
        photo.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        host.session.content["beach"] = photo
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /** Idles the main thread until [done], or for at most five seconds, for what is read off it. */
    private fun until(done: () -> Boolean) {
        val give = System.currentTimeMillis() + 5_000
        idle()
        while (!done() && System.currentTimeMillis() < give) {
            Thread.sleep(10)
            idle()
        }
    }

    private fun open(media: Media? = media()): ActivityController<EditActivity> {
        val intent =
            media?.let { EditActivity.intentFor(host, it) } ?: android.content.Intent(host, EditActivity::class.java)
        return Robolectric.buildActivity(EditActivity::class.java, intent).setup()
    }

    private fun EditActivity.crop(): CropView = findViewById(R.id.cropView)

    private fun EditActivity.save() = findViewById<MaterialToolbar>(R.id.toolbar).menu.findItem(R.id.save)

    private fun EditActivity.shown(): EditActivity {
        until { crop().edit != null }
        return this
    }

    @Test
    fun `a photo is shown under the frame, with nothing to save until something is asked`() {
        inTheVault()
        val editor = open().get().shown()
        assertEquals(Edit(Size(40f, 30f)), editor.crop().edit)
        assertFalse(editor.findViewById<View>(R.id.loading).isShown)
        assertFalse(editor.save().isEnabled)
        assertFalse(editor.findViewById<View>(R.id.resetButton).isEnabled)

        editor.findViewById<View>(R.id.rotateButton).performClick()
        assertEquals(1, editor.crop().edit!!.turns)
        assertTrue(editor.save().isEnabled)

        editor.findViewById<View>(R.id.resetButton).performClick()
        assertEquals(Edit(Size(40f, 30f)), editor.crop().edit)
        assertFalse(editor.save().isEnabled)
    }

    @Test
    fun `a shape chosen holds the frame, and a turn chooses its other way round`() {
        inTheVault()
        val editor = open().get().shown()
        val aspects = editor.findViewById<ChipGroup>(R.id.aspects)
        aspects.check(R.id.aspect4by3)
        assertEquals(Aspect.FOUR_BY_THREE, editor.crop().edit!!.aspect)
        editor.findViewById<View>(R.id.rotateButton).performClick()
        assertEquals(Aspect.THREE_BY_FOUR, editor.crop().edit!!.aspect)
        assertEquals(R.id.aspect3by4, aspects.checkedChipId)
        aspects.check(R.id.aspectSquare)
        val square = editor.crop().edit!!.crop
        assertEquals(square.width, square.height, 0.01f)
    }

    @Test
    fun `Save a copy keeps the photo cropped and turned beside it, and answers with the copy`() {
        inTheVault()
        host.session.grant =
            EncriResult.Ok(EncriGrant(setOf(Right.READ, Right.CREATE, Right.WRITE), folder("root", parentId = null)))
        val controller = open()
        val editor = controller.get().shown()
        editor.crop().change(
            editor
                .crop()
                .edit!!
                .copy(crop = Box(0f, 0f, 20f, 30f))
                .turnedClockwise(),
        )

        assertTrue(editor.save().isEnabled)
        editor.findViewById<MaterialToolbar>(R.id.toolbar).menu.performIdentifierAction(R.id.save, 0)
        until { editor.isFinishing }

        assertEquals(listOf(Triple("root", "beach (edited).png", "image/png")), host.session.created)
        val written = host.session.written.getValue("made-1")
        val copy = BitmapFactory.decodeByteArray(written, 0, written.size)
        assertEquals(30, copy.width)
        assertEquals(20, copy.height)
        assertEquals(Color.RED, copy.getPixel(15, 10))
        val result = shadowOf(editor)
        assertEquals(Activity.RESULT_OK, result.resultCode)
        assertEquals(
            VaultUris.document("made-1"),
            EditActivity.savedIn(ActivityResult(result.resultCode, result.resultIntent)),
        )
        assertEquals(editor.getString(R.string.edit_saved), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `a lock closes the editor`() {
        inTheVault()
        val editor = open().get().shown()
        runBlocking { host.session.events.emit(VaultEvent.Locked) }
        idle()
        assertTrue(editor.isFinishing)
    }

    @Test
    fun `what cannot be opened is said, and the editor closes`() {
        val editor = open().get()
        until { editor.isFinishing }
        assertTrue(editor.isFinishing)
        assertEquals(editor.getString(R.string.edit_cannot_open), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `nothing to edit, or a GIF, closes it at once`() {
        assertTrue(open(media = null).get().isFinishing)
        assertTrue(open(media(mimeType = "image/gif")).get().isFinishing)
    }

    @Test
    fun `a photo or video can be edited, and a GIF cannot`() {
        assertTrue(EditActivity.canEdit(media()))
        assertTrue(EditActivity.canEdit(media(type = MediaType.VIDEO, mimeType = "video/mp4")))
        assertFalse(EditActivity.canEdit(media(mimeType = "IMAGE/GIF")))
        assertEquals(null, EditActivity.savedIn(ActivityResult(Activity.RESULT_CANCELED, null)))
        assertNotNull(EditActivity.intentFor(host, media()).extras)
    }
}
