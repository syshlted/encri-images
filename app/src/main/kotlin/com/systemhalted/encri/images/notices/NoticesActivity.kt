/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.notices

import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import com.google.android.material.appbar.MaterialToolbar
import com.systemhalted.encri.images.R

/**
 * The third-party notices this app's libraries require in a binary distribution (AGENTS.md I19).
 *
 * The Apache License asks for its text and any NOTICE file to go with the app, and some of the libraries carry
 * code under other licences that ask for their notices to be reproduced. Most of their artifacts hold no licence
 * file, so without this screen the APK would carry none of it.
 *
 * What it shows is an asset, written by hand and held to the build by `:app:checkThirdPartyNotices`: every
 * module a shipped APK carries is named in it, at the version that ships. The screen only draws it.
 *
 * Opened from the settings, which the gallery offers whatever state the vault is in, because a licence's terms
 * do not depend on there being a vault. Its window follows the screenshot setting as every window here does
 * (I13). Not exported.
 */
class NoticesActivity : AppCompatActivity(R.layout.activity_notices) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        findViewById<TextView>(R.id.notices).text =
            noticeText(assets.open(NOTICES_ASSET).bufferedReader().use { it.readText() })

        val scroll = findViewById<NestedScrollView>(R.id.scrollView)
        val padding = scroll.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.updatePadding(left = padding + bars.left, right = padding + bars.right, bottom = padding + bars.bottom)
            insets
        }
    }
}
