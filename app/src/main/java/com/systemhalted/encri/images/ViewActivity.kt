/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images

import android.app.KeyguardManager.KeyguardDismissCallback
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.systemhalted.encri.images.datasources.MediaError
import com.systemhalted.encri.images.ext.fade
import com.systemhalted.encri.images.ext.setBarsVisibility
import com.systemhalted.encri.images.models.Album
import com.systemhalted.encri.images.models.AlbumType
import com.systemhalted.encri.images.models.Media
import com.systemhalted.encri.images.models.MediaType
import com.systemhalted.encri.images.models.MotionPhoto
import com.systemhalted.encri.images.models.RequestStatus
import com.systemhalted.encri.images.ui.dialogs.MediaInfoBottomSheetDialog
import com.systemhalted.encri.images.ui.recyclerview.MediaViewerAdapter
import com.systemhalted.encri.images.ext.getParcelable
import com.systemhalted.encri.images.ext.getSerializable
import com.systemhalted.encri.images.vault.DeleteFlow
import com.systemhalted.encri.images.vault.Deletes
import com.systemhalted.encri.images.vault.Handoff
import com.systemhalted.encri.images.vault.Handoffs
import com.systemhalted.encri.images.vault.LinkState
import com.systemhalted.encri.images.vault.SecureWindows
import com.systemhalted.encri.images.viewmodels.AlbumViewModel
import com.systemhalted.encri.images.viewmodels.LocalPlayerViewModel
import java.text.SimpleDateFormat

/**
 * An activity used to view one or mode medias.
 */
class ViewActivity : AppCompatActivity(R.layout.activity_view) {
    // View models
    private val viewModel by viewModels<LocalPlayerViewModel>()

    // Views
    private val appBarLayout by lazy { findViewById<AppBarLayout>(R.id.appBarLayout) }
    private val bottomSheetLinearLayout by lazy { findViewById<LinearLayout>(R.id.bottomSheetLinearLayout) }
    private val deleteButton by lazy { findViewById<MaterialButton>(R.id.deleteButton) }
    private val infoButton by lazy { toolbar.menu.findItem(R.id.info) }
    private val motionPhotoToggleButton by lazy { findViewById<MaterialButton>(R.id.motionPhotoToggleButton) }
    private val openWithButton by lazy { toolbar.menu.findItem(R.id.openWith) }
    private val shareButton by lazy { findViewById<MaterialButton>(R.id.shareButton) }
    private val toolbar by lazy { findViewById<MaterialToolbar>(R.id.toolbar) }
    private val viewPager by lazy { findViewById<ViewPager2>(R.id.viewPager) }

    private var lastVideoUriPlayed: Uri? = null

    // Through encri's handoff, said first (I10)
    private val handoffs by lazy {
        Handoffs(this, (application as ImagesApplication).link, lifecycleScope)
    }

    // Asking encri for the right the first time, and saying it cannot be undone (I11)
    private val deleteFlow by lazy {
        val images = application as ImagesApplication
        DeleteFlow(
            this,
            Deletes(images.link, images.library),
            lifecycleScope,
            ::askEncri,
            ::forgetDeleted,
        )
    }

    // encri learns who asked from getCallingPackage, which only a start for a result sets (encri D120).
    private val askEncriForResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            deleteFlow.answered(it.resultCode, it.data)
        }

    // Adapter
    private val mediaViewerAdapter by lazy {
        MediaViewerAdapter(
            localPlayerViewModel = viewModel,
            onNavigate = { forward ->
                viewPager.adapter?.let { adapter ->
                    val currentPosition = viewPager.currentItem

                    val newPosition = if (forward) {
                        currentPosition + 1
                    } else {
                        currentPosition - 1
                    }

                    if (newPosition in 0 until adapter.itemCount) {
                        viewPager.setCurrentItem(newPosition, true)
                    }
                }
            },
        )
    }

    private val onPageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            super.onPageSelected(position)

            this@ViewActivity.viewModel.setMediaPosition(position)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge
        enableEdgeToEdge()

        ViewCompat.setOnApplyWindowInsetsListener(bottomSheetLinearLayout) { _, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )

            // Avoid updating the sheets height when they're hidden.
            // Once the system bars will be made visible again, this function
            // will be called again.
            if (!viewModel.fullscreenMode.value) {
                bottomSheetLinearLayout.updatePadding(
                    left = insets.left,
                    right = insets.right,
                    bottom = insets.bottom
                )

                updateSheetsHeight()
            }

            windowInsets
        }

        // Attach the adapter to the view pager
        viewPager.adapter = mediaViewerAdapter

        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.info -> {
                    viewModel.displayedMedia.value?.let { showInfo(it) }
                    true
                }

                R.id.openWith -> {
                    viewModel.displayedMedia.value?.let { handoffs.offer(it, Handoff.OPEN_WITH) }
                    true
                }

                else -> false
            }
        }

        toolbar.setNavigationOnClickListener {
            finish()
        }

        motionPhotoToggleButton.setOnClickListener {
            viewModel.toggleMotionPhotoEnabled()
        }

        shareButton.setOnClickListener {
            viewModel.displayedMedia.value?.let { handoffs.offer(it, Handoff.SHARE) }
        }

        deleteButton.setOnClickListener {
            viewModel.displayedMedia.value?.let { deleteFlow.offer(it) }
        }

        viewPager.offscreenPageLimit = 2
        viewPager.registerOnPageChangeCallback(onPageChangeCallback)

        viewModel.setViewRequest(
            LocalPlayerViewModel.ViewRequest(
                AlbumViewModel.AlbumRequest(
                    intent.extras?.getSerializable(EXTRA_ALBUM_TYPE, AlbumType::class),
                    intent.extras?.getParcelable(EXTRA_ALBUM_URI, Uri::class),
                    intent.extras?.getSerializable(EXTRA_MEDIA_TYPE, MediaType::class),
                    intent.extras?.getString(EXTRA_MIME_TYPE),
                ),
                intent.extras?.getParcelable(EXTRA_INITIAL_MEDIA_URI, Uri::class),
            )
        )

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { leaveWhenTheVaultCloses() }
                loadData()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        viewModel.play()
    }

    override fun onPause() {
        saveCurrentVideoPosition()

        viewModel.pause()

        super.onPause()
    }

    override fun onDestroy() {
        saveCurrentVideoPosition()

        viewPager.unregisterOnPageChangeCallback(onPageChangeCallback)

        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        updateSheetsHeight()
    }

    private suspend fun loadData() {
        coroutineScope {
            launch {
                viewModel.mediasWithInitialPosition.collect {
                    when (it) {
                        is RequestStatus.Loading -> {
                            // Do nothing
                        }

                        is RequestStatus.Success -> {
                            val (medias, initialPosition) = it.data

                            mediaViewerAdapter.submitList(medias)

                            initialPosition?.let { position ->
                                viewPager.setCurrentItem(position, false)
                                onPageChangeCallback.onPageSelected(position)

                                viewModel.setMediaPosition(position)
                            }

                            if (medias.isEmpty()) {
                                // Get out of here
                                finish()
                            }
                        }

                        is RequestStatus.Error -> {
                            Log.e(LOG_TAG, "Failed to load medias, error: ${it.error}")

                            mediaViewerAdapter.submitList(listOf())

                            if (it.error == MediaError.NOT_FOUND) {
                                // Get out of here
                                finish()
                            }
                        }
                    }
                }
            }

            launch {
                viewModel.isPlaying.collectLatest { isPlaying ->
                    viewPager.keepScreenOn = isPlaying
                }
            }

            launch {
                viewModel.fullscreenMode.collectLatest { fullscreenMode ->
                    appBarLayout.fade(!fullscreenMode)
                    bottomSheetLinearLayout.fade(!fullscreenMode)

                    window.setBarsVisibility(systemBars = !fullscreenMode)

                    // If the sheets are being made visible again, update the values
                    if (!fullscreenMode) {
                        updateSheetsHeight()
                    }
                }
            }

            launch {
                viewModel.displayedMedia.collectLatest { displayedMedia ->
                    // Update date and time text
                    displayedMedia?.also {
                        toolbar.title = dateFormatter.format(it.dateModified)
                        toolbar.subtitle = timeFormatter.format(it.dateModified)
                    } ?: run {
                        toolbar.title = ""
                        toolbar.subtitle = ""
                    }

                    // Update info button
                    infoButton.isVisible = displayedMedia != null

                    // What leaves through encri's handoff
                    openWithButton.isVisible = displayedMedia != null
                    shareButton.isVisible = displayedMedia != null
                    deleteButton.isVisible = displayedMedia != null

                    // Reset motion photo toggle button
                    viewModel.toggleMotionPhotoEnabled(false)
                }
            }

            launch {
                viewModel.displayedMediaToMotionPhoto.collectLatest { (displayedMedia, motionPhoto) ->
                    // Update ExoPlayer
                    displayedMedia?.let {
                        updateExoPlayer(it, motionPhoto)
                    }

                    val isPlayingMotionPhoto = motionPhoto != null
                    motionPhotoToggleButton.isSelected = isPlayingMotionPhoto
                    motionPhotoToggleButton.setText(
                        when (isPlayingMotionPhoto) {
                            true -> R.string.motion_photo_show_photo
                            false -> R.string.motion_photo_show_video
                        }
                    )

                    // Trigger a sheets height update
                    updateSheetsHeight()
                }
            }

            launch {
                viewModel.motionPhoto.collectLatest { motionPhoto ->
                    motionPhotoToggleButton.isVisible = motionPhoto != null
                }
            }

        }
    }

    /**
     * Update exoPlayer's status.
     * @param media The currently displayed [Media]
     */
    private fun updateExoPlayer(media: Media, motionPhoto: MotionPhoto?) {
        if (media.mediaType == MediaType.VIDEO) {
            if (media.uri != lastVideoUriPlayed) {
                saveCurrentVideoPosition()
                lastVideoUriPlayed = media.uri
                viewModel.setCurrentVideoUri(media.uri)
            }
        } else {
            saveCurrentVideoPosition()
            motionPhoto?.also(viewModel::playMotionPhoto) ?: viewModel.stop()

            // Make sure we will forcefully reload and restart the video
            lastVideoUriPlayed = null
        }
    }

    private fun saveCurrentVideoPosition() {
        viewModel.saveCurrentVideoPosition(lastVideoUriPlayed)
    }

    private fun askEncri(intent: Intent) {
        try {
            askEncriForResult.launch(intent)
        } catch (_: ActivityNotFoundException) {
            // encri was uninstalled while this screen showed; the link says so once it asks again.
            (application as ImagesApplication).link.open()
        }
    }

    /**
     * Called before the list without [media] reaches the pager, so moving on from a deleted video does not
     * save its place again.
     */
    private fun forgetDeleted(media: Media) {
        if (media.uri == lastVideoUriPlayed) {
            lastVideoUriPlayed = null
        }
        viewModel.forgetVideoPosition(media.uri)
    }

    /**
     * A descriptor encri already opened goes on serving across a lock (encri D103), so playing on is this
     * screen's to stop. Its view model releases the player when it finishes.
     */
    private suspend fun leaveWhenTheVaultCloses() {
        val link = (application as ImagesApplication).link
        link.open()
        link.state.collect { state ->
            if (state is LinkState.Refused || (state as? LinkState.Open)?.locked == true) {
                finish()
            }
        }
    }

    private fun showInfo(media: Media) {
        lifecycleScope.launch {
            val exif = viewModel.exifOf(media)
            if (!isFinishing) {
                SecureWindows.secure(MediaInfoBottomSheetDialog(this@ViewActivity, media, exif)).show()
            }
        }
    }

    private fun updateSheetsHeight() {
        appBarLayout.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        bottomSheetLinearLayout.measure(
            View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED
        )

        viewModel.setSheetsHeight(
            appBarLayout.measuredHeight,
            bottomSheetLinearLayout.measuredHeight,
        )
    }

    companion object {
        private val LOG_TAG = ViewActivity::class.simpleName!!

        private val dateFormatter = SimpleDateFormat.getDateInstance()
        private val timeFormatter = SimpleDateFormat.getTimeInstance()

        val EXTRA_ALBUM_TYPE = "${ViewActivity::class.qualifiedName}.album_type"
        val EXTRA_ALBUM_URI = "${ViewActivity::class.qualifiedName}.album_uri"
        val EXTRA_MEDIA_TYPE = "${ViewActivity::class.qualifiedName}.media_type"
        val EXTRA_MIME_TYPE = "${ViewActivity::class.qualifiedName}.mime_type"
        val EXTRA_INITIAL_MEDIA_URI = "${ViewActivity::class.qualifiedName}.initial_media_uri"

        /**
         * Create a [Bundle] to use as the extras for this activity.
         * @param albumType The [AlbumType] to display, null to use [albumUri]
         * @param albumUri The [Album] to display's bucket ID, if null, reels will be shown
         * @param fileType The [MediaType] to filter for
         * @param mimeType The MIME type to filter for
         * @param initialMediaUri The [Media] to start from
         */
        fun createBundle(
            albumType: AlbumType? = null,
            albumUri: Uri? = null,
            fileType: MediaType? = null,
            mimeType: String? = null,
            initialMediaUri: Uri? = null,
        ) = bundleOf(
            EXTRA_ALBUM_TYPE to albumType,
            EXTRA_ALBUM_URI to albumUri,
            EXTRA_MEDIA_TYPE to fileType,
            EXTRA_MIME_TYPE to mimeType,
            EXTRA_INITIAL_MEDIA_URI to initialMediaUri,
        )
    }
}
