package com.jpcottin.simpletorrent

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.jpcottin.simpletorrent.data.TorrentManager
import com.jpcottin.simpletorrent.theme.SimpleTorrentTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestAllFilesAccessIfNeeded()
        TorrentManager.init(this)
        handleTorrentIntent(intent)
        enableEdgeToEdge()
        setContent {
            SimpleTorrentTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainNavigation()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Reinit only if the resolved save path changed (e.g. user just granted MANAGE_EXTERNAL_STORAGE)
        val newPath = TorrentManager.resolvedSavePath(this)
        if (newPath != TorrentManager.currentSavePath) {
            TorrentManager.nativeRelease()
            TorrentManager.init(this)
        }
    }

    override fun onStop() {
        super.onStop()
        // onStop is guaranteed before process death; flush resume data here so we
        // survive the OS killing the process without onDestroy being called.
        lifecycleScope.launch(Dispatchers.IO) {
            TorrentManager.nativeSaveResumeData()
        }
    }

    override fun onDestroy() {
        // Must save before release: the onStop coroutine races with nativeRelease()
        // and may lose if nativeRelease() nulls g_session first.
        TorrentManager.nativeSaveResumeData()
        TorrentManager.nativeRelease()
        super.onDestroy()
    }

    // Called when the app is already running and a new .torrent intent arrives
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleTorrentIntent(intent)
    }

    private fun handleTorrentIntent(intent: Intent) {
        val uri: Uri = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> @Suppress("DEPRECATION") intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            else -> null
        } ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                ?.takeIf { it.isNotBlank() }
                ?: "received.torrent"

            val stagingDir = File(filesDir, "torrent_staging").also { it.mkdirs() }
            val stagedFile = File(stagingDir, name)
            runCatching {
                contentResolver.openInputStream(uri)!!.use { it.copyTo(stagedFile.outputStream()) }
                TorrentManager.addTorrentFile(stagedFile.absolutePath)
            }
        }
    }

    private fun requestAllFilesAccessIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.fromParts("package", packageName, null),
                )
            )
        }
    }
}
