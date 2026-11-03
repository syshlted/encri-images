/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.ext

import android.content.res.Resources.getSystem
import kotlin.math.roundToInt

/**
 * dp -> px.
 */
val Int.px
    get() = (this * getSystem().displayMetrics.density).roundToInt()
