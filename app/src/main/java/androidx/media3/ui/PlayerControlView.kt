/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

// The members are package-private, so the compiler's "shadowed by a member" is wrong from outside this
// package: these are how com.systemhalted.encri.images.ext reaches them.
@file:Suppress("EXTENSION_SHADOWED_BY_MEMBER")

package androidx.media3.ui

@androidx.media3.common.util.UnstableApi
fun PlayerControlView.updateAll() {
    updateAll()
}

@androidx.media3.common.util.UnstableApi
fun PlayerControlView.requestPlayPauseFocus() {
    requestPlayPauseFocus()
}
