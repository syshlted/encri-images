/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.vault

import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.os.Bundle
import android.view.WindowManager

/**
 * Every screen of this app is kept out of screenshots and the recents thumbnail (AGENTS.md I3). A gallery of a
 * vault is exactly what those would otherwise save, in plaintext, to wherever they go.
 *
 * **A dialog is a window of its own** (I3, amended), which an activity's flag does not reach: a screen recording
 * leaves out the activity behind it and shows the dialog. So every dialog goes through [secure] before it is shown.
 */
object SecureWindows : Application.ActivityLifecycleCallbacks {
    /** [dialog], kept out of screenshots as the activity it belongs to is. */
    fun <T : Dialog> secure(dialog: T): T = dialog.apply { window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE) }

    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle,
    ) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}
