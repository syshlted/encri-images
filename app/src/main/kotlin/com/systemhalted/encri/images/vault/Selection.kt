/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.vault

import android.net.Uri
import com.systemhalted.encri.images.models.Media
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The photos and videos chosen in a grid, for one share or one delete (AGENTS.md I16).
 *
 * Held by the grid's view model, so a rotation keeps it, and in memory alone, as the library is (I3). It holds only
 * what the grid still shows: a photo deleted, or the vault locked, leaves it at once.
 */
class Selection {
    private val chosen = MutableStateFlow<Set<Uri>>(emptySet())

    /** What is chosen, by the URI each is known by inside this app (I4). */
    val uris: StateFlow<Set<Uri>> = chosen.asStateFlow()

    /** Whether anything is chosen, which is when a tap chooses rather than opens. */
    val active: Boolean get() = chosen.value.isNotEmpty()

    /** Chooses [uri], or lets it go if it was chosen. */
    fun toggle(uri: Uri) = chosen.update { if (uri in it) it - uri else it + uri }

    fun clear() {
        chosen.value = emptySet()
    }

    /** Lets go of whatever [shown] no longer holds. */
    fun keepOnly(shown: Collection<Uri>) = chosen.update { it.intersect(shown.toSet()) }

    /** The chosen ones among [medias], in the grid's order. */
    fun of(medias: List<Media>): List<Media> = chosen.value.let { uris -> medias.filter { it.uri in uris } }
}
