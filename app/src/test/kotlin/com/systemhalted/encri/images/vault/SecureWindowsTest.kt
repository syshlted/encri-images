/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.vault

import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.view.Window
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SecureWindowsTest {
    private val application: Application = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        application.registerActivityLifecycleCallbacks(SecureWindows)
    }

    @After
    fun tearDown() {
        application.unregisterActivityLifecycleCallbacks(SecureWindows)
    }

    private val Window.secure: Boolean
        get() = attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0

    @Test
    fun `every activity's window is kept out of screenshots`() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()

        assertTrue(activity.window.secure)
    }

    @Test
    fun `a dialog's window is not, until it is made so`() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()

        assertFalse("the activity's flag does not reach a dialog", Dialog(activity).window!!.secure)
        assertTrue(SecureWindows.secure(Dialog(activity)).window!!.secure)
    }
}
