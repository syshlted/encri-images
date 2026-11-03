/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.fragments

import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.systemhalted.encri.images.R
import com.systemhalted.encri.images.ext.getViewProperty
import com.systemhalted.encri.images.ext.loadThumbnail
import com.systemhalted.encri.images.ext.updatePadding
import com.systemhalted.encri.images.models.Album
import com.systemhalted.encri.images.models.RequestStatus
import com.systemhalted.encri.images.models.Thumbnail
import com.systemhalted.encri.images.ui.recyclerview.AlbumThumbnailLayoutManager
import com.systemhalted.encri.images.ui.recyclerview.SimpleListAdapter
import com.systemhalted.encri.images.ui.recyclerview.UniqueItemDiffCallback
import com.systemhalted.encri.images.viewmodels.AlbumsViewModel

/**
 * An albums list visualizer.
 */
class AlbumsFragment : Fragment(R.layout.fragment_albums) {
    // View models
    private val albumsViewModel by viewModels<AlbumsViewModel>()

    // Views
    private val noMediaLinearLayout by getViewProperty<LinearLayout>(R.id.noMediaLinearLayout)
    private val recyclerView by getViewProperty<RecyclerView>(R.id.recyclerView)

    // RecyclerView
    private val adapter by lazy {
        object : SimpleListAdapter<Album, View>(
            UniqueItemDiffCallback(),
            { parent ->
                LayoutInflater.from(parent.context).inflate(
                    R.layout.album_thumbnail_view, parent, false
                )
            }
        ) {
            // Views
            private val ViewHolder.descriptionTextView
                get() = view.findViewById<TextView>(R.id.descriptionTextView)!!
            private val ViewHolder.itemsCountTextView
                get() = view.findViewById<TextView>(R.id.itemsCountTextView)!!
            private val ViewHolder.thumbnailImageView
                get() = view.findViewById<ImageView>(R.id.thumbnailImageView)!!

            override fun ViewHolder.onPrepareView() {
                view.setOnClickListener {
                    item?.let {
                        findNavController().navigate(
                            R.id.action_mainFragment_to_fragment_album,
                            AlbumFragment.createBundle(albumUri = it.uri)
                        )
                    }
                }
            }

            override fun ViewHolder.onBindView(item: Album) {
                descriptionTextView.text = item.name ?: view.resources.getString(R.string.album_vault)
                item.mediaCount?.let { mediaCount ->
                    itemsCountTextView.text = view.resources.getQuantityString(
                        R.plurals.album_thumbnail_items, mediaCount, mediaCount
                    )
                }

                thumbnailImageView.loadThumbnail(
                    item.thumbnail,
                    options = Thumbnail.previewOptions(),
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Insets
        ViewCompat.setOnApplyWindowInsetsListener(recyclerView) { _, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )

            recyclerView.updatePadding(
                insets,
                start = true,
                end = true,
            )

            windowInsets
        }

        val context = requireContext()

        recyclerView.layoutManager = AlbumThumbnailLayoutManager(context)
        recyclerView.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                loadData()
            }
        }
    }

    override fun onDestroyView() {
        recyclerView.layoutManager = null
        recyclerView.adapter = null

        super.onDestroyView()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        recyclerView.layoutManager = AlbumThumbnailLayoutManager(requireContext())
    }

    private suspend fun loadData() {
        coroutineScope {
            albumsViewModel.loadAlbums(AlbumsViewModel.AlbumsRequest())

            launch {
                albumsViewModel.albums.collectLatest {
                    when (it) {
                        is RequestStatus.Loading -> {
                            // Whatever was here, the vault may have locked since.
                            adapter.submitList(listOf())
                        }

                        is RequestStatus.Success -> {
                            adapter.submitList(it.data)

                            val isEmpty = it.data.isEmpty()
                            recyclerView.isVisible = !isEmpty
                            noMediaLinearLayout.isVisible = isEmpty
                        }

                        is RequestStatus.Error -> {
                            Log.e(LOG_TAG, "Failed to load albums, error: ${it.error}")

                            recyclerView.isVisible = false
                            noMediaLinearLayout.isVisible = true
                        }
                    }
                }
            }
        }
    }

    companion object {
        private val LOG_TAG = AlbumsFragment::class.simpleName!!
    }
}
