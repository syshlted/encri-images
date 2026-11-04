/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.vault

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.systemhalted.encri.images.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.w3c.dom.Element
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory

/** What the merged manifest gives away, including whatever a library merged in (I3). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ShippedManifestTest {
    private val info =
        ApplicationProvider.getApplicationContext<Application>().let {
            it.packageManager.getPackageInfo(
                it.packageName,
                PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES or PackageManager.GET_PROVIDERS or
                    PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS,
            )
        }

    @Test
    fun `the app asks for no permission a vault's plaintext could leave by`() {
        // AndroidX's signature permission to itself reaches nothing outside this app.
        val asked =
            info.requestedPermissions
                .orEmpty()
                .filterNot { it.endsWith(".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION") }
        assertEquals(emptyList<String>(), asked)
    }

    @Test
    fun `the launcher is the only activity another app can start`() {
        val exported =
            info.activities
                .orEmpty()
                .filter { it.exported }
                .map { it.name.substringAfterLast('.') }
        assertEquals(listOf("MainActivity"), exported)
    }

    @Test
    fun `nothing else in the app can be reached from outside it`() {
        val reachable =
            info.services
                .orEmpty()
                .filter { it.exported }
                .map { it.name } +
                info.providers
                    .orEmpty()
                    .filter { it.exported }
                    .map { it.name } +
                // AndroidX's profile installer is exported behind DUMP, which only the shell and the system hold.
                info.receivers
                    .orEmpty()
                    .filter { it.exported && it.permission == null }
                    .map { it.name }
        assertEquals(emptyList<String>(), reachable)
    }

    @Test
    fun `nothing leaves through a backup`() {
        assertEquals(0, info.applicationInfo!!.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }

    @Test
    fun `nothing leaves through a transfer to a new phone either`() {
        // From API 31 allowBackup="false" stops a cloud backup and not a device transfer; only the rules do.
        assertEquals("@xml/data_extraction_rules", mergedApplicationAttribute("dataExtractionRules"))
        val excluded = excludedIn(R.xml.data_extraction_rules)
        assertEquals(setOf("cloud-backup", "device-transfer"), excluded.keys)
        excluded.forEach { (kind, domains) -> assertEquals(kind, EVERY_DOMAIN, domains) }
    }

    /**
     * [name] on the merged manifest's `<application>`, read from the file AGP merged, since Robolectric does not
     * parse every attribute.
     */
    private fun mergedApplicationAttribute(name: String): String? {
        val config = Properties()
        javaClass.classLoader!!.getResourceAsStream("com/android/tools/test_config.properties").use(config::load)
        val manifest = File(config.getProperty("android_merged_manifest"))
        val application =
            DocumentBuilderFactory
                .newInstance()
                .apply { isNamespaceAware = true }
                .newDocumentBuilder()
                .parse(manifest)
                .getElementsByTagName("application")
                .item(0) as Element
        return application.getAttributeNS(ANDROID, name).ifEmpty { null }
    }

    @Test
    fun `a screen recording carries no sound of a vault video`() {
        // FLAG_SECURE keeps the picture out of a recording and not the sound, which from API 29 any app may capture.
        assertEquals("false", mergedApplicationAttribute("allowAudioPlaybackCapture"))
    }

    /** Each kind of transfer in [rules], with the domains it excludes whole. */
    private fun excludedIn(rules: Int): Map<String, Set<String>> {
        val excluded = mutableMapOf<String, MutableSet<String>>()
        val parser = ApplicationProvider.getApplicationContext<Application>().resources.getXml(rules)
        var kind: String? = null
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "cloud-backup", "device-transfer" -> {
                    kind = parser.name.also { excluded[it] = mutableSetOf() }
                }

                "exclude" -> {
                    if (parser.getAttributeValue(null, "path") == ".") {
                        excluded.getValue(kind!!) += parser.getAttributeValue(null, "domain")
                    }
                }
            }
        }
        return excluded
    }

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"

        val EVERY_DOMAIN =
            setOf(
                "root",
                "file",
                "database",
                "sharedpref",
                "external",
                "device_root",
                "device_file",
                "device_database",
                "device_sharedpref",
            )
    }
}
