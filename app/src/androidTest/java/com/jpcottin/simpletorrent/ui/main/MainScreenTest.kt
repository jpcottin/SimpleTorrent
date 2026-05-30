package com.jpcottin.simpletorrent.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.jpcottin.simpletorrent.data.CreateTorrentResult
import com.jpcottin.simpletorrent.data.DataRepository
import com.jpcottin.simpletorrent.data.PeerInfo
import com.jpcottin.simpletorrent.data.TorrentInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test

class MainScreenTest {

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun launch(
        torrents: List<TorrentInfo> = emptyList(),
        createResult: CreateTorrentResult = CreateTorrentResult.Success("f", "/tmp/f.torrent", "magnet:?x"),
    ) {
        val repo = object : DataRepository {
            override val torrents: Flow<List<TorrentInfo>> = flowOf(torrents)
            override suspend fun addMagnet(uri: String) = "ok"
            override fun pauseTorrent(infoHash: String) {}
            override fun resumeTorrent(infoHash: String) {}
            override fun removeTorrent(infoHash: String, deleteFiles: Boolean) {}
            override suspend fun createTorrentFrom(s: String, o: String) = createResult
        }
        // Construct ViewModel outside the composable to avoid ViewModelConstructorInComposable lint
        val viewModel = MainScreenViewModel(repo)
        rule.setContent {
            MainScreen(
                onItemClick = {},
                viewModel = viewModel,
            )
        }
    }

    private fun fakeTorrent(
        infoHash: String = "abc123",
        name: String = "Big Buck Bunny",
        state: String = "downloading",
        isPaused: Boolean = false,
        progress: Float = 0.42f,
        peerList: List<PeerInfo> = emptyList(),
    ) = TorrentInfo(
        infoHash = infoHash, name = name, state = state, isPaused = isPaused,
        progress = progress, dlRateKbs = 512, ulRateKbs = 64, peers = peerList.size,
        peerList = peerList,
    )

    // ── empty state ───────────────────────────────────────────────────────────

    @Test
    fun emptyState_showsPlaceholderText() {
        launch()
        rule.onNodeWithText("No active torrents", substring = true).assertIsDisplayed()
    }

    @Test
    fun emptyState_showsMagnetInputBar() {
        launch()
        rule.onNodeWithText("Magnet link", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Add").assertIsDisplayed()
    }

    @Test
    fun emptyState_showsCreateButton() {
        launch()
        rule.onNodeWithContentDescription("Create torrent").assertIsDisplayed()
    }

    // ── torrent card ──────────────────────────────────────────────────────────

    @Test
    fun torrentCard_showsNameAndState() {
        launch(torrents = listOf(fakeTorrent()))
        rule.onNodeWithText("Big Buck Bunny").assertIsDisplayed()
        rule.onNodeWithText("downloading").assertIsDisplayed()
    }

    @Test
    fun torrentCard_showsPausedLabel_whenPaused() {
        launch(torrents = listOf(fakeTorrent(isPaused = true)))
        rule.onNodeWithText("paused").assertIsDisplayed()
    }

    @Test
    fun torrentCard_showsPauseButton_whenNotPaused() {
        launch(torrents = listOf(fakeTorrent(isPaused = false)))
        rule.onNodeWithContentDescription("Pause").assertIsDisplayed()
    }

    @Test
    fun torrentCard_showsResumeButton_whenPaused() {
        launch(torrents = listOf(fakeTorrent(isPaused = true)))
        rule.onNodeWithContentDescription("Resume").assertIsDisplayed()
    }

    @Test
    fun torrentCard_deleteButton_showsConfirmationDialog() {
        launch(torrents = listOf(fakeTorrent()))
        rule.onNodeWithContentDescription("Delete").performClick()
        rule.onNodeWithText("Delete torrent?").assertIsDisplayed()
        rule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun torrentCard_deleteDialog_dismissOnCancel() {
        launch(torrents = listOf(fakeTorrent()))
        rule.onNodeWithContentDescription("Delete").performClick()
        rule.onNodeWithText("Cancel").performClick()
        rule.onNodeWithText("Delete torrent?").assertDoesNotExist()
    }

    @Test
    fun torrentCard_peersToggle_showsPeerList() {
        val torrent = fakeTorrent(
            peerList = listOf(PeerInfo("192.168.1.10:6881", 512, 64, 0.8f)),
        )
        launch(torrents = listOf(torrent))
        rule.onNodeWithContentDescription("Show peers").performClick()
        rule.onNodeWithText("192.168.1.10:6881", substring = true).assertIsDisplayed()
    }

    @Test
    fun torrentCard_peersToggle_hidesPeerList_whenCollapsed() {
        val torrent = fakeTorrent(
            peerList = listOf(PeerInfo("10.0.0.1:6881", 128, 0, 0.5f)),
        )
        launch(torrents = listOf(torrent))
        rule.onNodeWithContentDescription("Show peers").performClick()
        rule.onNodeWithContentDescription("Hide peers").performClick()
        rule.onNodeWithText("10.0.0.1:6881", substring = true).assertDoesNotExist()
    }

    // ── create torrent ────────────────────────────────────────────────────────

    @Test
    fun createButton_showsDropdownWithFileAndFolderOptions() {
        launch()
        rule.onNodeWithContentDescription("Create torrent").performClick()
        rule.onNodeWithText("From file").assertIsDisplayed()
        rule.onNodeWithText("From folder").assertIsDisplayed()
    }

    // ── magnet input ──────────────────────────────────────────────────────────

    @Test
    fun magnetInput_clearAfterAdd() {
        launch()
        rule.onNode(hasText("Magnet link", substring = true)).performTextInput("magnet:?xt=urn:btih:abc")
        rule.onNodeWithText("Add").performClick()
        // TextField should be cleared (empty label placeholder is shown)
        rule.onNodeWithText("Magnet link", substring = true).assertIsDisplayed()
    }

    // ── size / ETA ────────────────────────────────────────────────────────────

    @Test
    fun torrentCard_showsSizeAndEta_whenDownloading() {
        launch(torrents = listOf(
            fakeTorrent().copy(
                totalWantedBytes = 1_471_026_298L,
                totalDoneBytes   = 276_482_048L,
                etaSecs          = 222L,
            )
        ))
        rule.onNodeWithText("263.7 MB / 1.37 GB", substring = true).assertIsDisplayed()
        rule.onNodeWithText("ETA 3m 42s", substring = true).assertIsDisplayed()
    }

    @Test
    fun torrentCard_showsSizeOnly_whenSeeding() {
        launch(torrents = listOf(
            fakeTorrent(state = "seeding").copy(
                totalWantedBytes = 276_482_048L,
                totalDoneBytes   = 276_482_048L,
                etaSecs          = -1L,
            )
        ))
        rule.onNodeWithText("263.6 MB / 263.6 MB", substring = true).assertIsDisplayed()
        rule.onNodeWithText("ETA", substring = true).assertDoesNotExist()
    }

    @Test
    fun torrentCard_hiddesSizeLine_whenTotalUnknown() {
        launch(torrents = listOf(fakeTorrent()))
        rule.onNodeWithText(" / ", substring = true).assertDoesNotExist()
    }

    // ── multiple torrents ─────────────────────────────────────────────────────

    @Test
    fun multipleCards_allNamesVisible() {
        launch(torrents = listOf(
            fakeTorrent(infoHash = "hash1", name = "Sintel"),
            fakeTorrent(infoHash = "hash2", name = "Elephants Dream"),
        ))
        rule.onNodeWithText("Sintel").assertIsDisplayed()
        rule.onNodeWithText("Elephants Dream").assertIsDisplayed()
    }
}
