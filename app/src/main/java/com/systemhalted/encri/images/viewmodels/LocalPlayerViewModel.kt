/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.exifinterface.media.ExifInterface
import androidx.preference.PreferenceManager
import com.systemhalted.encri.client.valueOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import com.systemhalted.encri.images.ext.applicationContext
import com.systemhalted.encri.images.ext.doubleTapToSeekEnabled
import com.systemhalted.encri.images.ext.doubleTapToSeekSeconds
import com.systemhalted.encri.images.ext.edgeTapNavigationEnabled
import com.systemhalted.encri.images.ext.getVideoPlaybackPosition
import com.systemhalted.encri.images.ext.hideNativeSeekButtons
import com.systemhalted.encri.images.ext.isPlayingFlow
import com.systemhalted.encri.images.ext.rememberVideoPlaybackPositionEnabled
import com.systemhalted.encri.images.ext.removeVideoPlaybackPosition
import com.systemhalted.encri.images.ext.setVideoPlaybackPosition
import com.systemhalted.encri.images.models.AlbumType
import com.systemhalted.encri.images.models.Media
import com.systemhalted.encri.images.models.MediaType
import com.systemhalted.encri.images.models.MotionPhoto
import com.systemhalted.encri.images.models.RequestStatus
import com.systemhalted.encri.images.models.RequestStatus.Companion.map
import com.systemhalted.encri.images.utils.MotionPhotoExtractor
import com.systemhalted.encri.images.utils.media3.ByteBufferDataSource
import com.systemhalted.encri.images.vault.LoopWhatLasts
import com.systemhalted.encri.images.vault.VaultMediaSource
import com.systemhalted.encri.images.vault.VaultUris
import com.systemhalted.encri.images.vault.initialPositionIn
import java.io.FileInputStream

@androidx.annotation.OptIn(UnstableApi::class)
class LocalPlayerViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : ImagesViewModel(application) {
    // Shared preferences
    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(
        applicationContext
    )

    // ExoPlayer
    @androidx.annotation.OptIn(UnstableApi::class)
    val exoPlayer = ExoPlayer.Builder(applicationContext)
        // Nothing but the vault: no cache, and nothing that can reach a network (I3).
        .setMediaSourceFactory(ProgressiveMediaSource.Factory(VaultMediaSource.Factory(link)))
        // Nor anything about what plays to the platform's media metrics, which Google may collect (I3).
        .setUsePlatformDiagnostics(false)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            true
        )
        .setHandleAudioBecomingNoisy(true)
        .build()
        .apply {
            // Looped as Glimpse loops a video, but only once it is known to last (ROADMAP.md).
            addListener(LoopWhatLasts(this))
        }

    val isPlaying = exoPlayer.isPlayingFlow()
        .flowOn(Dispatchers.Main)
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = false
        )

    /**
     * What to show, and where in it to start.
     */
    data class ViewRequest(
        val albumRequest: AlbumViewModel.AlbumRequest,
        val initialMediaUri: Uri?,
    )

    private val viewRequest = MutableStateFlow<ViewRequest?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val medias = viewRequest
        .filterNotNull()
        .flatMapLatest {
            val albumRequest = it.albumRequest
            when (albumRequest.albumType) {
                AlbumType.REELS -> mediaRepository.reels(albumRequest.mediaType)

                else -> albumRequest.albumUri?.let { albumUri ->
                    mediaRepository.album(albumUri).mapLatest { album ->
                        album.map { data -> data.second }
                    }
                } ?: flowOf(RequestStatus.Loading())
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = RequestStatus.Loading(),
        )

    /**
     * The latest media position.
     */
    private val _mediaPosition = savedStateHandle.getLiveData<Int?>(MEDIA_POSITION_KEY)
    val mediaPosition = savedStateHandle.getStateFlow<Int?>(MEDIA_POSITION_KEY, null)

    /**
     * The current height of top and bottom sheets, used to apply padding to media view UI.
     */
    private val _sheetsHeight = MutableStateFlow(0 to 0)
    val sheetsHeight = _sheetsHeight.asStateFlow()

    /**
     * Fullscreen mode, set by the user with a single tap on the viewed media.
     */
    private val _fullscreenMode = MutableStateFlow(false)
    val fullscreenMode = _fullscreenMode.asStateFlow()

    // The first position is read from the request itself, which is set before anything collects
    // this. A flow of its own could lose the race to a library already in memory, and the viewer
    // would open at the newest media instead of the one that was tapped.
    val mediasWithInitialPosition = combine(
        medias,
        mediaPosition,
    ) { medias, mediaPosition ->
        medias.map { data ->
            val newMediaPosition = when (mediaPosition) {
                null -> initialPositionIn(data, viewRequest.value?.initialMediaUri)

                else -> null
            }

            data to newMediaPosition
        }
    }

    /**
     * The currently displayed media.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val displayedMedia = combine(medias, mediaPosition.filterNotNull()) { medias, mediaPosition ->
        medias.map { it.getOrNull(mediaPosition) }
    }
        .flatMapLatest {
            flow {
                when (it) {
                    is RequestStatus.Loading -> {
                        // Do nothing
                    }

                    is RequestStatus.Success -> emit(it.data)

                    is RequestStatus.Error -> emit(null)
                }
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = null,
        )

    /**
     * Motion photo contained in the displayed media.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val motionPhoto = displayedMedia
        .mapLatest { media ->
            media?.takeIf { it.mediaType == MediaType.IMAGE }?.let {
                VaultUris.documentIdOf(it.uri)?.let { documentId ->
                    link.ask { session -> session.openRead(documentId) }.valueOrNull()
                }?.let(MotionPhotoExtractor::extractMotionPhoto)
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = null,
        )

    /**
     * Whether we're currently playing a motion photo video.
     */
    private val motionPhotoEnabled = MutableStateFlow(false)

    /**
     * Displayed media to motion photo if present and requested.
     */
    val displayedMediaToMotionPhoto = combine(
        displayedMedia,
        motionPhoto,
        motionPhotoEnabled
    ) { displayedMedia, motionPhoto, motionPhotoEnabled ->
        displayedMedia to motionPhoto.takeIf { motionPhotoEnabled }
    }
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = null to null,
        )

    val doubleTapToSeekEnabled: Boolean
        get() = sharedPreferences.doubleTapToSeekEnabled

    val doubleTapToSeekSeconds: Int
        get() = sharedPreferences.doubleTapToSeekSeconds

    val hideNativeSeekButtons: Boolean
        get() = sharedPreferences.hideNativeSeekButtons

    val edgeTapNavigationEnabled: Boolean
        get() = sharedPreferences.edgeTapNavigationEnabled

    override fun onCleared() {
        exoPlayer.release()

        super.onCleared()
    }

    fun setViewRequest(viewRequest: ViewRequest) {
        this.viewRequest.value = viewRequest
    }

    /**
     * What the file says about itself, read off the main thread, or null when there is nothing to read.
     */
    suspend fun exifOf(media: Media): ExifInterface? = withContext(Dispatchers.IO) {
        VaultUris.documentIdOf(media.uri)?.let { documentId ->
            link.ask { session -> session.openRead(documentId) }.valueOrNull()
        }?.use { descriptor ->
            runCatching {
                FileInputStream(descriptor.fileDescriptor).use { ExifInterface(it) }
            }.getOrNull()
        }
    }

    /**
     * Set the updated media position.
     */
    fun setMediaPosition(mediaPosition: Int) {
        _mediaPosition.value = mediaPosition
    }

    /**
     * Set the sheets height.
     */
    fun setSheetsHeight(top: Int, bottom: Int) {
        _sheetsHeight.value = top to bottom
    }

    /**
     * Toggle fullscreen mode.
     */
    fun toggleFullscreenMode() {
        _fullscreenMode.value = _fullscreenMode.value.not()
    }

    fun setCurrentVideoUri(uri: Uri) {
        val startPositionMs = when (sharedPreferences.rememberVideoPlaybackPositionEnabled) {
            true -> sharedPreferences.getVideoPlaybackPosition(uri)
            false -> 0L
        }

        exoPlayer.apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            if (startPositionMs > 0L) {
                seekTo(startPositionMs)
            }
            playWhenReady = true
        }
    }

    /**
     * A deleted video's place is not kept (I11), though encri never gives its id to another.
     */
    fun forgetVideoPosition(uri: Uri) {
        sharedPreferences.removeVideoPlaybackPosition(uri)
    }

    fun saveCurrentVideoPosition(currentVideoUri: Uri?) {
        val currentVideoUri = currentVideoUri ?: return

        if (!sharedPreferences.rememberVideoPlaybackPositionEnabled) {
            return
        }

        val currentPosition = exoPlayer.currentPosition
        val duration = exoPlayer.duration
        val nearEnd = duration > 0L &&
                currentPosition >= duration - FINISHED_VIDEO_POSITION_TOLERANCE_MS

        when {
            currentPosition <= 0L -> sharedPreferences.removeVideoPlaybackPosition(currentVideoUri)
            nearEnd -> sharedPreferences.removeVideoPlaybackPosition(currentVideoUri)
            else -> sharedPreferences.setVideoPlaybackPosition(
                currentVideoUri, currentPosition
            )
        }
    }

    fun toggleMotionPhotoEnabled(motionPhotoEnabled: Boolean = !this.motionPhotoEnabled.value) {
        this.motionPhotoEnabled.value = motionPhotoEnabled
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    fun playMotionPhoto(motionPhoto: MotionPhoto) {
        val dataSourceFactory = ByteBufferDataSource.Factory(motionPhoto.videoBuffer)
        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri("motion-photo://video"))

        exoPlayer.apply {
            setMediaSource(mediaSource)
            prepare()
            motionPhoto.metadata.presentationTimestampUs?.let {
                seekTo(it / 1000)
            }
            playWhenReady = true
        }
    }

    /**
     * @see ExoPlayer.play
     */
    fun play() {
        exoPlayer.play()
    }

    /**
     * @see ExoPlayer.pause
     */
    fun pause() {
        exoPlayer.pause()
    }

    /**
     * @see ExoPlayer.stop
     */
    fun stop() {
        exoPlayer.stop()
    }

    companion object {
        private const val MEDIA_POSITION_KEY = "media_position"

        private const val FINISHED_VIDEO_POSITION_TOLERANCE_MS = 2_000L
    }
}
