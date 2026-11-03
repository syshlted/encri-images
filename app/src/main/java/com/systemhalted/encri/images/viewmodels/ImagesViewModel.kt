/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.systemhalted.encri.images.ImagesApplication

open class ImagesViewModel(application: Application) : AndroidViewModel(application) {
    protected val mediaRepository = getApplication<ImagesApplication>().mediaRepository
    protected val link = getApplication<ImagesApplication>().link

    @Suppress("EmptyMethod")
    final override fun <T : Application> getApplication() = super.getApplication<T>()
}
