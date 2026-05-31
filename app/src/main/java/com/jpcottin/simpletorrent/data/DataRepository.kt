package com.jpcottin.simpletorrent.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface DataRepository {
    val torrents: Flow<List<TorrentInfo>>
    suspend fun addMagnet(uri: String): String
    fun pauseTorrent(infoHash: String)
    fun resumeTorrent(infoHash: String)
    fun removeTorrent(infoHash: String, deleteFiles: Boolean)
    fun setSequentialDownload(infoHash: String, enabled: Boolean)
    suspend fun createTorrentFrom(sourcePath: String, outputDir: String): CreateTorrentResult
}

class DefaultDataRepository : DataRepository {

    override val torrents: Flow<List<TorrentInfo>> = flow {
        while (true) {
            emit(TorrentManager.getTorrents())
            delay(3_000L)
        }
    }

    override suspend fun addMagnet(uri: String): String = TorrentManager.addMagnet(uri)
    override fun pauseTorrent(infoHash: String) = TorrentManager.pauseTorrent(infoHash)
    override fun resumeTorrent(infoHash: String) = TorrentManager.resumeTorrent(infoHash)
    override fun removeTorrent(infoHash: String, deleteFiles: Boolean) =
        TorrentManager.removeTorrent(infoHash, deleteFiles)
    override fun setSequentialDownload(infoHash: String, enabled: Boolean) =
        TorrentManager.setSequentialDownload(infoHash, enabled)
    override suspend fun createTorrentFrom(sourcePath: String, outputDir: String): CreateTorrentResult =
        TorrentManager.createTorrentFrom(sourcePath, outputDir)
}
