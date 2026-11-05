/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.notices

import android.app.Application
import android.content.Intent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.appbar.MaterialToolbar
import com.systemhalted.encri.images.R
import com.systemhalted.encri.images.SettingsActivity
import com.systemhalted.encri.images.vault.SecureWindows
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The notices screen (I19): it shows the whole asset, as [noticeText] draws it, in a window kept out of screenshots
 * as every window here is (I13), opened from a row of the settings.
 *
 * Whether the asset names everything the APK carries is `:app:checkThirdPartyNotices`'s to say, from the resolved
 * build. That no other app can open it is `ShippedManifestTest`'s.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class NoticesActivityTest {
    private val application: Application = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        application.registerActivityLifecycleCallbacks(SecureWindows)
    }

    @After
    fun tearDown() {
        application.unregisterActivityLifecycleCallbacks(SecureWindows)
    }

    @Test
    fun `the screen shows the whole asset, to the last line of the Apache License`() {
        val activity = Robolectric.buildActivity(NoticesActivity::class.java).setup().get()
        val asset =
            application.assets
                .open(NOTICES_ASSET)
                .bufferedReader()
                .use { it.readText() }

        val shown = activity.findViewById<TextView>(R.id.notices).text.toString()

        assertEquals(noticeText(asset).toString(), shown)
        assertTrue("the notices stop before the licence they point to", "END OF TERMS AND CONDITIONS" in shown)
        assertFalse("a heading's mark reached the screen", shown.lines().any { it.startsWith("# ") })
        assertTrue(activity.findViewById<TextView>(R.id.notices).isTextSelectable)
    }

    @Test
    fun `it is kept out of screenshots, and up leaves it`() {
        val activity = Robolectric.buildActivity(NoticesActivity::class.java).setup().get()
        assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)

        val toolbar = activity.findViewById<MaterialToolbar>(R.id.toolbar)
        val up = ArrayList<View>()
        toolbar.findViewsWithText(up, toolbar.navigationContentDescription, View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION)
        up.single().performClick()
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `a row of the settings opens it`() {
        val settings = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        val fragment = settings.supportFragmentManager.findFragmentById(R.id.settings) as PreferenceFragmentCompat
        val row = fragment.findPreference<Preference>(SettingsActivity.NOTICES_KEY)!!

        assertEquals(application.getString(R.string.notices_title), row.title)
        assertEquals(NoticesActivity::class.java.name, row.intent!!.component!!.className)
        row.performClick()
        val started: Intent = shadowOf(settings).nextStartedActivity
        assertEquals(NoticesActivity::class.java.name, started.component!!.className)
    }
}
