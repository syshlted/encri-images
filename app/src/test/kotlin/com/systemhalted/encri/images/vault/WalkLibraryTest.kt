/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

package com.systemhalted.encri.images.vault

import android.app.Application
import com.systemhalted.encri.client.EncriDocument
import com.systemhalted.encri.client.EncriResult
import com.systemhalted.encri.client.Failure
import com.systemhalted.encri.images.datasources.MediaError
import com.systemhalted.encri.images.models.MediaType
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class WalkLibraryTest {
    private val tree =
        mapOf(
            "root" to listOf(document("old", modified = 1), folder("trip", "Trip"), document("notes", "text/plain")),
            "trip" to
                listOf(
                    document("beach", modified = 5, parentId = "trip"),
                    document("film", "video/mp4", modified = 3, parentId = "trip"),
                    folder("deep", "Deep", "trip"),
                ),
            "deep" to listOf(document("cave", modified = 9, parentId = "deep")),
        )

    private suspend fun walked(session: FakeVaultSession) = (walkLibrary(session) as EncriResult.Ok).value

    @Test
    fun `everything at any depth is found, newest first`() =
        runTest {
            val library = walked(FakeVaultSession(tree = tree))
            assertEquals(
                listOf("cave", "beach", "film", "old"),
                library.medias.map { VaultUris.documentIdOf(it.uri) },
            )
        }

    @Test
    fun `a document that is neither a photo nor a video is left out`() =
        runTest {
            val library = walked(FakeVaultSession(tree = tree))
            assertEquals(0, library.medias.count { it.displayName == "notes.name" })
        }

    @Test
    fun `a photo and a video are told apart by type`() =
        runTest {
            val library = walked(FakeVaultSession(tree = tree))
            assertEquals(MediaType.VIDEO, library.medias.single { it.mimeType == "video/mp4" }.mediaType)
        }

    @Test
    fun `an album is a folder holding something directly, led by its newest`() =
        runTest {
            val library = walked(FakeVaultSession(tree = tree))
            assertEquals(listOf("Deep", "Trip", null), library.albums.map { it.name })
            val trip = library.albums.single { it.name == "Trip" }
            assertEquals(2, trip.mediaCount)
            assertEquals(VaultUris.document("beach"), trip.thumbnail?.uri)
            assertEquals(2, library.mediasIn(trip.uri).size)
        }

    @Test
    fun `the root album has no name of its own to show`() =
        runTest {
            val library = walked(FakeVaultSession(tree = tree))
            assertNull(library.albums.single { it.uri == VaultUris.folder("root") }.name)
        }

    @Test
    fun `a folder with nothing to show makes no album`() =
        runTest {
            val session = FakeVaultSession(tree = mapOf("root" to listOf(folder("empty"))))
            assertEquals(emptyList<Any>(), walked(session).albums)
        }

    @Test
    fun `a folder deleted during the walk is not a failure`() =
        runTest {
            val session = FakeVaultSession(tree = tree).apply { listFailures["trip"] = Failure.NOT_FOUND }
            assertEquals(listOf("old"), walked(session).medias.map { VaultUris.documentIdOf(it.uri) })
        }

    @Test
    fun `the grant's own folder gone is the session's end, not an empty vault`() =
        runTest {
            val session = FakeVaultSession(tree = tree).apply { listFailures["root"] = Failure.NOT_FOUND }
            assertEquals(Failure.ENDED, (walkLibrary(session) as EncriResult.Failed).failure)
        }

    @Test
    fun `any other failure ends the walk with that failure`() =
        runTest {
            val session = FakeVaultSession(tree = tree).apply { listFailures["trip"] = Failure.LOCKED }
            assertEquals(Failure.LOCKED, (walkLibrary(session) as EncriResult.Failed).failure)
        }

    @Test
    fun `each failure reads as an error the screens know`() {
        assertEquals(MediaError.NOT_FOUND, mediaErrorOf(Failure.NOT_FOUND))
        listOf(Failure.LOCKED, Failure.NOT_AUTHORIZED, Failure.ACCESS_OFF, Failure.ENDED).forEach {
            assertEquals(MediaError.AUTHENTICATION_REQUIRED, mediaErrorOf(it))
        }
        assertEquals(MediaError.IO, mediaErrorOf(Failure.DISCONNECTED))
    }

    /** [inner], whose every listing takes a while, counting how many are under way at once. */
    private class Slow(
        private val inner: FakeVaultSession,
    ) : VaultSession by inner {
        private var listing = 0
        var most = 0

        override suspend fun list(folderId: String): EncriResult<List<EncriDocument>> {
            most = maxOf(most, ++listing)
            delay(10)
            return inner.list(folderId).also { listing-- }
        }
    }

    private val wide =
        mapOf("root" to List(10) { folder("album$it", "Album $it") }) +
            List(10) { "album$it" to listOf(document("photo$it", modified = it.toLong(), parentId = "album$it")) }

    @Test
    fun `several folders are listed at once, and never more than asked`() =
        runTest {
            val slow = Slow(FakeVaultSession(tree = wide))
            walkLibrary(slow, atOnce = 4)
            assertEquals(4, slow.most)

            val one = Slow(FakeVaultSession(tree = wide))
            walkLibrary(one, atOnce = 1)
            assertEquals(1, one.most)
        }

    @Test
    fun `what a walk finds does not depend on how many folders it lists at once`() =
        runTest {
            assertEquals(walkLibrary(FakeVaultSession(tree = tree), 1), walkLibrary(FakeVaultSession(tree = tree), 8))
            val one = walkLibrary(Slow(FakeVaultSession(tree = wide)), atOnce = 1)
            val four = walkLibrary(Slow(FakeVaultSession(tree = wide)), atOnce = 4)
            assertEquals(10, (four as EncriResult.Ok).value.albums.size)
            assertEquals(one, four)
        }
}
