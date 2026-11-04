/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.ui.recyclerview

import android.graphics.RenderEffect
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.systemhalted.encri.images.R
import com.systemhalted.encri.images.ext.load
import com.systemhalted.encri.images.models.Media
import com.systemhalted.encri.images.models.MediaType
import com.systemhalted.encri.images.models.Thumbnail
import com.systemhalted.encri.images.viewmodels.AlbumViewModel
import java.util.Date

class ThumbnailAdapter : ListAdapter<AlbumViewModel.AlbumContent, RecyclerView.ViewHolder>(
    UniqueItemDiffCallback()
) {
    private var onItemSelected: (media: Media) -> Unit = {}
    private var onItemLongPressed: (media: Media) -> Unit = {}

    // What is chosen, drawn over each cell (I16)
    private var selected: Set<Uri> = emptySet()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        LayoutInflater.from(parent.context).let { layoutInflater ->
            when (viewType) {
                AlbumViewModel.AlbumContent.ViewType.THUMBNAIL.ordinal -> ThumbnailViewHolder(
                    layoutInflater.inflate(R.layout.thumbnail_view, parent, false)
                )

                AlbumViewModel.AlbumContent.ViewType.DATE_HEADER.ordinal -> DateHeaderViewHolder(
                    layoutInflater.inflate(R.layout.date_header_view, parent, false)
                )

                else -> throw Exception("Unknown view type $viewType")
            }
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder.itemViewType) {
            AlbumViewModel.AlbumContent.ViewType.THUMBNAIL.ordinal -> {
                val thumbnailViewHolder = holder as ThumbnailViewHolder
                val media = (getItem(position) as AlbumViewModel.AlbumContent.MediaItem).media
                thumbnailViewHolder.bind(media)
            }

            AlbumViewModel.AlbumContent.ViewType.DATE_HEADER.ordinal -> {
                val dateHeaderViewHolder = holder as DateHeaderViewHolder
                dateHeaderViewHolder.bind((getItem(position) as AlbumViewModel.AlbumContent.DateHeader).date)
            }
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        // Only what is chosen changed: the picture is not loaded again.
        if (SELECTION in payloads && holder is ThumbnailViewHolder) {
            holder.bindSelection((getItem(position) as AlbumViewModel.AlbumContent.MediaItem).media)
        } else {
            onBindViewHolder(holder, position)
        }
    }

    override fun getItemViewType(position: Int) = getItem(position).viewType

    fun setOnItemLongPressed(onItemLongPressed: ((media: Media) -> Unit)?) {
        this.onItemLongPressed = onItemLongPressed ?: {}
    }

    fun setSelected(selected: Set<Uri>) {
        if (selected == this.selected) return
        this.selected = selected
        notifyItemRangeChanged(0, itemCount, SELECTION)
    }

    fun setOnItemSelected(onItemSelected: ((media: Media) -> Unit)?) {
        this.onItemSelected = onItemSelected ?: {}
    }

    inner class ThumbnailViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        // Views
        private val selectionCheckedImageView =
            itemView.findViewById<ImageView>(R.id.selectionCheckedImageView)!!
        private val selectionScrimView = itemView.findViewById<View>(R.id.selectionScrimView)!!
        private val videoOverlayImageView =
            itemView.findViewById<ImageView>(R.id.videoOverlayImageView)!!
        private val thumbnailImageView = itemView.findViewById<ImageView>(R.id.thumbnailImageView)!!

        fun bind(media: Media) {
            itemView.setOnClickListener {
                onItemSelected(media)
            }
            itemView.setOnLongClickListener {
                onItemLongPressed(media)
                true
            }

            thumbnailImageView.load(
                media.uri,
                options = Thumbnail.previewOptions(),
            )

            videoOverlayImageView.isVisible = media.mediaType == MediaType.VIDEO

            bindSelection(media)
        }

        /** Glimpse's look for a chosen cell: blurred from API 31, behind a scrim before it, and checked. */
        fun bindSelection(media: Media) {
            val isSelected = media.uri in selected

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                thumbnailImageView.setRenderEffect(
                    when (isSelected) {
                        true -> RenderEffect.createBlurEffect(BLUR_RADIUS, BLUR_RADIUS, Shader.TileMode.CLAMP)
                        false -> null
                    }
                )
            } else {
                selectionScrimView.isVisible = isSelected
            }

            selectionCheckedImageView.isVisible = selected.isNotEmpty()
            selectionCheckedImageView.setImageResource(
                when (isSelected) {
                    true -> R.drawable.ic_check_circle
                    false -> R.drawable.ic_check_circle_outline
                }
            )
        }
    }

    class DateHeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        // Views
        private val textView = view as TextView

        fun bind(date: Date) {
            textView.text = DateUtils.getRelativeTimeSpanString(
                date.time,
                System.currentTimeMillis(),
                DateUtils.DAY_IN_MILLIS
            )
        }
    }

    companion object {
        private const val SELECTION = "selection"
        private const val BLUR_RADIUS = 16f
    }
}
