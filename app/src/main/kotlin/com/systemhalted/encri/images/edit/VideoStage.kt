/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.edit

import android.content.Context
import android.graphics.Matrix
import android.net.Uri
import android.view.TextureView
import android.widget.Button
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.systemhalted.encri.images.R
import com.systemhalted.encri.images.vault.LoopWhatLasts

/**
 * A video under the editor's frame (I22): played into [surface] by ExoPlayer from [videos], the store's (I23), with
 * nothing reported to the platform (I3), and started and stopped by [button].
 */
@OptIn(UnstableApi::class)
class VideoStage(
    context: Context,
    videos: DataSource.Factory,
    private val surface: TextureView,
    private val button: Button,
) {
    private val player: ExoPlayer =
        ExoPlayer
            .Builder(context)
            .setUsePlatformDiagnostics(false)
            .setMediaSourceFactory(ProgressiveMediaSource.Factory(videos))
            .build()

    /** The size [uri]'s video is shown at, given to [onSized] once ExoPlayer knows it; [onFailed] if it cannot play. */
    fun show(
        uri: Uri,
        onSized: (Size) -> Unit,
        onFailed: () -> Unit,
    ) {
        player.addListener(LoopWhatLasts(player))
        player.addListener(
            object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    // ExoPlayer gives the size the decoder turned it to, which is how it is shown.
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        onSized(Size(videoSize.width * videoSize.pixelWidthHeightRatio, videoSize.height.toFloat()))
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    button.setText(if (isPlaying) R.string.edit_pause else R.string.edit_play)
                    button.contentDescription = button.text
                    val icon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
                    button.setCompoundDrawablesRelativeWithIntrinsicBounds(0, icon, 0, 0)
                }

                override fun onPlayerError(error: PlaybackException) = onFailed()
            },
        )
        player.setVideoTextureView(surface)
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        button.setOnClickListener { player.playWhenReady = !player.playWhenReady }
    }

    /**
     * The video's pixels, of a video [source] big as it is shown, drawn where [placement] puts them. A `TextureView`
     * stretches a video over itself, so that is undone first.
     */
    fun place(
        placement: Matrix,
        source: Size,
    ) {
        if (surface.width == 0 || surface.height == 0) return
        val transform = Matrix()
        transform.setScale(source.width / surface.width, source.height / surface.height)
        transform.postConcat(placement)
        surface.setTransform(transform)
    }

    fun pause() = player.pause()

    fun release() = player.release()
}
