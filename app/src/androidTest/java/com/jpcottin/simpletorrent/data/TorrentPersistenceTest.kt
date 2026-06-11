package com.jpcottin.simpletorrent.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.random.Random

/**
 * End-to-end tests for resume-data persistence in the native layer: torrents must
 * survive a session release + re-init (the "app restart" path), resume files must be
 * written eagerly without waiting for onStop, and a corrupt resume file must be
 * quarantined instead of crashing or silently losing the rest.
 */
@RunWith(AndroidJUnit4::class)
class TorrentPersistenceTest {

    private lateinit var context: Context
    private lateinit var stateDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        stateDir = File(context.cacheDir, "test_state_${System.nanoTime()}").apply { mkdirs() }
        // The native session is a process-wide singleton; make sure each test starts fresh
        TorrentManager.nativeRelease()
    }

    @After
    fun tearDown() {
        TorrentManager.nativeRelease()
        stateDir.deleteRecursively()
    }

    @Test
    fun torrentSurvivesSessionRestart() {
        TorrentManager.init(context, stateDir.absolutePath)
        val hash = seedSampleTorrent()

        TorrentManager.nativeSaveResumeData()
        TorrentManager.nativeRelease()
        TorrentManager.init(context, stateDir.absolutePath)

        waitFor("torrent $hash restored after restart") {
            TorrentManager.getTorrents().any { it.infoHash == hash }
        }
    }

    @Test
    fun resumeFileIsWrittenEagerly_withoutExplicitSave() {
        TorrentManager.init(context, stateDir.absolutePath)
        val hash = seedSampleTorrent()

        // No nativeSaveResumeData() here: the add_torrent_alert handler must request
        // the save on its own. Polling getTorrents() drives the alert loop.
        waitFor("eager resume file for $hash") {
            TorrentManager.getTorrents()
            File(stateDir, "$hash.resume").exists()
        }
    }

    @Test
    fun corruptResumeFileIsQuarantined_andSessionStillStarts() {
        // Name is not a valid 40-hex info-hash, so no magnet recovery is possible —
        // the file must still be quarantined without crashing the session
        val corrupt = File(stateDir, "deadbeef.resume").apply { writeText("not bencoded data") }

        TorrentManager.init(context, stateDir.absolutePath)

        // Session must be functional despite the bad file
        TorrentManager.getTorrents()
        assertFalse("corrupt file should have been moved aside", corrupt.exists())
        assertTrue(
            "corrupt file should be quarantined as .corrupt",
            File(stateDir, "deadbeef.resume.corrupt").exists(),
        )
    }

    @Test
    fun corruptResumeFile_isRecoveredAsMagnet_whenNameIsValidInfoHash() {
        val hash = "ab".repeat(20) // valid 40-char hex info-hash
        File(stateDir, "$hash.resume").writeText("garbage, not bencoded")

        TorrentManager.init(context, stateDir.absolutePath)

        assertTrue(File(stateDir, "$hash.resume.corrupt").exists())
        // The torrent must come back as a magnet add (fetching metadata) instead
        // of silently vanishing
        waitFor("recovered torrent $hash in session") {
            TorrentManager.getTorrents().any { it.infoHash == hash }
        }
    }

    @Test
    fun removedTorrentDoesNotReappearAfterRestart() {
        TorrentManager.init(context, stateDir.absolutePath)
        val hash = seedSampleTorrent()
        waitFor("resume file for $hash") {
            TorrentManager.getTorrents()
            File(stateDir, "$hash.resume").exists()
        }

        TorrentManager.removeTorrent(hash, deleteFiles = false)
        waitFor("torrent $hash removed") {
            TorrentManager.getTorrents().none { it.infoHash == hash }
        }
        assertFalse(File(stateDir, "$hash.resume").exists())

        TorrentManager.nativeRelease()
        TorrentManager.init(context, stateDir.absolutePath)
        Thread.sleep(1_000) // give async_add_torrent a chance to (wrongly) restore it
        assertTrue(TorrentManager.getTorrents().none { it.infoHash == hash })
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Creates and seeds a small local torrent, returning its info hash. */
    private fun seedSampleTorrent(): String {
        val source = File(context.cacheDir, "sample_${System.nanoTime()}.bin").apply {
            writeBytes(Random.nextBytes(64 * 1024))
        }
        val result = TorrentManager.createTorrentFrom(
            source.absolutePath,
            File(context.cacheDir, "torrents").absolutePath,
        )
        assertTrue("createTorrentFrom failed: $result", result is CreateTorrentResult.Success)
        waitFor("torrent appears in session") { TorrentManager.getTorrents().isNotEmpty() }
        return TorrentManager.getTorrents().first().infoHash
    }

    private fun waitFor(what: String, timeoutMs: Long = 15_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(200)
        }
        fail("Timed out after ${timeoutMs}ms waiting for: $what")
    }
}
