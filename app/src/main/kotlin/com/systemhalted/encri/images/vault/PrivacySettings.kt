/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.vault

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

/**
 * The two settings what this app lets out of its screens rests on (AGENTS.md I13), in its own preferences, which
 * nothing backs up or transfers (I3). The settings screen writes them under these keys.
 */
class PrivacySettings(
    private val preferences: SharedPreferences,
) {
    /** Whether the user allows screenshots of this app. Off until they say so, as in encri-files (F3). */
    val screenshotsAllowed: Boolean get() = preferences.getBoolean(SCREENSHOTS, false)

    /** Whether the grid asks encri for previews. On until the user says not (encri D112, encri-files F4). */
    val previewsShown: Boolean get() = preferences.getBoolean(PREVIEWS, true)

    companion object {
        const val SCREENSHOTS = "allow_screenshots"
        const val PREVIEWS = "show_previews"

        fun of(context: Context): PrivacySettings =
            PrivacySettings(PreferenceManager.getDefaultSharedPreferences(context))
    }
}
