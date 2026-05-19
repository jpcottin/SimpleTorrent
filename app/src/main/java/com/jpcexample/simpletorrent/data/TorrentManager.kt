package com.jpcexample.simpletorrent.data

import android.content.Context
import android.os.Build
import android.os.Environment
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class PeerInfo(
    val ip: String,
    val dlKbs: Int,
    val ulKbs: Int,
    val progress: Float,
)

@Serializable
data class TorrentInfo(
    val infoHash: String,
    val name: String,
    val state: String,
    val isPaused: Boolean,
    val progress: Float,
    val dlRateKbs: Int,
    val ulRateKbs: Int,
    val peers: Int,
    val piecesMap: String = "",
    val peerList: List<PeerInfo> = emptyList(),
)

sealed interface CreateTorrentResult {
    data class Success(val name: String, val torrentFile: String, val magnetUri: String) : CreateTorrentResult
    data class Failure(val error: String) : CreateTorrentResult
}

object TorrentManager {

    private val json = Json { ignoreUnknownKeys = true }

    init {
        System.loadLibrary("simpletorrent")
    }

    fun resolvedSavePath(context: Context): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
        } else {
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.absolutePath
                ?: context.filesDir.absolutePath
        }

    var currentSavePath: String = ""
        private set

    fun init(context: Context) {
        currentSavePath = resolvedSavePath(context)
        val statePath = context.filesDir.absolutePath + "/torrent_state"
        nativeInit(currentSavePath, statePath)
    }

    fun getTorrents(): List<TorrentInfo> =
        runCatching { json.decodeFromString<List<TorrentInfo>>(getTorrentsJson()) }
            .getOrDefault(emptyList())

    fun createTorrentFrom(sourcePath: String, outputDir: String): CreateTorrentResult {
        val raw = createTorrent(sourcePath, outputDir)
        return runCatching {
            val obj = Json.parseToJsonElement(raw).jsonObject
            if (obj.containsKey("error")) {
                CreateTorrentResult.Failure(obj["error"]!!.jsonPrimitive.content)
            } else {
                CreateTorrentResult.Success(
                    name        = obj["name"]!!.jsonPrimitive.content,
                    torrentFile = obj["torrentFile"]!!.jsonPrimitive.content,
                    magnetUri   = obj["magnetUri"]!!.jsonPrimitive.content,
                )
            }
        }.getOrElse { CreateTorrentResult.Failure(it.message ?: "unknown error") }
    }

    external fun nativeInit(savePath: String, statePath: String)
    external fun nativeRelease()
    external fun nativeSaveResumeData()
    external fun addMagnet(magnetUri: String): String
    external fun addTorrentFile(torrentPath: String): String
    external fun pauseTorrent(infoHash: String)
    external fun resumeTorrent(infoHash: String)
    external fun removeTorrent(infoHash: String, deleteFiles: Boolean)
    external fun getMagnetUri(infoHash: String): String
    external fun saveTorrentFile(infoHash: String, outputDir: String): String
    private external fun createTorrent(sourcePath: String, outputDir: String): String
    private external fun getTorrentsJson(): String
}
