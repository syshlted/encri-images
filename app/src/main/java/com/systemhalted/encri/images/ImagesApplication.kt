/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images

import android.app.Application
import com.google.android.material.color.DynamicColors
import com.systemhalted.encri.images.repository.MediaRepository
import com.systemhalted.encri.images.vault.ClientOpener
import com.systemhalted.encri.images.vault.LinkState
import com.systemhalted.encri.images.vault.PrivacySettings
import com.systemhalted.encri.images.vault.SecureWindows
import com.systemhalted.encri.images.vault.VaultDataSource
import com.systemhalted.encri.images.vault.VaultGlide
import com.systemhalted.encri.images.vault.VaultLibrary
import com.systemhalted.encri.images.vault.VaultLink
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

class ImagesApplication : Application() {
    private val scope = MainScope()

    val link by lazy { VaultLink(ClientOpener(this), scope) }
    val library by lazy { VaultLibrary(link, scope) }
    val mediaRepository by lazy { MediaRepository(VaultDataSource(library)) }

    override fun onCreate() {
        super.onCreate()

        // Observe dynamic colors changes
        DynamicColors.applyToActivitiesIfAvailable(this)

        registerActivityLifecycleCallbacks(SecureWindows)
        VaultGlide.install(this, link) { PrivacySettings.of(this).previewsShown }

        // A locked vault leaves no picture in memory that this app can help.
        scope.launch {
            link.state
                .filter { it !is LinkState.Open || it.locked }
                .collect { VaultGlide.forget(this@ImagesApplication) }
        }
    }
}
