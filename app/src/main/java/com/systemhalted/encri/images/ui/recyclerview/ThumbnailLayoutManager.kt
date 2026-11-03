/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.ui.recyclerview

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import com.systemhalted.encri.images.ext.px
import com.systemhalted.encri.images.viewmodels.AlbumViewModel

class ThumbnailLayoutManager(
    context: Context,
    adapter: RecyclerView.Adapter<*>,
) : DisplayAwareGridLayoutManager(context, 4, 4.px) {
    init {
        spanSizeLookup = ThumbnailSpanSizeLookup(adapter, spanCount)
    }

    private class ThumbnailSpanSizeLookup(
        private val adapter: RecyclerView.Adapter<*>,
        private val spanCount: Int,
    ) : SpanSizeLookup() {
        override fun getSpanSize(position: Int) = when (adapter.getItemViewType(position)) {
            AlbumViewModel.AlbumContent.ViewType.THUMBNAIL.ordinal -> 1
            AlbumViewModel.AlbumContent.ViewType.DATE_HEADER.ordinal -> spanCount
            else -> throw Exception("Unknown view type")
        }
    }
}
