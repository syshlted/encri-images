/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.edit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The editor knows the vault in one place, its activity (I23), so that the rest can go to Glimpse and be taken back
 * from it with a store of this app's own. Read from the sources, which the unit tests run beside.
 */
class EditorSeamTest {
    @Test
    fun `nothing in the editor but its activity knows the vault`() {
        val sources = File(EDIT).listFiles { file -> file.extension == "kt" }.orEmpty()
        assertTrue(sources.size > FEWEST_SOURCES, "the editor's sources, from ${File(EDIT).absolutePath}")
        val knowing =
            sources
                .filter { source -> source.readLines().any { VAULT.containsMatchIn(it) && ALLOWED !in it } }
                .map(File::getName)
                .sorted()
        assertEquals(listOf("EditActivity.kt"), knowing)
    }

    private companion object {
        const val EDIT = "src/main/kotlin/com/systemhalted/encri/images/edit"

        /** The vault's package, and encri's client. */
        val VAULT = Regex("""com\.systemhalted\.encri\.(images\.vault|client)\.""")

        /** A player's listener that loops a clip that lasts: in the vault's package, and knowing nothing of it. */
        const val ALLOWED = "images.vault.LoopWhatLasts"

        /** The editor has more files than this, so a wrong directory, holding none of them, is not taken for clean. */
        const val FEWEST_SOURCES = 10
    }
}
