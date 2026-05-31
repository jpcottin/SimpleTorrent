package com.jpcottin.simpletorrent.data

import kotlin.time.Duration.Companion.seconds
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.retryWhen
import kotlin.math.min
import kotlin.time.Duration.Companion.seconds

interface DataRepository {
    val torrents: Flow<List<TorrentInfo>>
    suspend fun addMagnet(uri: String): String
    fun pauseTorrent(infoHash: String)
    fun resumeTorrent(infoHash: String)
    fun removeTorrent(infoHash: String, deleteFiles: Boolean)
    fun setSequentialDownload(infoHash: String, enabled: Boolean)
    suspend fun createTorrentFrom(sourcePath: String, outputDir: String): CreateTorrentResult
    fun setBackgroundMode(isBackground: Boolean)
}

class DefaultDataRepository : DataRepository {

    override val torrents: Flow<List<TorrentInfo>> = flow {
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

    override suspend fun addMagnet(uri: String): String = TorrentManager.addMagnet(uri)
    override fun pauseTorrent(infoHash: String) = TorrentManager.pauseTorrent(infoHash)
    override fun resumeTorrent(infoHash: String) = TorrentManager.resumeTorrent(infoHash)
    override fun removeTorrent(infoHash: String, deleteFiles: Boolean) =
        TorrentManager.removeTorrent(infoHash, deleteFiles)
    override fun setSequentialDownload(infoHash: String, enabled: Boolean) =
        TorrentManager.setSequentialDownload(infoHash, enabled)
    override suspend fun createTorrentFrom(sourcePath: String, outputDir: String): CreateTorrentResult =
        TorrentManager.createTorrentFrom(sourcePath, outputDir)
    override fun setBackgroundMode(isBackground: Boolean) {
        TorrentManager.nativeSetTickInterval(if (isBackground) 2000 else 500)
    }
}
