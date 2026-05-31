package com.jpcottin.simpletorrent

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.jpcottin.simpletorrent.data.FileInfo
import com.jpcottin.simpletorrent.data.PeerInfo
import com.jpcottin.simpletorrent.data.TorrentInfo
import com.jpcottin.simpletorrent.theme.SimpleTorrentTheme
import com.jpcottin.simpletorrent.ui.main.MainScreen
import com.jpcottin.simpletorrent.ui.main.FileList
import com.jpcottin.simpletorrent.ui.main.PeerList
import com.jpcottin.simpletorrent.ui.main.MagnetInputBar
import com.jpcottin.simpletorrent.ui.main.TorrentCard

// ── Form-factor multi-preview annotation ────────────────────────────────────

@Preview(name = "Phone",    device = Devices.PHONE,    showBackground = true)
@Preview(name = "Foldable", device = Devices.FOLDABLE, showBackground = true)
@Preview(name = "Tablet",   device = Devices.TABLET,   showBackground = true)
annotation class FormFactorPreviews

// ── TorrentCard — downloading ────────────────────────────────────────────────

@PreviewTest
@FormFactorPreviews
@Composable
fun TorrentCardDownloadingScreenshot() {
    SimpleTorrentTheme {
        TorrentCard(
            torrent = TorrentInfo(
                "abc123", "Big Buck Bunny", "downloading", false, 0.42f, 1024, 128, 7,
                piecesMap = "0".repeat(84) + "1".repeat(84) + "2".repeat(32),
                totalWantedBytes     = 1_471_026_298L,
                totalDoneBytes       = 617_831_044L,
                etaSecs              = 822L,
                allTimeUploadBytes   = 12_345_678L,
                allTimeDownloadBytes = 617_831_044L,
            ),
            onPause = {}, onResume = {}, onRemove = {}, onPlay = { _, _ -> },
        )
    }
}

// ── TorrentCard — paused ─────────────────────────────────────────────────────

@PreviewTest
@Preview(name = "Paused", showBackground = true)
@Composable
fun TorrentCardPausedScreenshot() {
    SimpleTorrentTheme {
        TorrentCard(
            torrent = TorrentInfo(
                "abc123", "Big Buck Bunny", "downloading", true, 0.42f, 0, 0, 0,
                totalWantedBytes = 1_471_026_298L,
                totalDoneBytes   = 617_831_044L,
            ),
            onPause = {}, onResume = {}, onRemove = {}, onPlay = { _, _ -> },
        )
    }
}

// ── TorrentCard — seeding with ratio ─────────────────────────────────────────

@PreviewTest
@Preview(name = "Seeding", showBackground = true)
@Composable
fun TorrentCardSeedingScreenshot() {
    SimpleTorrentTheme {
        TorrentCard(
            torrent = TorrentInfo(
                "abc123", "Big Buck Bunny", "seeding", false, 1f, 0, 512, 3,
                totalWantedBytes     = 276_482_048L,
                totalDoneBytes       = 276_482_048L,
                allTimeUploadBytes   = 345_602_560L,
                allTimeDownloadBytes = 276_482_048L,
            ),
            onPause = {}, onResume = {}, onRemove = {}, onPlay = { _, _ -> },
        )
    }
}

// ── TorrentCard — multi-file with file list ───────────────────────────────────

@PreviewTest
@Preview(name = "Multi-file", showBackground = true)
@Composable
fun TorrentCardMultiFileScreenshot() {
    SimpleTorrentTheme {
        TorrentCard(
            torrent = TorrentInfo(
                "def456", "Cosmos Laundromat", "downloading", false, 0.27f, 2048, 64, 12,
                totalWantedBytes     = 3_758_096_384L,
                totalDoneBytes       = 1_014_685_022L,
                etaSecs              = 3_240L,
                allTimeUploadBytes   = 52_428_800L,
                allTimeDownloadBytes = 1_014_685_022L,
                fileList = listOf(
                    FileInfo("cosmos_laundromat_4k.mkv", 3_650_000_000L, 980_000_000L),
                    FileInfo("subtitles_en.srt",              45_000L,        45_000L),
                    FileInfo("subtitles_fr.srt",              48_000L,        48_000L),
                ),
            ),
            onPause = {}, onResume = {}, onRemove = {}, onPlay = { _, _ -> },
        )
    }
}

// ── MagnetInputBar ────────────────────────────────────────────────────────────

@PreviewTest
@Preview(name = "Magnet input — empty", showBackground = true)
@Composable
fun MagnetInputBarScreenshot() {
    SimpleTorrentTheme {
        MagnetInputBar(
            value = "",
            onValueChange = {},
            onAdd = {},
            onCreateFile = {},
            onCreateFolder = {},
        )
    }
}

// ── FileList ──────────────────────────────────────────────────────────────────

@PreviewTest
@Preview(name = "File list", showBackground = true)
@Composable
fun FileListScreenshot() {
    SimpleTorrentTheme {
        FileList(
            files = listOf(
                FileInfo("BigBuckBunny.mp4",  276_482_048L, 276_482_048L),
                FileInfo("subtitles_en.srt",      140_000L,     140_000L),
                FileInfo("poster.jpg",            303_000L,     150_000L),
            ),
            onPlay = { _, _ -> },
        )
    }
}

// ── PeerList ──────────────────────────────────────────────────────────────────

@PreviewTest
@Preview(name = "Peer list", showBackground = true)
@Composable
fun PeerListScreenshot() {
    SimpleTorrentTheme {
        PeerList(
            peers = listOf(
                PeerInfo("192.168.1.10:6881",  4096, 128, 0.85f),
                PeerInfo("10.0.0.5:51413",     1024,   0, 0.42f),
                PeerInfo("172.16.0.3:6889",      256,  64, 0.10f),
            ),
        )
    }
}
