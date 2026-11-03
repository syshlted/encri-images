/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.vault

import android.app.Application
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class VaultUrisTest {
    @Test
    fun `a document id comes back as it went in, whatever it holds`() {
        val id = "a/b c?d#e%20"
        assertEquals(id, VaultUris.documentIdOf(VaultUris.document(id)))
    }

    @Test
    fun `a folder is not a document`() {
        assertNull(VaultUris.documentIdOf(VaultUris.folder("f")))
    }

    @Test
    fun `a uri from anywhere else is not a document`() {
        assertNull(VaultUris.documentIdOf(Uri.parse("content://media/external/document/1")))
        assertNull(VaultUris.documentIdOf(Uri.parse("https://example.com/document/1")))
    }
}
