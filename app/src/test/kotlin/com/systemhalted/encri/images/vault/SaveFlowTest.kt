/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.vault

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.systemhalted.encri.client.Encri
import com.systemhalted.encri.client.EncriGrant
import com.systemhalted.encri.client.EncriResult
import com.systemhalted.encri.client.Failure
import com.systemhalted.encri.client.Right
import com.systemhalted.encri.images.R
import com.systemhalted.encri.images.edit.Made
import com.systemhalted.encri.images.edit.TooLargeToEdit
import com.systemhalted.encri.images.models.Media
import com.systemhalted.encri.images.models.MediaType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast
import java.io.IOException
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SaveFlowTest {
    private val session = FakeVaultSession(tree = mapOf("root" to listOf(document("beach"))))
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val link = VaultLink({ Opening.Open(session) }, scope).apply { open() }
    private val library = VaultLibrary(link, scope)
    private val activity: Activity =
        Robolectric
            .buildActivity(AppCompatActivity::class.java)
            .apply { get().setTheme(R.style.Theme_Images_MediaViewer) }
            .setup()
            .get()
    private val asked = mutableListOf<Intent>()
    private val kept = mutableListOf<Uri>()
    private val progress = mutableListOf<Int>()
    private var made = 0
    private var released = 0
    private val bytes = byteArrayOf(7, 7, 7)

    /** What making the copy does: by default, makes one at once. */
    private var making: suspend () -> Made = {
        made++
        Made("beach (edited).jpg", "image/jpeg", { it.write(bytes) }, { released++ })
    }

    private val beach =
        Media(
            uri = VaultUris.document("beach"),
            mediaType = MediaType.IMAGE,
            mimeType = "image/jpeg",
            albumUri = VaultUris.folder("root"),
            albumName = null,
            displayName = "beach.jpg",
            dateAdded = Date(0),
            dateModified = Date(0),
            sizeBytes = 10,
        )

    private val copies = Copies(link, library)
    private val flow =
        SaveFlow(activity, beach, copies, VaultEditStore(link, copies), scope, asked::add, { report ->
            report(50)
            progress += 50
            making()
        }, kept::add)

    private fun grantOf(vararg rights: Right) =
        EncriResult.Ok(EncriGrant(rights.toSet(), folder("root", parentId = null)))

    private fun shown(): AlertDialog = ShadowDialog.getLatestDialog() as AlertDialog

    private fun title(): String = shown().findViewById<TextView>(androidx.appcompat.R.id.alertTitle)!!.text.toString()

    private fun isSecure(dialog: AlertDialog) =
        dialog.window!!.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun press(button: Int) {
        shown().getButton(button).performClick()
        idle()
    }

    private fun toast(): String? = ShadowToast.getTextOfLatestToast()

    @Test
    fun `with the rights, the copy is made, kept beside the original, and said`() {
        session.grant = grantOf(Right.READ, Right.CREATE, Right.WRITE)

        flow.offer()
        idle()

        assertEquals(listOf(VaultUris.document("made-1")), kept)
        assertArrayEquals(bytes, session.written["made-1"])
        assertEquals(listOf(Triple("root", "beach (edited).jpg", "image/jpeg")), session.created)
        assertEquals(activity.getString(R.string.edit_saved), toast())
        assertEquals(1, released)
        assertEquals(listOf(50), progress)
        assertEquals(activity.getString(R.string.edit_saving), title())
        assertTrue("the progress dialog is kept out of screenshots", isSecure(shown()))
        assertFalse("and gone once it is done", shown().isShowing)
    }

    @Test
    fun `without them, encri is asked for making and writing, and for every right held, before anything is made`() {
        session.grant = grantOf(Right.READ, Right.DELETE)

        flow.offer()
        idle()
        assertEquals(activity.getString(R.string.edit_allow_title), title())
        assertTrue(isSecure(shown()))
        press(AlertDialog.BUTTON_POSITIVE)

        assertEquals(
            setOf(Right.READ, Right.DELETE, Right.CREATE, Right.WRITE),
            Encri.authorizationResult(Activity.RESULT_OK, asked.single()),
        )
        assertEquals(0, made)
    }

    @Test
    fun `once encri allows it, the copy is made and kept`() {
        flow.offer()
        idle()
        press(AlertDialog.BUTTON_POSITIVE)

        session.grant = grantOf(Right.READ, Right.CREATE, Right.WRITE)
        flow.answered(Activity.RESULT_OK, Encri.authorizationIntent(setOf(Right.CREATE, Right.WRITE)))
        idle()

        assertEquals(listOf(VaultUris.document("made-1")), kept)
    }

    @Test
    fun `a consent given up on, allowing less, or with nothing waiting, goes no further`() {
        flow.answered(Activity.RESULT_OK, Encri.authorizationIntent(setOf(Right.CREATE, Right.WRITE)))
        flow.offer()
        idle()
        press(AlertDialog.BUTTON_POSITIVE)
        flow.answered(Activity.RESULT_CANCELED, null)
        flow.offer()
        idle()
        press(AlertDialog.BUTTON_POSITIVE)
        flow.answered(Activity.RESULT_OK, Encri.authorizationIntent(setOf(Right.CREATE)))
        idle()

        assertEquals(0, made)
        assertTrue(kept.isEmpty())
    }

    @Test
    fun `a grant encri does not tell, a copy too large or one that fails, is said, naming nothing`() {
        session.grant = EncriResult.Failed(Failure.FAILED, "no answer")
        flow.offer()
        idle()
        assertEquals(activity.getString(R.string.edit_failed), toast())

        session.grant = grantOf(Right.READ, Right.CREATE, Right.WRITE)
        making = { throw TooLargeToEdit() }
        flow.offer()
        idle()
        assertEquals(activity.getString(R.string.edit_too_large), toast())

        making = { throw IOException("decode") }
        flow.offer()
        idle()
        assertEquals(activity.getString(R.string.edit_failed), toast())

        assertTrue(kept.isEmpty())
        assertTrue(session.created.isEmpty())
    }

    @Test
    fun `a grant narrowed between asking and keeping asks encri again`() {
        session.grant = grantOf(Right.READ, Right.CREATE, Right.WRITE)
        session.create = EncriResult.Failed(Failure.NOT_AUTHORIZED, "narrowed")

        flow.offer()
        idle()

        assertEquals(activity.getString(R.string.edit_allow_title), title())
        assertEquals("what was made is let go", 1, released)
        assertTrue(kept.isEmpty())
    }

    @Test
    fun `Cancel stops the copy being made, and nothing is kept`() {
        session.grant = grantOf(Right.READ, Right.CREATE, Right.WRITE)
        val never = CompletableDeferred<Made>()
        making = { never.await() }

        flow.offer()
        idle()
        flow.offer()
        idle()
        assertEquals(activity.getString(R.string.edit_saving), title())
        press(AlertDialog.BUTTON_NEGATIVE)

        assertEquals("a second press while it saves shows nothing more", 1, ShadowDialog.getShownDialogs().size)
        assertTrue(kept.isEmpty())
        assertTrue(session.created.isEmpty())
        assertFalse(shown().isShowing)
    }
}
