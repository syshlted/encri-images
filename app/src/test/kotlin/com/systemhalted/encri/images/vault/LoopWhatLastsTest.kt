/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.vault

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

class LoopWhatLastsTest {
    /** A player that answers only what [LoopWhatLasts] asks of it, and remembers the repeat mode it was given. */
    private class Clip(
        var durationMs: Long,
        initialRepeatMode: Int = Player.REPEAT_MODE_ONE,
    ) {
        var repeatMode = initialRepeatMode

        val player =
            Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, args ->
                when (method.name) {
                    "getDuration" -> durationMs
                    "setRepeatMode" -> repeatMode = args[0] as Int
                    else -> error("not asked of a player: ${method.name}")
                }
            } as Player

        val loop = LoopWhatLasts(player)
    }

    @Test
    fun `a clip that lasts loops once it is ready`() {
        val clip = Clip(durationMs = 8_000, initialRepeatMode = Player.REPEAT_MODE_OFF)
        clip.loop.onPlaybackStateChanged(Player.STATE_READY)
        assertEquals(Player.REPEAT_MODE_ONE, clip.repeatMode)
    }

    @Test
    fun `a clip of no length does not loop, so the player is not asked to start it again and again`() {
        val clip = Clip(durationMs = 0)
        clip.loop.onPlaybackStateChanged(Player.STATE_READY)
        assertEquals(Player.REPEAT_MODE_OFF, clip.repeatMode)
    }

    @Test
    fun `a clip whose length is not known does not loop`() {
        val clip = Clip(durationMs = C.TIME_UNSET)
        clip.loop.onPlaybackStateChanged(Player.STATE_READY)
        assertEquals(Player.REPEAT_MODE_OFF, clip.repeatMode)
    }

    @Test
    fun `a new clip stops the last one's loop before it is ready, since one that never gets ready would spin`() {
        val clip = Clip(durationMs = 8_000)
        clip.loop.onPlaybackStateChanged(Player.STATE_READY)

        clip.durationMs = 0
        clip.loop.onMediaItemTransition(MediaItem.Builder().setMediaId("next").build(), PLAYLIST_CHANGED)
        assertEquals(Player.REPEAT_MODE_OFF, clip.repeatMode)
        clip.loop.onPlaybackStateChanged(Player.STATE_BUFFERING)
        clip.loop.onPlaybackStateChanged(Player.STATE_ENDED)
        assertEquals(Player.REPEAT_MODE_OFF, clip.repeatMode)
    }

    @Test
    fun `a clip looping is a transition that leaves its loop alone`() {
        val clip = Clip(durationMs = 8_000)
        clip.loop.onPlaybackStateChanged(Player.STATE_READY)
        clip.loop.onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)
        assertEquals(Player.REPEAT_MODE_ONE, clip.repeatMode)
    }

    private companion object {
        const val PLAYLIST_CHANGED = Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED
    }
}
