/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.ext

import androidx.media3.common.Player
import kotlinx.coroutines.channels.awaitClose

fun Player.isPlayingFlow() = conflatedCallbackFlow {
    val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            trySend(isPlaying)
        }
    }

    addListener(listener)
    trySend(isPlaying)

    awaitClose {
        removeListener(listener)
    }
}
