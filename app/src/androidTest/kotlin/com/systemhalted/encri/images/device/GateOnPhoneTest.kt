/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.device

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.systemhalted.encri.images.MainActivity
import com.systemhalted.encri.images.R
import com.systemhalted.encri.images.device.OnPhone.context
import com.systemhalted.encri.images.device.OnPhone.instrumentation
import com.systemhalted.encri.images.device.OnPhone.log
import com.systemhalted.encri.images.vault.gateOf
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The app as installed on this phone shows the gate its link calls for, or the gallery and nothing in front of
 * it (I6): its Application, Glide's setup, the secure windows and the gate, over whatever encri this phone has.
 *
 * It needs no encri, so it is the check that runs on every phone, and on a phone without encri it is the gate
 * saying so.
 */
@RunWith(AndroidJUnit4::class)
class GateOnPhoneTest {
    @Test
    fun theScreenIsWhatTheLinkCallsFor() {
        val activity =
            instrumentation.startActivitySync(
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        try {
            OnPhone.settled()
            val shown = rendered(activity)
            log("gate: API ${Build.VERSION.SDK_INT}, ${Build.MODEL}, showing $shown")
        } finally {
            activity.finish()
        }
    }

    /**
     * Waits until the screen is what the link's state calls for, and answers what that is. The screen follows the
     * state on the main thread, so it may be a frame or so behind it.
     */
    private fun rendered(activity: Activity): String {
        val until = SystemClock.uptimeMillis() + RENDER_MILLIS
        while (true) {
            val state = OnPhone.images.link.state.value
            val gate = gateOf(state)
            var seen = ""
            var matches = false
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val gateShown = activity.findViewById<View>(R.id.gateView).isVisible
                val galleryShown = activity.findViewById<View>(R.id.navHostFragment).isVisible
                val message = activity.findViewById<TextView>(R.id.gateMessage).text.toString()
                seen = "the gate ${if (gateShown) "shown" else "hidden"}, saying “$message”, " +
                    "and the gallery ${if (galleryShown) "shown" else "hidden"}, " +
                    "in an activity ${(activity as MainActivity).lifecycle.currentState}"
                matches = gateShown == (gate != null) && galleryShown == (gate == null) &&
                    (gate == null || message == context.getString(gate.message))
            }
            if (matches) return gate?.let { context.resources.getResourceEntryName(it.message) } ?: "the gallery"
            if (SystemClock.uptimeMillis() > until) {
                throw AssertionError("the link is ${state.javaClass.simpleName} and the screen has $seen")
            }
            SystemClock.sleep(POLL_MILLIS)
        }
    }

    private companion object {
        const val RENDER_MILLIS = 5_000L
        const val POLL_MILLIS = 100L
    }
}
