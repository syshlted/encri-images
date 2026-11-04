/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.vault

import android.app.Application
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.systemhalted.encri.images.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PrivacySettingsTest {
    private val application: Application = ApplicationProvider.getApplicationContext()
    private val preferences = PreferenceManager.getDefaultSharedPreferences(application)

    @After
    fun tearDown() {
        preferences.edit(commit = true) { clear() }
    }

    @Test
    fun `screenshots are kept out and previews shown until the user says otherwise`() {
        val privacy = PrivacySettings.of(application)
        assertFalse(privacy.screenshotsAllowed)
        assertTrue(privacy.previewsShown)
    }

    @Test
    fun `what the user chose is what is read`() {
        preferences.edit(commit = true) {
            putBoolean(PrivacySettings.SCREENSHOTS, true)
            putBoolean(PrivacySettings.PREVIEWS, false)
        }
        val privacy = PrivacySettings.of(application)
        assertTrue(privacy.screenshotsAllowed)
        assertFalse(privacy.previewsShown)
    }

    @Test
    fun `the settings screen writes these keys, and shows these defaults`() {
        val switches = switchesIn(R.xml.root_preferences)
        assertEquals("false", switches[PrivacySettings.SCREENSHOTS])
        assertEquals("true", switches[PrivacySettings.PREVIEWS])
    }

    /** Every switch in [screen], by key, with the default it shows. */
    private fun switchesIn(screen: Int): Map<String, String?> {
        val switches = mutableMapOf<String, String?>()
        val parser = application.resources.getXml(screen)
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "SwitchPreferenceCompat") {
                switches[parser.getAttributeValue(APP, "key")] = parser.getAttributeValue(APP, "defaultValue")
            }
        }
        return switches
    }

    private companion object {
        const val APP = "http://schemas.android.com/apk/res-auto"
    }
}
