package com.localplayer.app.data.mediastore

import com.localplayer.app.testutil.track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryBuilderTest {

    @Test
    fun `filter hides tracks shorter than threshold`() {
        val tracks = listOf(
            track(1, durationMs = 29_999),
            track(2, durationMs = 30_000),
            track(3, durationMs = 5_000),
        )
        val result = LibraryBuilder.filter(tracks, minDurationSec = 30, excludedFolders = emptySet())
        assertEquals(listOf(2L), result.map { it.id })
    }

    @Test
    fun `filter with zero threshold keeps everything`() {
        val tracks = listOf(track(1, durationMs = 1_000), track(2, durationMs = 0))
        assertEquals(2, LibraryBuilder.filter(tracks, 0, emptySet()).size)
    }

    @Test
    fun `excluded folder matches relative and absolute paths by whole segments`() {
        val whatsapp = "/storage/emulated/0/WhatsApp/Media/WhatsApp Audio"
        assertTrue(LibraryBuilder.isInFolder(whatsapp, "WhatsApp/Media"))
        assertTrue(LibraryBuilder.isInFolder(whatsapp, "/storage/emulated/0/WhatsApp"))
        assertTrue(LibraryBuilder.isInFolder(whatsapp, "WhatsApp/Media/"))
        // Совпадение по части имени папки не считается
        assertFalse(LibraryBuilder.isInFolder("/storage/emulated/0/WhatsAppBackup", "WhatsApp"))
        assertFalse(LibraryBuilder.isInFolder("/storage/emulated/0/Music", "WhatsApp/Media"))
    }

    @Test
    fun `albums are grouped by id and sorted by disc and track number`() {
        val tracks = listOf(
            track(1, album = "B", albumId = 10, trackNumber = 2),
            track(2, album = "B", albumId = 10, trackNumber = 1),
            track(3, album = "A", albumId = 20, trackNumber = 1),
        )
        val library = LibraryBuilder.build(tracks)
        assertEquals(listOf("A", "B"), library.albums.map { it.title })
        assertEquals(listOf(2L, 1L), library.albumsById.getValue(10).tracks.map { it.id })
        assertEquals(2L, library.albumsById.getValue(10).coverTrackId)
    }

    @Test
    fun `album artist is the most frequent artist`() {
        val tracks = listOf(
            track(1, artist = "X", albumId = 1),
            track(2, artist = "Y", albumId = 1),
            track(3, artist = "Y", albumId = 1),
        )
        assertEquals("Y", LibraryBuilder.build(tracks).albums.single().artist)
    }

    @Test
    fun `artists count distinct albums`() {
        val tracks = listOf(
            track(1, artist = "X", albumId = 1),
            track(2, artist = "X", albumId = 2),
            track(3, artist = "Z", albumId = 3),
        )
        val library = LibraryBuilder.build(tracks)
        assertEquals(2, library.artistsByName.getValue("X").albumCount)
        assertEquals(listOf("X", "Z"), library.artists.map { it.name })
    }

    @Test
    fun `folder tree collapses common prefix and keeps nesting`() {
        val tracks = listOf(
            track(1, folder = "/storage/emulated/0/Music/Rock"),
            track(2, folder = "/storage/emulated/0/Music/Rock/Live"),
            track(3, folder = "/storage/emulated/0/Music/Jazz"),
        )
        val library = LibraryBuilder.build(tracks)
        assertNotNull(library.folderRoot)
        val root = checkNotNull(library.folderRoot)
        assertEquals("/storage/emulated/0/Music", root.path)
        assertEquals(listOf("Jazz", "Rock"), root.subfolders.map { it.name })
        val rock = library.foldersByPath.getValue("/storage/emulated/0/Music/Rock")
        assertEquals(2, rock.totalTracks)
        assertEquals(listOf(1L), rock.tracks.map { it.id })
        assertEquals(setOf(1L, 2L), rock.allTracks().map { it.id }.toSet())
        assertEquals(3, root.totalTracks)
    }

    @Test
    fun `empty library has no folder root`() {
        val library = LibraryBuilder.build(emptyList())
        assertEquals(null, library.folderRoot)
        assertTrue(library.albums.isEmpty())
    }
}
