/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.vault

/**
 * What a screen of this app's own reaches the vault through: the application's one link (I7), and the library over it.
 *
 * The application is the host. A screen asks for this rather than for the application itself, so its tests can stand
 * in an application of their own, with a link to a stand-in encri.
 */
interface VaultHost {
    val link: VaultLink
    val library: VaultLibrary
}
