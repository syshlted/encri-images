/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.vault

import android.app.Application
import com.systemhalted.encri.images.models.Media
import com.systemhalted.encri.images.models.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SelectionTest {
    private val selection = Selection()

    private fun media(id: String) =
        Media(
            uri = VaultUris.document(id),
            mediaType = MediaType.IMAGE,
            mimeType = "image/jpeg",
            albumUri = VaultUris.folder("root"),
            albumName = null,
            displayName = "$id.jpg",
            dateAdded = Date(0),
            dateModified = Date(0),
            sizeBytes = 1,
        )

    private val grid = listOf(media("a"), media("b"), media("c"))

    @Test
    fun `nothing is chosen until something is, and choosing it again lets it go`() {
        assertFalse(selection.active)

        selection.toggle(grid[1].uri)
        assertTrue(selection.active)
        assertEquals(setOf(grid[1].uri), selection.uris.value)

        selection.toggle(grid[1].uri)
        assertFalse(selection.active)
    }

    @Test
    fun `what is chosen comes back in the grid's order, whatever order it was chosen in`() {
        selection.toggle(grid[2].uri)
        selection.toggle(grid[0].uri)
        assertEquals(listOf(grid[0], grid[2]), selection.of(grid))
    }

    @Test
    fun `what the grid no longer shows leaves the selection`() {
        grid.forEach { selection.toggle(it.uri) }
        selection.keepOnly(listOf(grid[0].uri, grid[2].uri))
        assertEquals(setOf(grid[0].uri, grid[2].uri), selection.uris.value)

        selection.clear()
        assertFalse(selection.active)
    }
}
