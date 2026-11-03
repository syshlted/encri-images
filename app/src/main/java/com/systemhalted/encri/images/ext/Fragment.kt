/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.ext

import android.view.View
import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import kotlin.properties.ReadOnlyProperty

inline fun <reified T : View?> getViewProperty(@IdRes viewId: Int) =
    ReadOnlyProperty<Fragment, T> { thisRef, _ ->
        thisRef.requireView().findViewById<T>(viewId)
    }
