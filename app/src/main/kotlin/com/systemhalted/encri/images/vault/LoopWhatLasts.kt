/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.vault

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player

/**
 * Glimpse plays a video over and over. A clip of no length, looped, ends as soon as it starts, and the player makes
 * and drops a decoder for it several times a second for as long as it is on screen (seen on the A20, I4).
 *
 * So nothing loops until it is ready and is known to last: each new clip starts with [Player.REPEAT_MODE_OFF], and
 * one whose duration is more than nothing loops once the player has it ready. A clip that never gets ready, or that
 * lasts nothing, plays once and stops.
 */
class LoopWhatLasts(
    private val player: Player,
) : Player.Listener {
    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        // A loop of the same clip is a transition too, and changes nothing.
        if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) player.repeatMode = Player.REPEAT_MODE_OFF
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_READY) player.repeatMode = repeatModeFor(player.duration)
    }

    internal companion object {
        /** Whether a clip of [durationMs] loops: only when it is known, and more than nothing. */
        fun repeatModeFor(durationMs: Long): Int =
            if (durationMs != C.TIME_UNSET && durationMs > 0) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }
}
