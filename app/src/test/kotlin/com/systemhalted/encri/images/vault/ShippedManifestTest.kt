/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.vault

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
}
