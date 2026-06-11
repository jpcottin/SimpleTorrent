package com.jpcottin.simpletorrent.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.retryWhen
import kotlin.time.Duration.Companion.seconds

interface DataRepository {
    val torrents: Flow<List<TorrentInfo>>
    suspend fun addMagnet(uri: String): String
    suspend fun pauseTorrent(infoHash: String)
    suspend fun resumeTorrent(infoHash: String)
    suspend fun removeTorrent(infoHash: String, deleteFiles: Boolean)
    suspend fun setSequentialDownload(infoHash: String, enabled: Boolean)
    suspend fun createTorrentFrom(sourcePath: String, outputDir: String): CreateTorrentResult
}

/**
 * Owns the native-boundary threading contract: every mutating call goes through
 * [TorrentManager.withSession], which (a) waits for any pending session init so a
 * call can never hit an uninitialized session, and (b) executes on the session
 * queue's IO scope so the native mutex can never block the caller's thread.
 */
class DefaultDataRepository : DataRepository {

    override val torrents: Flow<List<TorrentInfo>> = flow {
        // Wait for any pending init so the first emission isn't a false empty list
        TorrentManager.withSession { }
        while (true) {
            emit(TorrentManager.getTorrents())
            val delay = if (TorrentManager.isInBackground) 10.seconds else 3.seconds
            delay(delay)
        }
    }
        .retryWhen { cause, attempt ->
            Log.w("DataRepository", "Polling error (attempt $attempt): ${cause.message}")
            val backoffDelay = (3 * (attempt + 1)).coerceAtMost(30).seconds
            delay(backoffDelay)
            true
        }
        .flowOn(Dispatchers.IO)

    override suspend fun addMagnet(uri: String): String =
        TorrentManager.withSession { TorrentManager.addMagnet(uri) }
    override suspend fun pauseTorrent(infoHash: String) =
        TorrentManager.withSession { TorrentManager.pauseTorrent(infoHash) }
    override suspend fun resumeTorrent(infoHash: String) =
        TorrentManager.withSession { TorrentManager.resumeTorrent(infoHash) }
    override suspend fun removeTorrent(infoHash: String, deleteFiles: Boolean) =
        TorrentManager.withSession { TorrentManager.removeTorrent(infoHash, deleteFiles) }
    override suspend fun setSequentialDownload(infoHash: String, enabled: Boolean) =
        TorrentManager.withSession { TorrentManager.setSequentialDownload(infoHash, enabled) }

    override suspend fun createTorrentFrom(sourcePath: String, outputDir: String): CreateTorrentResult {
        // Wait for init, but hash OFF the session queue: hashing a large file can
        // take minutes and must not delay a queued onStop resume-data save.
        TorrentManager.withSession { }
        return TorrentManager.createTorrentFrom(sourcePath, outputDir)
    }
}
