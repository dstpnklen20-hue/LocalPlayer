package com.localplayer.app.data.mediastore

import app.cash.turbine.test
import com.localplayer.app.data.prefs.SettingsRepository
import com.localplayer.app.testutil.FakeMediaStoreSource
import com.localplayer.app.testutil.testDataStore
import com.localplayer.app.testutil.track
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class MediaStoreRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun TestScope.createRepository(source: FakeMediaStoreSource): Pair<MediaStoreRepository, SettingsRepository> {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val settings = SettingsRepository(testDataStore(tmp.root, backgroundScope))
        val repo = MediaStoreRepository(source, settings, backgroundScope, dispatcher)
        return repo to settings
    }

    @Test
    fun `refresh loads and filters short tracks`() = runTest {
        val source = FakeMediaStoreSource(listOf(track(1), track(2, durationMs = 10_000)))
        val (repo, _) = createRepository(source)

        repo.refresh()

        repo.library.filterNotNull().test {
            assertEquals(listOf(1L), awaitItem().tracks.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
        // Без фильтров доступны все треки — для восстановления очереди
        assertEquals(2, repo.allTracks.value?.size)
    }

    @Test
    fun `changing threshold re-filters without rescanning`() = runTest {
        val source = FakeMediaStoreSource(listOf(track(1), track(2, durationMs = 10_000)))
        val (repo, settings) = createRepository(source)
        repo.refresh()

        repo.library.filterNotNull().test {
            assertEquals(1, awaitItem().tracks.size)
            settings.setMinDurationSec(0)
            assertEquals(2, awaitItem().tracks.size)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, source.queryCount)
    }

    @Test
    fun `excluded folders are hidden`() = runTest {
        val source = FakeMediaStoreSource(
            listOf(track(1), track(2, folder = "/storage/emulated/0/WhatsApp/Media/Audio")),
        )
        val (repo, settings) = createRepository(source)
        settings.addExcludedFolder("WhatsApp/Media")
        repo.refresh()

        repo.library.filterNotNull().test {
            assertEquals(listOf(1L), expectMostRecentItem().tracks.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `mediastore change triggers rescan after debounce`() = runTest {
        val source = FakeMediaStoreSource(listOf(track(1)))
        val (repo, _) = createRepository(source)
        repo.refresh()
        assertEquals(1, source.queryCount)

        runCurrent() // даём подписаться на изменения
        source.tracks = listOf(track(1), track(2))
        source.changeEvents.emit(Unit)
        source.changeEvents.emit(Unit)
        advanceTimeBy(1_500)

        // Две быстрые нотификации — одно пересканирование
        assertEquals(2, source.queryCount)
        assertEquals(2, repo.allTracks.value?.size)
    }

    @Test
    fun `missing permission gives empty library instead of crash`() = runTest {
        val source = FakeMediaStoreSource(listOf(track(1))).apply { throwSecurity = true }
        val (repo, _) = createRepository(source)

        repo.refresh()

        assertTrue(repo.allTracks.value!!.isEmpty())
        assertTrue(!repo.isRefreshing.value)
    }
}
