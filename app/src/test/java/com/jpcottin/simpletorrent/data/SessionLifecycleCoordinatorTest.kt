package com.jpcottin.simpletorrent.data

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionLifecycleCoordinatorTest {

    private class FakeSession : TorrentSessionControl {
        val ops = mutableListOf<String>()
        var pathChanged = false
        override fun savePathChanged() = pathChanged
        override fun init() { ops += "init" }
        override fun release() { ops += "release" }
        override fun saveResumeData() { ops += "save" }
        override fun setTickInterval(intervalMs: Int) { ops += "tick($intervalMs)" }
        override fun setBackground(isBackground: Boolean) { ops += "background($isBackground)" }
    }

    // ── the disappearing-torrents regression ─────────────────────────────────

    @Test
    fun onDestroy_neverReleasesSessionBeforePendingSave() = runTest {
        // Swiping the app away fires onStop then onDestroy back to back. The old
        // code released the session on the main thread while the save ran async on
        // Dispatchers.IO; when release won the race, no resume data was written and
        // torrents disappeared on the next launch even though their data was on disk.
        val fake = FakeSession()
        val coordinator = SessionLifecycleCoordinator(fake, this)

        coordinator.onStop()
        coordinator.onDestroy() // enqueued immediately, before the save has run
        advanceUntilIdle()

        val saveIndex = fake.ops.indexOf("save")
        val releaseIndex = fake.ops.indexOf("release")
        assertTrue("save must have run", saveIndex >= 0)
        assertTrue("release must have run", releaseIndex >= 0)
        assertTrue("release ran before save: ${fake.ops}", saveIndex < releaseIndex)
    }

    @Test
    fun activityRecreation_releasesOldSessionBeforeInitOfNewOne() = runTest {
        val fake = FakeSession()
        val coordinator = SessionLifecycleCoordinator(fake, this)

        // Rotation: old activity stops and is destroyed, new one is created
        coordinator.onStop()
        coordinator.onDestroy()
        coordinator.onCreate()
        advanceUntilIdle()

        assertEquals(
            listOf("save", "tick(2000)", "background(true)", "release", "init"),
            fake.ops,
        )
    }

    // ── individual lifecycle steps ────────────────────────────────────────────

    @Test
    fun onCreate_initializesSession() = runTest {
        val fake = FakeSession()
        SessionLifecycleCoordinator(fake, this).onCreate()
        advanceUntilIdle()
        assertEquals(listOf("init"), fake.ops)
    }

    @Test
    fun onStart_restoresForegroundTickAndPolling() = runTest {
        val fake = FakeSession()
        SessionLifecycleCoordinator(fake, this).onStart()
        advanceUntilIdle()
        assertEquals(listOf("tick(500)", "background(false)"), fake.ops)
    }

    @Test
    fun onStop_savesResumeDataBeforeEnteringBackgroundMode() = runTest {
        val fake = FakeSession()
        SessionLifecycleCoordinator(fake, this).onStop()
        advanceUntilIdle()
        assertEquals(listOf("save", "tick(2000)", "background(true)"), fake.ops)
    }

    @Test
    fun onResume_doesNothingWhenSavePathUnchanged() = runTest {
        val fake = FakeSession()
        SessionLifecycleCoordinator(fake, this).onResume()
        advanceUntilIdle()
        assertTrue(fake.ops.isEmpty())
    }

    @Test
    fun onResume_savesBeforeRecreatingSessionWhenSavePathChanged() = runTest {
        // The old code released + re-inited without saving first, dropping any
        // torrent added since the last save.
        val fake = FakeSession().apply { pathChanged = true }
        SessionLifecycleCoordinator(fake, this).onResume()
        advanceUntilIdle()
        assertEquals(listOf("save", "release", "init"), fake.ops)
    }

    // ── withSession ───────────────────────────────────────────────────────────

    @Test
    fun withSession_runsAfterPendingInitAndReturnsValue() = runTest {
        val fake = FakeSession()
        val coordinator = SessionLifecycleCoordinator(fake, this)

        coordinator.onCreate()
        val result = coordinator.withSession {
            fake.ops += "addTorrent"
            "ok:abc123"
        }

        assertEquals("ok:abc123", result)
        assertEquals(listOf("init", "addTorrent"), fake.ops)
    }

    @Test
    fun queue_preservesFifoOrderAcrossManyOperations() = runTest {
        val fake = FakeSession()
        val coordinator = SessionLifecycleCoordinator(fake, this)

        coordinator.onCreate()
        coordinator.onStart()
        coordinator.onStop()
        coordinator.onStart()
        coordinator.onStop()
        coordinator.onDestroy()
        advanceUntilIdle()

        assertEquals(
            listOf(
                "init",
                "tick(500)", "background(false)",
                "save", "tick(2000)", "background(true)",
                "tick(500)", "background(false)",
                "save", "tick(2000)", "background(true)",
                "release",
            ),
            fake.ops,
        )
    }
}
