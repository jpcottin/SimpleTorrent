package com.jpcottin.simpletorrent.ui.main

import com.jpcottin.simpletorrent.data.CreateTorrentResult
import com.jpcottin.simpletorrent.data.DataRepository
import com.jpcottin.simpletorrent.data.TorrentInfo
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After  fun tearDown() { Dispatchers.resetMain() }

    // ── uiState ──────────────────────────────────────────────────────────────

    @Test
    fun uiState_initiallyLoading() {
        // StateFlow holds the initial value without needing a subscriber
        val vm = viewModel()
        assertEquals(MainScreenUiState.Loading, vm.uiState.value)
    }

    @Test
    fun uiState_emitsSuccess_whenRepositoryEmitsTorrents() = runTest {
        val torrent = fakeTorrent()
        val vm = viewModel(repo = FakeRepository(torrents = listOf(torrent)))
        val state = vm.uiState.first { it is MainScreenUiState.Success }
        assertEquals(listOf(torrent), (state as MainScreenUiState.Success).torrents)
    }

    @Test
    fun uiState_emitsEmptySuccess_whenRepositoryEmitsEmptyList() = runTest {
        val vm = viewModel(repo = FakeRepository(torrents = emptyList()))
        val state = vm.uiState.first { it is MainScreenUiState.Success }
        assertTrue((state as MainScreenUiState.Success).torrents.isEmpty())
    }

    @Test
    fun uiState_emitsError_whenFlowThrows() = runTest {
        val vm = viewModel(repo = FakeRepository(throwOnTorrents = RuntimeException("network fail")))
        val state = vm.uiState.first { it is MainScreenUiState.Error }
        assertEquals("network fail", (state as MainScreenUiState.Error).throwable.message)
    }

    // ── torrent operations ────────────────────────────────────────────────────

    @Test
    fun addMagnet_trimsAndDelegatesToRepository() = runTest {
        val repo = FakeRepository()
        val vm = viewModel(repo = repo)
        vm.addMagnet("  magnet:?xt=urn:btih:abc  ")
        assertEquals("magnet:?xt=urn:btih:abc", repo.lastAddedMagnet)
    }

    @Test
    fun pause_delegatesToRepository() = runTest {
        val repo = FakeRepository()
        viewModel(repo = repo).pause("hash1")
        assertEquals("hash1", repo.lastPausedHash)
    }

    @Test
    fun resume_delegatesToRepository() = runTest {
        val repo = FakeRepository()
        viewModel(repo = repo).resume("hash2")
        assertEquals("hash2", repo.lastResumedHash)
    }

    @Test
    fun remove_delegatesToRepository() = runTest {
        val repo = FakeRepository()
        viewModel(repo = repo).remove("hash3", deleteFiles = true)
        assertEquals("hash3" to true, repo.lastRemovedHash to repo.lastRemovedDeleteFiles)
    }

    // ── create torrent ────────────────────────────────────────────────────────

    @Test
    fun createTorrent_setsDone_whenRepositorySucceeds() = runTest {
        val success = CreateTorrentResult.Success("file.mkv", "/tmp/file.mkv.torrent", "magnet:?xt=urn:btih:abc")
        val vm = viewModel(repo = FakeRepository(createResult = success))
        vm.createTorrent("/data/file.mkv", "/tmp")
        val state = vm.createState.first { it !is CreateState.Creating }
        assertTrue(state is CreateState.Done)
        assertEquals(success, (state as CreateState.Done).result)
    }

    @Test
    fun createTorrent_setsFailed_whenRepositoryFails() = runTest {
        val failure = CreateTorrentResult.Failure("file not found")
        val vm = viewModel(repo = FakeRepository(createResult = failure))
        vm.createTorrent("/data/missing.mkv", "/tmp")
        val state = vm.createState.first { it !is CreateState.Creating }
        assertTrue(state is CreateState.Failed)
        assertEquals("file not found", (state as CreateState.Failed).error)
    }

    @Test
    fun dismissCreateResult_resetsToIdle() = runTest {
        val vm = viewModel(repo = FakeRepository(createResult = CreateTorrentResult.Failure("err")))
        vm.createTorrent("/x", "/y")
        vm.createState.first { it is CreateState.Failed }
        vm.dismissCreateResult()
        assertEquals(CreateState.Idle, vm.createState.value)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun viewModel(repo: FakeRepository = FakeRepository()) =
        MainScreenViewModel(repo, testDispatcher)

    private fun fakeTorrent(name: String = "Big Buck Bunny") = TorrentInfo(
        infoHash = "abc123", name = name, state = "downloading",
        isPaused = false, progress = 0.5f,
        dlRateKbs = 1024, ulRateKbs = 128, peers = 5,
    )
}

private class FakeRepository(
    torrents: List<TorrentInfo> = emptyList(),
    private val throwOnTorrents: Throwable? = null,
    private val createResult: CreateTorrentResult =
        CreateTorrentResult.Success("test.mkv", "/tmp/test.mkv.torrent", "magnet:?xt=urn:btih:aabbcc"),
) : DataRepository {

    override val torrents: Flow<List<TorrentInfo>> = flow {
        if (throwOnTorrents != null) throw throwOnTorrents
        emit(torrents)
    }

    var lastAddedMagnet: String? = null
    var lastPausedHash: String? = null
    var lastResumedHash: String? = null
    var lastRemovedHash: String? = null
    var lastRemovedDeleteFiles: Boolean? = null

    override suspend fun addMagnet(uri: String): String { lastAddedMagnet = uri; return "ok" }
    override fun pauseTorrent(infoHash: String) { lastPausedHash = infoHash }
    override fun resumeTorrent(infoHash: String) { lastResumedHash = infoHash }
    override fun removeTorrent(infoHash: String, deleteFiles: Boolean) {
        lastRemovedHash = infoHash; lastRemovedDeleteFiles = deleteFiles
    }
    override suspend fun createTorrentFrom(sourcePath: String, outputDir: String) = createResult
}
