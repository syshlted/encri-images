/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.testvault

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.systemhalted.encri.client.EncriResult
import com.systemhalted.encri.client.Failure
import com.systemhalted.encri.images.testvault.TestVault.blocking
import com.systemhalted.encri.images.testvault.TestVault.log
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Removes every library [SeedLibrary] made and this package has not yet removed (I6).
 *
 * By the ids the seed wrote down, and by nothing else: never a folder found by its name, which could be the
 * user's. encri never gives an id to a second document (encri-api's `EncriDocument`), so an id written down here
 * names the seed's folder or nothing.
 */
@RunWith(AndroidJUnit4::class)
class RemoveLibrary {
    @Test
    fun remove() {
        val seeded = TestVault.seeded
        val ids = if (seeded.exists()) seeded.readLines().filter(String::isNotBlank) else emptyList()
        if (ids.isEmpty()) {
            log("remove: nothing seeded")
            return
        }
        TestVault.use { session ->
            ids.forEach { id ->
                when (val deleted = blocking(DELETE_MILLIS) { session.delete(id) }) {
                    is EncriResult.Ok -> log("remove: deleted a seeded library")
                    is EncriResult.Failed -> check(deleted.failure == Failure.NOT_FOUND) { "not deleted: $deleted" }
                }
                val after = blocking { session.stat(id) }
                assertEquals(Failure.NOT_FOUND, (after as? EncriResult.Failed)?.failure)
            }
        }
        TestVault.seeded.delete()
    }

    private companion object {
        /** A folder of thousands goes in one request, and encri deletes each document in it. */
        const val DELETE_MILLIS = 600_000L
    }
}
