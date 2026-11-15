/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.vault

import android.app.Activity
import android.app.Application
import android.content.Intent
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
import com.systemhalted.encri.client.VaultEvent
import com.systemhalted.encri.images.R
import com.systemhalted.encri.images.models.Media
import com.systemhalted.encri.images.models.RequestStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DeletesTest {
    private val tree =
        mapOf(
            "root" to
                listOf(document("photo", modified = 2), document("film", "video/mp4", modified = 1), folder("sub")),
            "sub" to listOf(document("other", modified = 3, parentId = "sub")),
        )

    private fun grantOf(vararg rights: Right) =
        EncriResult.Ok(EncriGrant(rights.toSet(), folder("root", parentId = null)))

    private class Rig(
        val session: FakeVaultSession,
        val link: VaultLink,
        val library: VaultLibrary,
        val deletes: Deletes,
    ) {
        fun library() = (library.state.value as RequestStatus.Success).data

        fun media(id: String): Media = library().medias.single { it.uri == VaultUris.document(id) }

        /** The library's documents, newest first. */
        fun ids(): List<String> = library().medias.map { VaultUris.documentIdOf(it.uri)!! }
    }

    /** A link and library over [sessions], one per session asked for, the last again once they run out. */
    private fun rig(
        scope: CoroutineScope,
        vararg sessions: FakeVaultSession = arrayOf(FakeVaultSession(tree = tree)),
    ): Rig {
        var opened = 0
        val link = VaultLink({ Opening.Open(sessions[minOf(opened++, sessions.lastIndex)]) }, scope)
        val library = VaultLibrary(link, scope)
        link.open()
        return Rig(sessions.first(), link, library, Deletes(link, library))
    }

    private fun TestScope.testRig(vararg sessions: FakeVaultSession = arrayOf(FakeVaultSession(tree = tree))) =
        rig(CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)), *sessions)

    // ---- Deletes ---------------------------------------------------------------------------------

    @Test
    fun `whether it may delete is asked of encri each time`() =
        runTest {
            val rig = testRig()

            rig.session.grant = grantOf(Right.READ)
            assertEquals(false, rig.deletes.allowed())
            rig.session.grant = grantOf(Right.READ, Right.DELETE)
            assertEquals(true, rig.deletes.allowed())
            rig.session.grant = EncriResult.Failed(Failure.FAILED, "no")
            assertNull(rig.deletes.allowed())
            assertEquals(3, rig.session.grantsAsked)
        }

    @Test
    fun `a delete leaves the library at once, and an album it empties goes with it`() =
        runTest {
            val rig = testRig()

            assertEquals(Deleted.GONE, rig.deletes.delete(rig.media("other")))

            assertEquals(listOf("other"), rig.session.deleted)
            assertEquals(listOf("photo", "film"), rig.ids())
            assertEquals(listOf(VaultUris.folder("root")), rig.library().albums.map { it.uri })
        }

    @Test
    fun `a walk that listed it before the delete does not bring it back`() =
        runTest {
            val rig = testRig()

            rig.deletes.delete(rig.media("photo"))
            // The fake's tree still holds it, as a walk that listed the folder first would.
            rig.library.reload()

            assertEquals(listOf("other", "film"), rig.ids())
        }

    @Test
    fun `one already gone is gone`() =
        runTest {
            val rig = testRig()
            rig.session.delete = EncriResult.Failed(Failure.NOT_FOUND, "gone")

            assertEquals(Deleted.GONE, rig.deletes.delete(rig.media("photo")))
            assertEquals(2, rig.library().medias.size)
        }

    @Test
    fun `a grant without the right, or a failure, deletes nothing here`() =
        runTest {
            val rig = testRig()

            rig.session.delete = EncriResult.Failed(Failure.NOT_AUTHORIZED, "no right")
            assertEquals(Deleted.NOT_ALLOWED, rig.deletes.delete(rig.media("photo")))
            rig.session.delete = EncriResult.Failed(Failure.FAILED, "broke")
            assertEquals(Deleted.FAILED, rig.deletes.delete(rig.media("photo")))

            assertEquals(3, rig.library().medias.size)
        }

    @Test
    fun `a delete that finds the vault locked is the lock`() =
        runTest {
            val rig = testRig()
            val photo = rig.media("photo")
            rig.session.delete = EncriResult.Failed(Failure.LOCKED, "locked")

            assertEquals(Deleted.FAILED, rig.deletes.delete(photo))
            assertEquals(LinkState.Open(rig.session, locked = true), rig.link.state.value)
        }

    @Test
    fun `what does not name a vault document is never asked`() =
        runTest {
            val rig = testRig()
            val elsewhere = rig.media("photo").copy(uri = VaultUris.folder("sub"))

            assertEquals(Deleted.FAILED, rig.deletes.delete(elsewhere))
            assertEquals(emptyList<String>(), rig.session.deleted)
        }

    @Test
    fun `after encri's consent screen, the session that replaces the old one is the one asked`() =
        runTest {
            val before = FakeVaultSession(tree = tree).apply { grant = grantOf(Right.READ) }
            val after = FakeVaultSession(tree = tree).apply { grant = grantOf(Right.READ, Right.DELETE) }
            val rig = testRig(before, after)

            val allowed = async { rig.deletes.awaitAllowed() }
            assertFalse("the old session does not allow it, so it waits", allowed.isCompleted)
            before.events.emit(VaultEvent.Ended)

            assertTrue(allowed.await())
            assertEquals(1, after.grantsAsked)
        }

    @Test
    fun `a grant that never allows it is given up on`() =
        runTest {
            val rig = testRig()
            rig.session.grant = grantOf(Right.READ)

            val allowed = async { rig.deletes.awaitAllowed() }
            advanceTimeBy(Deletes.ALLOWING_MILLIS + 1)

            assertFalse(allowed.await())
        }

    // ---- DeleteFlow ------------------------------------------------------------------------------

    private val activity: Activity =
        Robolectric
            .buildActivity(AppCompatActivity::class.java)
            .apply { get().setTheme(R.style.Theme_Images_MediaViewer) }
            .setup()
            .get()
    private val asked = mutableListOf<Intent>()
    private val gone = mutableListOf<Media>()

    private fun flowOf(rig: Rig) =
        DeleteFlow(activity, rig.deletes, CoroutineScope(Dispatchers.Unconfined), asked::add, gone::add)

    private fun uiRig() = rig(CoroutineScope(Dispatchers.Unconfined))

    private fun shown(): AlertDialog = ShadowDialog.getLatestDialog() as AlertDialog

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun press(button: Int) {
        shown().getButton(button).performClick()
        idle()
    }

    private fun title(): String = shown().findViewById<TextView>(androidx.appcompat.R.id.alertTitle)!!.text.toString()

    @Test
    fun `a delete is said first, in a secure dialog, and Cancel deletes nothing`() {
        val rig = uiRig()
        rig.session.grant = grantOf(Right.READ, Right.DELETE)

        flowOf(rig).offer(rig.media("photo"))
        idle()

        assertEquals(activity.getString(R.string.delete_photo_title), title())
        assertTrue(shown().window!!.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        press(AlertDialog.BUTTON_NEGATIVE)
        assertEquals(emptyList<String>(), rig.session.deleted)
    }

    @Test
    fun `Delete deletes it, and says so to the screen it came from`() {
        val rig = uiRig()
        rig.session.grant = grantOf(Right.READ, Right.DELETE)
        val film = rig.media("film")

        flowOf(rig).offer(film)
        idle()
        assertEquals(activity.getString(R.string.delete_video_title), title())
        press(AlertDialog.BUTTON_POSITIVE)

        assertEquals(listOf("film"), rig.session.deleted)
        assertEquals(listOf(film), gone)
    }

    @Test
    fun `without the right, encri's consent screen is asked for reading and deleting`() {
        val rig = uiRig()

        flowOf(rig).offer(rig.media("photo"))
        idle()
        assertEquals(activity.getString(R.string.delete_allow_title), title())
        press(AlertDialog.BUTTON_POSITIVE)

        val wanted = Encri.authorizationIntent(setOf(Right.READ, Right.DELETE))
        assertEquals(1, asked.size)
        assertTrue(wanted.filterEquals(asked.single()))
        assertEquals(wanted.extras!!.keySet(), asked.single().extras!!.keySet())
        // Every extra, whatever its type, which only the untyped get can compare.
        @Suppress("DEPRECATION")
        wanted.extras!!.keySet().forEach { assertEquals(wanted.extras!!.get(it), asked.single().extras!!.get(it)) }
        assertEquals(emptyList<String>(), rig.session.deleted)
    }

    @Test
    fun `the consent screen is asked for every right the grant holds as well, which it would replace (I22)`() {
        val rig = uiRig()
        rig.session.grant = grantOf(Right.READ, Right.CREATE, Right.WRITE)

        flowOf(rig).offer(rig.media("photo"))
        idle()
        press(AlertDialog.BUTTON_POSITIVE)

        assertEquals(
            setOf(Right.READ, Right.CREATE, Right.WRITE, Right.DELETE),
            Encri.authorizationResult(Activity.RESULT_OK, asked.single()),
        )
    }

    @Test
    fun `once encri allows it, the delete is said, and nothing is deleted before`() {
        val rig = uiRig()
        val flow = flowOf(rig)
        flow.offer(rig.media("photo"))
        idle()
        press(AlertDialog.BUTTON_POSITIVE)

        rig.session.grant = grantOf(Right.READ, Right.DELETE)
        flow.answered(Activity.RESULT_OK, consent(Right.READ, Right.DELETE))
        idle()

        assertEquals(activity.getString(R.string.delete_photo_title), title())
        assertEquals(emptyList<String>(), rig.session.deleted)
    }

    @Test
    fun `a consent screen given up on, or that allowed less, goes no further`() {
        val rig = uiRig()
        val flow = flowOf(rig)
        for (answer in listOf(Activity.RESULT_CANCELED to null, Activity.RESULT_OK to consent(Right.READ))) {
            flow.offer(rig.media("photo"))
            idle()
            press(AlertDialog.BUTTON_POSITIVE)

            flow.answered(answer.first, answer.second)
            idle()

            assertEquals(activity.getString(R.string.delete_allow_title), title())
        }
        assertEquals("asked once for each offer, and not after", 2, rig.session.grantsAsked)
    }

    @Test
    fun `an answer with nothing waiting on it is not a delete`() {
        val rig = uiRig()

        flowOf(rig).answered(Activity.RESULT_OK, consent(Right.READ, Right.DELETE))
        idle()

        assertNull(ShadowDialog.getLatestDialog())
        assertEquals(0, rig.session.grantsAsked)
    }

    @Test
    fun `a grant taken away between the question and the delete asks encri again`() {
        val rig = uiRig()
        rig.session.grant = grantOf(Right.READ, Right.DELETE)
        flowOf(rig).offer(rig.media("photo"))
        idle()

        rig.session.delete = EncriResult.Failed(Failure.NOT_AUTHORIZED, "narrowed")
        press(AlertDialog.BUTTON_POSITIVE)

        assertEquals(activity.getString(R.string.delete_allow_title), title())
        assertEquals(emptyList<Media>(), gone)
    }

    @Test
    fun `a delete that fails, or a grant encri does not tell, says so without naming it`() {
        val rig = uiRig()
        rig.session.grant = EncriResult.Failed(Failure.FAILED, "no")
        val flow = flowOf(rig)

        flow.offer(rig.media("photo"))
        idle()
        assertEquals(activity.getString(R.string.delete_failed), ShadowToast.getTextOfLatestToast())

        rig.session.grant = grantOf(Right.READ, Right.DELETE)
        rig.session.delete = EncriResult.Failed(Failure.FAILED, "broke")
        flow.offer(rig.media("photo"))
        idle()
        ShadowToast.reset()
        press(AlertDialog.BUTTON_POSITIVE)
        assertEquals(activity.getString(R.string.delete_failed), ShadowToast.getTextOfLatestToast())
        assertEquals(emptyList<Media>(), gone)
    }

    private fun message(): String = shown().findViewById<TextView>(android.R.id.message)!!.text.toString()

    @Test
    fun `several are said once, and Delete deletes them all (I16)`() {
        val rig = uiRig()
        rig.session.grant = grantOf(Right.READ, Right.DELETE)
        val several = listOf(rig.media("other"), rig.media("photo"), rig.media("film"))

        flowOf(rig).offer(several)
        idle()
        assertEquals(activity.resources.getQuantityString(R.plurals.delete_several_title, 3, 3), title())
        assertEquals(activity.getString(R.string.delete_several_explain), message())
        assertEquals(emptyList<String>(), rig.session.deleted)

        press(AlertDialog.BUTTON_POSITIVE)
        assertEquals(listOf("other", "photo", "film"), rig.session.deleted)
        assertEquals(several, gone)
        assertEquals(emptyList<String>(), rig.ids())
    }

    @Test
    fun `of several, those that fail are counted and the rest are deleted`() {
        val rig = uiRig()
        rig.session.grant = grantOf(Right.READ, Right.DELETE)
        rig.session.deleteFailures["film"] = Failure.FAILED

        flowOf(rig).offer(listOf(rig.media("photo"), rig.media("film")))
        idle()
        press(AlertDialog.BUTTON_POSITIVE)

        assertEquals("the one that failed stays, as does what was not chosen", listOf("other", "film"), rig.ids())
        assertEquals(listOf(VaultUris.document("photo")), gone.map(Media::uri))
        assertEquals(
            activity.resources.getQuantityString(R.plurals.delete_some_failed, 1, 1),
            ShadowToast.getTextOfLatestToast(),
        )
    }

    @Test
    fun `of several, a grant narrowed part way asks encri again for what is left`() {
        val rig = uiRig()
        rig.session.grant = grantOf(Right.READ, Right.DELETE)
        rig.session.deleteFailures["film"] = Failure.NOT_AUTHORIZED
        val flow = flowOf(rig)

        flow.offer(listOf(rig.media("photo"), rig.media("film")))
        idle()
        press(AlertDialog.BUTTON_POSITIVE)
        assertEquals(activity.getString(R.string.delete_allow_title), title())

        press(AlertDialog.BUTTON_POSITIVE)
        rig.session.deleteFailures.clear()
        flow.answered(Activity.RESULT_OK, consent(Right.READ, Right.DELETE))
        idle()
        assertEquals("only what is left is asked about", activity.getString(R.string.delete_video_title), title())
        press(AlertDialog.BUTTON_POSITIVE)
        assertEquals(listOf("photo", "film", "film"), rig.session.deleted)
        assertEquals(listOf("other"), rig.ids())
    }

    @Test
    fun `nothing chosen is nothing to delete`() {
        val rig = uiRig()
        flowOf(rig).offer(emptyList())
        idle()
        assertEquals(0, rig.session.grantsAsked)
    }

    /** encri's answer: the rights allowed, in the extra and the bits they were asked in. */
    private fun consent(vararg rights: Right): Intent =
        Intent().putExtras(Encri.authorizationIntent(rights.toSet()).extras!!)
}
