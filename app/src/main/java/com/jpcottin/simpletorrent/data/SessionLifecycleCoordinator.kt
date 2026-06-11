package com.jpcottin.simpletorrent.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

/**
 * Abstraction over the native torrent session so the lifecycle sequencing in
 * [SessionLifecycleCoordinator] can be unit tested without loading the native library.
 */
interface TorrentSessionControl {
    /** True if the resolved save path no longer matches the one the session was started with. */
    fun savePathChanged(): Boolean
    fun init()
    fun release()
    fun saveResumeData()
    fun setTickInterval(intervalMs: Int)
    fun setBackground(isBackground: Boolean)
}

/**
 * Serializes all native session operations (init / save / release) on a FIFO queue
 * owned by a process-wide scope that survives Activity destruction.
 *
 * This fixes two ways resume data could be silently lost:
 *  - onStop used to launch the save on lifecycleScope, which is cancelled at
 *    ON_DESTROY — swiping the app away could cancel the save before it ran.
 *  - onDestroy used to release the session on the main thread, racing the async
 *    save; if release won, save_all_resume_data() found no session and wrote nothing.
 *
 * Lifecycle methods must be called from a single thread (the main thread).
 */
class SessionLifecycleCoordinator(
    private val session: TorrentSessionControl,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private var tail: Deferred<*>? = null

    fun onCreate() {
        enqueue { session.init() }
    }

    fun onStart() {
        enqueue {
            session.setTickInterval(FOREGROUND_TICK_MS)
            session.setBackground(false)
        }
    }

    /** Re-creates the session when the save path changed (e.g. storage permission granted). */
    fun onResume() {
        enqueue {
            if (session.savePathChanged()) {
                session.saveResumeData()
                session.release()
                session.init()
            }
        }
    }

    fun onStop() {
        enqueue {
            session.saveResumeData()
            session.setTickInterval(BACKGROUND_TICK_MS)
            session.setBackground(true)
        }
    }

    fun onDestroy() {
        enqueue { session.release() }
    }

    /** Runs [block] on the session queue, after any pending init/save/release. */
    suspend fun <T> withSession(block: suspend () -> T): T = enqueue(block).await()

    @Synchronized
    private fun <T> enqueue(block: suspend () -> T): Deferred<T> {
        val prev = tail
        val next = scope.async {
            prev?.join()
            block()
        }
        // Drop the reference once the queue drains so the last completed
        // Deferred isn't retained indefinitely
        next.invokeOnCompletion {
            synchronized(this@SessionLifecycleCoordinator) {
                if (tail === next) tail = null
            }
        }
        tail = next
        return next
    }

    companion object {
        const val FOREGROUND_TICK_MS = 500
        const val BACKGROUND_TICK_MS = 2000
    }
}
