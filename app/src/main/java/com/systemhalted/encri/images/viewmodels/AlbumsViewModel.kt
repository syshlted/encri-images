/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.viewmodels

import android.app.Application
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import com.systemhalted.encri.images.models.MediaType
import com.systemhalted.encri.images.models.RequestStatus

class AlbumsViewModel(application: Application) : ImagesViewModel(application) {
    data class AlbumsRequest(
        val mediaType: MediaType? = null,
        val mimeType: String? = null,
    )

    private val _albumsRequest = MutableStateFlow<AlbumsRequest?>(null)
    val albumsRequest = _albumsRequest.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val albums = albumsRequest
        .filterNotNull()
        .flatMapLatest { albumsRequest ->
            mediaRepository.albums(
                albumsRequest.mediaType,
                albumsRequest.mimeType,
            )
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = RequestStatus.Loading()
        )

    fun loadAlbums(albumsRequest: AlbumsRequest?) {
        _albumsRequest.value = albumsRequest
    }
}
