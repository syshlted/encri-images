/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.vault

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Looper
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.systemhalted.encri.client.Encri
import com.systemhalted.encri.client.EncriResult
import com.systemhalted.encri.client.Failure
import com.systemhalted.encri.images.R
import com.systemhalted.encri.images.models.Media
import com.systemhalted.encri.images.models.MediaType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class HandoffsTest {
    private val session = FakeVaultSession()
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val link = VaultLink({ Opening.Open(session) }, scope).apply { open() }
    private val activity =
        Robolectric
            .buildActivity(AppCompatActivity::class.java)
            .apply { get().setTheme(R.style.Theme_Images_MediaViewer) }
            .setup()
            .get()
    private val handoffs = Handoffs(activity, link, scope)

    private val photo =
        Media(
            uri = VaultUris.document("p1"),
            mediaType = MediaType.IMAGE,
            mimeType = "image/jpeg",
            albumUri = VaultUris.folder("root"),
            albumName = null,
            displayName = "p1.jpg",
            dateAdded = Date(0),
            dateModified = Date(0),
            sizeBytes = 10,
        )

    private fun shown(): AlertDialog = ShadowDialog.getLatestDialog() as AlertDialog

    private fun press(button: Int) {
        shown().getButton(button).performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun started(): Intent? = shadowOf(activity).nextStartedActivity

    /** An activity of [packageName] that takes [action] for any image. */
    private fun taker(
        packageName: String,
        action: String,
    ): ComponentName {
        val component = ComponentName(packageName, "$packageName.Take")
        val packages = shadowOf(activity.packageManager)
        packages.addActivityIfNotPresent(component)
        packages.addIntentFilterForActivity(
            component,
            IntentFilter(action).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addDataType("image/*")
            },
        )
        return component
    }

    @Test
    fun `nothing is handed over until the user agrees, and not at all when they do not`() {
        handoffs.offer(photo, Handoff.SHARE)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(shown().isShowing)
        assertTrue(
            "the dialog is kept out of screenshots",
            shown().window!!.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0,
        )
        assertEquals(emptyList<List<String>>(), session.handedOff)

        press(AlertDialog.BUTTON_NEGATIVE)

        assertEquals(emptyList<List<String>>(), session.handedOff)
        assertNull(started())
    }

    @Test
    fun `a share sends encri's handoff through the share sheet, with the grant in the stream and the clip`() {
        handoffs.offer(photo, Handoff.SHARE)
        shadowOf(Looper.getMainLooper()).idle()
        press(AlertDialog.BUTTON_POSITIVE)

        assertEquals(listOf(listOf("p1")), session.handedOff)
        val chooser = started()!!
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val target = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
        val handed = Uri.parse("content://handoff.test/p1")
        assertEquals(Intent.ACTION_SEND, target.action)
        assertEquals("image/jpeg", target.type)
        assertEquals(handed, target.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java))
        assertEquals(handed, target.clipData!!.getItemAt(0).uri)
        assertEquals("", target.clipData!!.description.label)
        assertTrue(target.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertNull("the share sheet names itself", chooser.getCharSequenceExtra(Intent.EXTRA_TITLE))
    }

    private val film =
        photo.copy(uri = VaultUris.document("v1"), mediaType = MediaType.VIDEO, mimeType = "video/mp4")

    private fun messageShown(): String = shown().findViewById<TextView>(android.R.id.message)!!.text.toString()

    @Test
    fun `several are shared through one handoff, each in the stream and the clip (I16)`() {
        val second = photo.copy(uri = VaultUris.document("p2"))
        handoffs.offer(listOf(photo, second), Handoff.SHARE)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(activity.getString(R.string.handoff_share_several_explain), messageShown())
        press(AlertDialog.BUTTON_POSITIVE)

        assertEquals(listOf(listOf("p1", "p2")), session.handedOff)
        val target = started()!!.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
        val handed = listOf(Uri.parse("content://handoff.test/p1"), Uri.parse("content://handoff.test/p2"))
        assertEquals(Intent.ACTION_SEND_MULTIPLE, target.action)
        assertEquals("image/jpeg", target.type)
        assertEquals(handed, target.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java))
        assertEquals(handed, List(target.clipData!!.itemCount) { target.clipData!!.getItemAt(it).uri })
        assertTrue(target.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun `several share as the type they have in common, or as any`() {
        assertEquals("image/jpeg", Handoffs.mimeTypeOf(listOf(photo, photo)))
        assertEquals("image/*", Handoffs.mimeTypeOf(listOf(photo, photo.copy(mimeType = "image/png"))))
        assertEquals("*/*", Handoffs.mimeTypeOf(listOf(photo, film)))
    }

    @Test
    fun `a handoff that comes back short opens nothing`() {
        session.handOff = EncriResult.Ok(listOf(Uri.parse("content://handoff.test/p1")))
        handoffs.offer(listOf(photo, film), Handoff.SHARE)
        shadowOf(Looper.getMainLooper()).idle()
        press(AlertDialog.BUTTON_POSITIVE)

        assertNull(started())
        assertEquals(activity.getString(R.string.handoff_failed), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `only a share takes several, since a view is of one`() {
        assertThrows(IllegalArgumentException::class.java) { handoffs.offer(listOf(photo, film), Handoff.OPEN_WITH) }
        handoffs.offer(emptyList(), Handoff.SHARE)
        assertNull(ShadowDialog.getLatestDialog())
    }

    @Test
    fun `open with views the handoff, in a chooser of its own`() {
        handoffs.offer(photo, Handoff.OPEN_WITH)
        shadowOf(Looper.getMainLooper()).idle()
        press(AlertDialog.BUTTON_POSITIVE)

        val chooser = started()!!
        val target = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
        assertEquals(Intent.ACTION_VIEW, target.action)
        assertEquals(Uri.parse("content://handoff.test/p1"), target.data)
        assertEquals("image/jpeg", target.type)
        assertTrue(target.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(
            activity.getString(R.string.file_action_open_with),
            chooser.getCharSequenceExtra(Intent.EXTRA_TITLE),
        )
    }

    @Test
    fun `encri is left out of the chooser, and nothing else is`() {
        val vault = taker(Encri.PACKAGE, Intent.ACTION_SEND)
        taker("org.example.gallery", Intent.ACTION_SEND)
        val viewer = taker(Encri.PACKAGE, Intent.ACTION_VIEW)

        val shared = handoffs.chooserFor(Handoff.SHARE, Uri.parse("content://handoff.test/p1"), "image/jpeg")
        val opened = handoffs.chooserFor(Handoff.OPEN_WITH, Uri.parse("content://handoff.test/p1"), "image/jpeg")

        assertEquals(
            listOf(vault),
            shared.getParcelableArrayExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, ComponentName::class.java)!!.toList(),
        )
        assertEquals(
            listOf(viewer),
            opened.getParcelableArrayExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, ComponentName::class.java)!!.toList(),
        )
    }

    @Test
    fun `with no encri to take it, nothing is left out`() {
        taker("org.example.gallery", Intent.ACTION_SEND)

        val shared = handoffs.chooserFor(Handoff.SHARE, Uri.parse("content://handoff.test/p1"), "image/jpeg")

        assertNull(shared.getParcelableArrayExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, ComponentName::class.java))
    }

    @Test
    fun `a handoff encri refuses opens nothing, and says so without naming the photo`() {
        session.handOff = EncriResult.Failed(Failure.NOT_FOUND, "gone")

        handoffs.offer(photo, Handoff.SHARE)
        shadowOf(Looper.getMainLooper()).idle()
        press(AlertDialog.BUTTON_POSITIVE)

        assertNull(started())
        assertEquals(activity.getString(R.string.handoff_failed), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `a handoff that finds the vault locked is the lock`() {
        session.handOff = EncriResult.Failed(Failure.LOCKED, "locked")

        handoffs.offer(photo, Handoff.SHARE)
        shadowOf(Looper.getMainLooper()).idle()
        press(AlertDialog.BUTTON_POSITIVE)

        assertNull(started())
        assertEquals(LinkState.Open(session, locked = true), link.state.value)
    }

    @Test
    fun `a phone with nothing to take it says so`() {
        shadowOf(activity.application).checkActivities(true)

        handoffs.offer(photo, Handoff.SHARE)
        shadowOf(Looper.getMainLooper()).idle()
        press(AlertDialog.BUTTON_POSITIVE)

        assertEquals(activity.getString(R.string.handoff_no_app), ShadowToast.getTextOfLatestToast())
    }
}
