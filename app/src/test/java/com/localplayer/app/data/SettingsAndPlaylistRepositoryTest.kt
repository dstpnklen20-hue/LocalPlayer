package com.localplayer.app.data

import app.cash.turbine.test
import com.localplayer.app.data.model.AppSettings
import com.localplayer.app.data.model.ThemeMode
import com.localplayer.app.data.model.TrackSort
import com.localplayer.app.data.model.TrackSortField
import com.localplayer.app.data.prefs.SessionRepository
import com.localplayer.app.data.prefs.SettingsRepository
import com.localplayer.app.testutil.DirectTransactionRunner
import com.localplayer.app.testutil.FakePlaylistDao
import com.localplayer.app.testutil.testDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `defaults match the spec`() = runTest {
        val repo = SettingsRepository(testDataStore(tmp.root, backgroundScope))
        val s = repo.settings.first()
        assertEquals(AppSettings(), s)
        assertEquals(30, s.minDurationSec)
        assertTrue(s.pauseOnDisconnect) // F22: вкл по умолчанию
        assertFalse(s.resumeOnBluetooth) // F22: выкл по умолчанию
    }

    @Test
    fun `values are persisted`() = runTest {
        val repo = SettingsRepository(testDataStore(tmp.root, backgroundScope))
        repo.settings.test {
            awaitItem()
            repo.setThemeMode(ThemeMode.DARK)
            assertEquals(ThemeMode.DARK, awaitItem().themeMode)
            repo.setTrackSort(TrackSort(TrackSortField.DURATION, ascending = false))
            assertEquals(TrackSort(TrackSortField.DURATION, false), awaitItem().trackSort)
            repo.addExcludedFolder("WhatsApp/Media")
            assertEquals(setOf("WhatsApp/Media"), awaitItem().excludedFolders)
            repo.removeExcludedFolder("WhatsApp/Media")
            assertTrue(awaitItem().excludedFolders.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }
}

class SessionRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `session round trip`() = runTest {
        val repo = SessionRepository(testDataStore(tmp.root, backgroundScope, "session"))
        assertNull(repo.load())
        repo.saveQueue(listOf(5, 7, 9))
        repo.savePosition(index = 1, positionMs = 42_000, shuffle = true, repeatMode = 2)
        val saved = repo.load()!!
        assertEquals(listOf(5L, 7L, 9L), saved.queueIds)
        assertEquals(1, saved.currentIndex)
        assertEquals(42_000L, saved.positionMs)
        assertTrue(saved.shuffle)
        assertEquals(2, saved.repeatMode)
    }
}

class PlaylistRepositoryTest {

    private val repo = PlaylistRepository(FakePlaylistDao(), DirectTransactionRunner)

    @Test
    fun `adding tracks skips duplicates and keeps order`() = runTest {
        val id = repo.create("  Rock  ")
        repo.addTracks(id, listOf(3, 1, 3))
        repo.addTracks(id, listOf(1, 2))
        assertEquals(listOf(3L, 1L, 2L), repo.observeTrackIds(id).first())
        assertEquals("Rock", repo.playlists.first().single().name)
    }

    @Test
    fun `reorder and remove`() = runTest {
        val id = repo.create("Mix")
        repo.addTracks(id, listOf(1, 2, 3))
        repo.reorder(id, listOf(3, 1, 2))
        assertEquals(listOf(3L, 1L, 2L), repo.observeTrackIds(id).first())
        repo.removeTrack(id, 1)
        assertEquals(listOf(3L, 2L), repo.observeTrackIds(id).first())
    }

    @Test
    fun `favorites toggle and cannot be deleted or renamed`() = runTest {
        repo.observeIsFavorite(7).test {
            assertFalse(awaitItem())
            repo.toggleFavorite(7)
            assertTrue(awaitItem())
            repo.toggleFavorite(7)
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        repo.delete(PlaylistRepository.FAVORITES_ID)
        repo.rename(PlaylistRepository.FAVORITES_ID, "x")
        val favorites = repo.favoritesSummary.first()!!
        assertEquals("", favorites.name)
        // «Избранное» не попадает в список пользовательских плейлистов
        assertTrue(repo.playlists.first().none { it.id == PlaylistRepository.FAVORITES_ID })
    }

    @Test
    fun `most played sorted by count`() = runTest {
        repeat(3) { repo.recordPlay(2, now = it.toLong()) }
        repo.recordPlay(1, now = 10)
        repeat(2) { repo.recordPlay(3, now = it.toLong()) }
        assertEquals(listOf(2L, 3L, 1L), repo.observeMostPlayed().first())
    }

    @Test
    fun `deleted files are removed from all playlists`() = runTest {
        val a = repo.create("A")
        val b = repo.create("B")
        repo.addTracks(a, listOf(1, 2))
        repo.addTracks(b, listOf(2, 3))
        repo.removeTracksEverywhere(listOf(2))
        assertEquals(listOf(1L), repo.observeTrackIds(a).first())
        assertEquals(listOf(3L), repo.observeTrackIds(b).first())
    }
}
