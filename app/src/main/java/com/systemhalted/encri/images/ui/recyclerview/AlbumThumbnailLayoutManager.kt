/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.ui.recyclerview

import android.content.Context
import com.systemhalted.encri.images.ext.px

class AlbumThumbnailLayoutManager(
    context: Context,
) : DisplayAwareGridLayoutManager(context, 2, 24.px)
