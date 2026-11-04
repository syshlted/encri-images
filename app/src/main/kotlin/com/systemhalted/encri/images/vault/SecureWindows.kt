/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.vault

import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.os.Bundle
import android.view.Window
import android.view.WindowManager

/**
 * Every screen of this app is kept out of screenshots and the recents thumbnail (AGENTS.md I3), unless the user
 * allows screenshots in its settings (I13). A gallery of a vault is exactly what those would otherwise save, in
 * plaintext, to wherever they go.
 *
 * **A dialog is a window of its own** (I3, amended), which an activity's flag does not reach: a screen recording
 * leaves out the activity behind it and shows the dialog. So every dialog goes through [secure] before it is shown.
 *
 * **The setting is applied when a window is made and again whenever a screen comes to the front**, as encri-files
 * applies its own (F3), so a change in settings reaches the gallery behind them without a restart, and the first
 * frame, the one the recent apps keep, is already right.
 */
object SecureWindows : Application.ActivityLifecycleCallbacks {
    /** [dialog], kept out of screenshots as the activity it belongs to is. */
    fun <T : Dialog> secure(dialog: T): T =
        dialog.apply { window?.let { follow(it, PrivacySettings.of(context).screenshotsAllowed) } }

    /** [activity]'s window, made to follow the setting now: for the screen the setting was changed on. */
    fun follow(activity: Activity) = follow(activity.window, PrivacySettings.of(activity).screenshotsAllowed)

    private fun follow(
        window: Window,
        allowed: Boolean,
    ) {
        if (allowed) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) = follow(activity)

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityResumed(activity: Activity) = follow(activity)

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle,
    ) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}
