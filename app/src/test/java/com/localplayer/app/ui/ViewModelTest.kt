package com.localplayer.app.ui

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.localplayer.app.data.mediastore.LibraryBuilder
import com.localplayer.app.data.mediastore.MediaStoreRepository
import com.localplayer.app.data.model.TrackSort
import com.localplayer.app.data.model.TrackSortField
import com.localplayer.app.data.prefs.SettingsRepository
import com.localplayer.app.playback.SleepTimer
import com.localplayer.app.testutil.FakeMediaStoreSource
import com.localplayer.app.testutil.MainDispatcherRule
import com.localplayer.app.testutil.testDataStore
import com.localplayer.app.testutil.track
import com.localplayer.app.ui.components.IndexEntry
import com.localplayer.app.ui.screens.library.LibraryViewModel
import com.localplayer.app.ui.screens.search.SearchViewModel
import com.localplayer.app.util.formatDuration
import com.localplayer.app.util.indexLetter
import com.localplayer.app.util.normalized
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val tracks = listOf(
        track(1, title = "ёлка", artist = "B", album = "Z", durationMs = 200_000, dateAdded = 3),
        track(2, title = "Апрель", artist = "A", album = "Y", durationMs = 100_000, dateAdded = 1),
        track(3, title = "beta", artist = "C", album = "X", durationMs = 300_000, dateAdded = 2),
        track(4, title = "Ель", artist = "A", album = "Y", durationMs = 150_000, dateAdded = 4),
    )

    @Test
    fun `sorting by every field`() {
        fun ids(field: TrackSortField, asc: Boolean = true) =
            LibraryViewModel.sortTracks(tracks, TrackSort(field, asc)).map { it.id }

        // ё сортируется как е («елка» < «ель»), регистр не важен; латиница раньше кириллицы
        assertEquals(listOf(3L, 2L, 1L, 4L), ids(TrackSortField.TITLE))
        assertEquals(listOf(4L, 1L, 2L, 3L), ids(TrackSortField.TITLE, asc = false))
        assertEquals(listOf(2L, 4L, 1L, 3L), ids(TrackSortField.ARTIST))
        assertEquals(listOf(4L, 1L, 3L, 2L), ids(TrackSortField.DATE_ADDED, asc = false))
        assertEquals(listOf(2L, 4L, 1L, 3L), ids(TrackSortField.DURATION))
    }

    @Test
    fun `alphabet index only for string sorts`() {
        val sorted = LibraryViewModel.sortTracks(tracks, TrackSort(TrackSortField.TITLE))
        assertEquals(
            listOf(IndexEntry("B", 0), IndexEntry("А", 1), IndexEntry("Е", 2)),
            LibraryViewModel.trackIndex(sorted, TrackSort(TrackSortField.TITLE)),
        )
        assertTrue(LibraryViewModel.trackIndex(sorted, TrackSort(TrackSortField.DURATION)).isEmpty())
    }

    @Test
    fun `ui state follows saved sort`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val settings = SettingsRepository(testDataStore(tmp.root, backgroundScope))
        val repo = MediaStoreRepository(FakeMediaStoreSource(tracks), settings, backgroundScope, dispatcher)
        val vm = LibraryViewModel(repo, settings, dispatcher)

        vm.uiState.test {
            var state = awaitItem()
            while (state.loading) state = awaitItem()
            assertEquals(listOf(3L, 2L, 1L, 4L), state.tracks.map { it.id })

            vm.setSort(TrackSort(TrackSortField.DURATION, ascending = false))
            state = awaitItem()
            while (state.sort.field != TrackSortField.DURATION) state = awaitItem()
            assertEquals(listOf(3L, 1L, 4L, 2L), state.tracks.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val library = LibraryBuilder.build(
        listOf(
            track(1, title = "Ёжик в тумане", artist = "Сказки", album = "Мультфильмы", albumId = 1),
            track(2, title = "Звезда", artist = "Кино", album = "Группа крови", albumId = 2),
            track(3, title = "Кукушка", artist = "Кино", album = "Чёрный альбом", albumId = 3),
        ),
    )

    @Test
    fun `search ignores case and yo`() {
        assertEquals(listOf(1L), SearchViewModel.search(library, "ЕЖИК").tracks.map { it.id })
        assertEquals(listOf(3L), SearchViewModel.search(library, "черный").tracks.map { it.id })
    }

    @Test
    fun `results are grouped by type`() {
        val r = SearchViewModel.search(library, "кино")
        assertEquals(listOf("Кино"), r.artists.map { it.name })
        assertEquals(2, r.tracks.size)
        assertEquals(2, r.albums.size) // альбомы исполнителя тоже находятся
    }

    @Test
    fun `blank query gives empty results`() {
        assertTrue(SearchViewModel.search(library, "   ").isEmpty)
    }

    @Test
    fun `query is debounced`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val settings = SettingsRepository(testDataStore(tmp.root, backgroundScope))
        val repo = MediaStoreRepository(FakeMediaStoreSource(library.tracks), settings, backgroundScope, dispatcher)
        val vm = SearchViewModel(SavedStateHandle(), repo, dispatcher)

        vm.results.test {
            assertTrue(awaitItem().isEmpty)
            vm.onQueryChange("к")
            vm.onQueryChange("ки")
            vm.onQueryChange("кино")
            advanceTimeBy(SearchViewModel.DEBOUNCE_MS - 50)
            expectNoEvents()
            advanceTimeBy(100)
            // Промежуточные запросы отброшены — сразу результат для «кино»
            assertEquals("кино", awaitItem().query)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

class UtilTest {

    @Test
    fun `sleep timer fades only in the last 10 seconds`() {
        assertEquals(1f, SleepTimer.fadeVolume(60_000), 0f)
        assertEquals(1f, SleepTimer.fadeVolume(10_000), 0f)
        assertEquals(0.5f, SleepTimer.fadeVolume(5_000), 0.001f)
        assertEquals(0f, SleepTimer.fadeVolume(0), 0f)
        assertEquals(0f, SleepTimer.fadeVolume(-100), 0f)
    }

    @Test
    fun `text helpers`() {
        assertEquals("еж", "ЁЖ".normalized())
        assertEquals("Е", indexLetter("ёлка"))
        assertEquals("#", indexLetter("7 Weeks"))
        assertEquals("#", indexLetter("..."))
        assertEquals("A", indexLetter("#apple"))
        assertEquals("3:07", formatDuration(187_000))
        assertEquals("1:02:03", formatDuration(3_723_000))
        assertEquals("0:00", formatDuration(-5))
    }
}
