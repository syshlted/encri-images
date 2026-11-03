/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.fragments

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.systemhalted.encri.images.R
import com.systemhalted.encri.images.ext.getViewProperty
import com.systemhalted.encri.images.models.AlbumType
import com.systemhalted.encri.images.models.MediaType
import com.systemhalted.encri.images.ui.views.ListItem

/**
 * A fragment showing a search bar with categories.
 */
class LibraryFragment : Fragment(R.layout.fragment_library) {
    // Views
    private val photosAlbumListItem by getViewProperty<ListItem>(R.id.photosAlbumListItem)
    private val libraryNestedScrollView by getViewProperty<NestedScrollView>(R.id.libraryNestedScrollView)
    private val videosAlbumListItem by getViewProperty<ListItem>(R.id.videosAlbumListItem)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )

            libraryNestedScrollView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                rightMargin = insets.right
            }

            windowInsets
        }

        photosAlbumListItem.setOnClickListener {
            openAlbum(AlbumType.REELS, MediaType.IMAGE)
        }

        videosAlbumListItem.setOnClickListener {
            openAlbum(AlbumType.REELS, MediaType.VIDEO)
        }
    }

    private fun openAlbum(
        albumType: AlbumType,
        fileType: MediaType? = null,
    ) {
        findNavController().navigate(
            R.id.action_mainFragment_to_fragment_album,
            AlbumFragment.createBundle(
                albumType = albumType,
                fileType = fileType,
            )
        )
    }
}
