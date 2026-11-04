/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.fragments

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.ActionMode
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.shape.MaterialShapeDrawable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.systemhalted.encri.images.R
import com.systemhalted.encri.images.ViewActivity
import com.systemhalted.encri.images.datasources.MediaError
import com.systemhalted.encri.images.ext.getParcelable
import com.systemhalted.encri.images.ext.getSerializable
import com.systemhalted.encri.images.ext.getViewProperty
import com.systemhalted.encri.images.models.Album
import com.systemhalted.encri.images.models.AlbumType
import com.systemhalted.encri.images.models.MediaType
import com.systemhalted.encri.images.models.RequestStatus
import com.systemhalted.encri.images.ui.recyclerview.ThumbnailAdapter
import com.systemhalted.encri.images.ui.recyclerview.ThumbnailLayoutManager
import com.systemhalted.encri.images.viewmodels.AlbumViewModel

/**
 * A fragment showing a list of media from a specific album with thumbnails.
 */
class AlbumFragment : Fragment(R.layout.fragment_album) {
    // View models
    private val viewModel by viewModels<AlbumViewModel>()

    // Views
    private val appBarLayout by getViewProperty<AppBarLayout>(R.id.appBarLayout)
    private val noMediaLinearLayout by getViewProperty<LinearLayout>(R.id.noMediaLinearLayout)
    private val recyclerView by getViewProperty<RecyclerView>(R.id.recyclerView)
    private val toolbar by getViewProperty<MaterialToolbar>(R.id.toolbar)

    // RecyclerView
    private val thumbnailAdapter by lazy { ThumbnailAdapter() }

    // Arguments
    private val albumType: AlbumType?
        get() = requireArguments().getSerializable(ARG_ALBUM_TYPE, AlbumType::class)
    private val albumUri: Uri?
        get() = requireArguments().getParcelable(ARG_ALBUM_URI, Uri::class)
    private val mediaType: MediaType?
        get() = requireArguments().getSerializable(ARG_MEDIA_TYPE, MediaType::class)
    private val mimeType: String?
        get() = requireArguments().getString(ARG_MIME_TYPE, null)
    private val hideToolbar: Boolean
        get() = requireArguments().getBoolean(ARG_HIDE_TOOLBAR, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Insets
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )

            toolbar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                rightMargin = insets.right
            }

            recyclerView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                rightMargin = insets.right
            }
            recyclerView.updatePadding(bottom = insets.bottom)

            windowInsets
        }

        appBarLayout.isVisible = !hideToolbar

        toolbar.setupWithNavController(findNavController())

        val context = requireContext()

        appBarLayout.statusBarForeground = MaterialShapeDrawable.createWithElevationOverlay(context)

        recyclerView.layoutManager = ThumbnailLayoutManager(
            requireContext(), thumbnailAdapter
        )
        recyclerView.adapter = thumbnailAdapter

        thumbnailAdapter.setOnItemSelected { media ->
            startActivity(
                Intent(requireContext(), ViewActivity::class.java).apply {
                    putExtras(
                        ViewActivity.createBundle(
                            albumType,
                            albumUri,
                            mediaType,
                            mimeType,
                            media.uri,
                        )
                    )
                }
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                loadData()
            }
        }
    }

    override fun onDestroyView() {
        recyclerView.layoutManager = null
        recyclerView.adapter = null

        thumbnailAdapter.setOnItemSelected(null)

        super.onDestroyView()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        recyclerView.layoutManager = ThumbnailLayoutManager(
            requireContext(), thumbnailAdapter
        )
    }

    private suspend fun loadData() {
        coroutineScope {
            viewModel.loadAlbum(
                AlbumViewModel.AlbumRequest(
                    albumType = albumType,
                    albumUri = albumUri,
                    mediaType = mediaType,
                    mimeType = mimeType,
                )
            )

            launch {
                viewModel.album.collectLatest {
                    when (it) {
                        is RequestStatus.Loading -> {
                            // Whatever was here, the vault may have locked since.
                            thumbnailAdapter.submitList(listOf())
                        }

                        is RequestStatus.Success -> {
                            val (album, medias) = it.data

                            val albumRequest = viewModel.albumRequest.value
                            when (albumRequest?.albumType) {
                                AlbumType.REELS -> when (albumRequest.mediaType) {
                                    MediaType.IMAGE -> R.string.album_photos
                                    MediaType.VIDEO -> R.string.album_videos
                                    else -> R.string.album_reels
                                }

                                null -> null
                            }?.also { stringResId ->
                                toolbar.setTitle(stringResId)
                            } ?: run {
                                toolbar.title = album.name ?: getString(R.string.album_vault)
                            }

                            thumbnailAdapter.submitList(medias)

                            val isEmpty = medias.isEmpty()
                            recyclerView.isVisible = !isEmpty
                            noMediaLinearLayout.isVisible = isEmpty
                        }

                        is RequestStatus.Error -> {
                            Log.e(LOG_TAG, "Failed to load album, error: ${it.error}")

                            toolbar.title = ""

                            thumbnailAdapter.submitList(listOf())

                            recyclerView.isVisible = false
                            noMediaLinearLayout.isVisible = true

                            if (it.error == MediaError.NOT_FOUND) {
                                // Get out of here
                                findNavController().navigateUp()
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        private val LOG_TAG = AlbumFragment::class.simpleName!!

        private const val ARG_ALBUM_TYPE = "album_type"
        private const val ARG_ALBUM_URI = "album_uri"
        private const val ARG_MEDIA_TYPE = "media_type"
        private const val ARG_MIME_TYPE = "mime_type"
        private const val ARG_HIDE_TOOLBAR = "hide_toolbar"

        /**
         * Create a [Bundle] to use as the arguments for this fragment.
         * @param albumType The [AlbumType] to display, null to use [albumUri]
         * @param albumUri The [Album] to display's bucket ID, if null, reels will be shown
         * @param fileType The [MediaType] to filter for
         * @param mimeType The MIME type to filter for
         */
        fun createBundle(
            albumType: AlbumType? = null,
            albumUri: Uri? = null,
            fileType: MediaType? = null,
            mimeType: String? = null,
            hideToolbar: Boolean = false,
        ) = Bundle().apply {
            putSerializable(ARG_ALBUM_TYPE, albumType)
            putParcelable(ARG_ALBUM_URI, albumUri)
            putSerializable(ARG_MEDIA_TYPE, fileType)
            putString(ARG_MIME_TYPE, mimeType)
            putBoolean(ARG_HIDE_TOOLBAR, hideToolbar)
        }
    }
}
