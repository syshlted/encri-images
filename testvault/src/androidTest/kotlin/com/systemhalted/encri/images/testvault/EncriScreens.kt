/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.testvault

import android.app.Instrumentation
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.systemhalted.encri.client.Encri

/**
 * Presses encri's buttons for a test, the way a person would, through the accessibility tree: encri-api's own
 * device tests' way (encri-api L13), which says why the tree and not touches.
 *
 * **Only encri's own window is ever pressed or typed into**, so a passphrase goes nowhere but encri, whatever
 * happens to be in front. **Matched on encri's English words**, as encri-api's tests name them, so this runs on a
 * phone set to English; case aside, because the theme draws a button's label in capitals.
 */
internal class EncriScreens(
    instrumentation: Instrumentation,
) {
    private val automation = instrumentation.uiAutomation

    /** Whether a button with exactly [label] is on screen now. */
    fun showing(label: String): Boolean = button(label) != null

    /** Waits for a button with exactly [label] and presses it. */
    fun press(
        label: String,
        millis: Long = WAIT_MILLIS,
    ) {
        val found = waitFor(millis) { button(label) } ?: throw AssertionError("encri never showed “$label”")
        check(found.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { "“$label” did not take the press" }
    }

    /** Types [passphrase] into the passphrase field on encri's locked screen and unlocks with it. */
    fun unlockWith(passphrase: String) {
        val field = waitFor(WAIT_MILLIS) { editable() } ?: throw AssertionError("encri showed no passphrase field")
        val typed =
            Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, passphrase)
            }
        check(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, typed)) { "the passphrase field took nothing" }
        press(UNLOCK_WITH_PASSPHRASE)
    }

    /** Waits until one of [labels] is on screen, and answers which. */
    fun whichOf(
        vararg labels: String,
        millis: Long = WAIT_MILLIS,
    ): String =
        waitFor(millis) { labels.firstOrNull(::showing) }
            ?: throw AssertionError("encri showed none of ${labels.toList()}")

    /** The window in front, if it is encri's; null for anything else, which is never touched. */
    private fun encris(): AccessibilityNodeInfo? =
        automation.rootInActiveWindow?.takeIf { it.packageName?.toString() == Encri.PACKAGE }

    private fun button(label: String): AccessibilityNodeInfo? =
        encris()
            ?.findAccessibilityNodeInfosByText(label)
            .orEmpty()
            .firstOrNull {
                it.text?.toString().equals(label, ignoreCase = true) && it.isClickable && it.isVisibleToUser &&
                    it.isEnabled
            }

    private fun editable(): AccessibilityNodeInfo? {
        val pending = ArrayDeque(listOfNotNull(encris()))
        while (pending.isNotEmpty()) {
            val node = pending.removeFirst()
            if (node.isEditable && node.isVisibleToUser) return node
            (0 until node.childCount).mapNotNullTo(pending) { node.getChild(it) }
        }
        return null
    }

    private fun <T : Any> waitFor(
        millis: Long,
        probe: () -> T?,
    ): T? {
        val until = SystemClock.uptimeMillis() + millis
        while (SystemClock.uptimeMillis() < until) {
            probe()?.let { return it }
            SystemClock.sleep(POLL_MILLIS)
        }
        return null
    }

    companion object {
        // encri's words, as encri-api's device tests name them.
        const val UNLOCK_WITH_PASSPHRASE = "Unlock with passphrase"
        const val UNLOCK_THE_VAULT = "Unlock the vault"
        const val TURN_ACCESS_ON = "Turn on access for other apps"
        const val ALLOW_THE_WHOLE_VAULT = "Allow the whole vault"

        private const val WAIT_MILLIS = 20_000L
        private const val POLL_MILLIS = 250L
    }
}
