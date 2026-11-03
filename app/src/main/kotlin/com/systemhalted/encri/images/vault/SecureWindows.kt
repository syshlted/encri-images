/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.vault

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.WindowManager

/**
 * Every screen of this app is kept out of screenshots and the recents thumbnail (AGENTS.md I3). A gallery of a
 * vault is exactly what those would otherwise save, in plaintext, to wherever they go.
 */
object SecureWindows : Application.ActivityLifecycleCallbacks {
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
