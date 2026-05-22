package com.jpcottin.simpletorrent.ui.main

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.jpcottin.simpletorrent.data.DefaultDataRepository
import com.jpcottin.simpletorrent.data.PeerInfo
import com.jpcottin.simpletorrent.data.TorrentInfo
import com.jpcottin.simpletorrent.data.TorrentManager
import com.jpcottin.simpletorrent.theme.SimpleTorrentTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel { MainScreenViewModel(DefaultDataRepository()) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val createState by viewModel.createState.collectAsStateWithLifecycle()
    var magnetInput by remember { mutableStateOf("") }
    val context = LocalContext.current

    fun resolveTreeToPath(uri: android.net.Uri): String? {
        val treeDocId = android.provider.DocumentsContract.getTreeDocumentId(uri)
        val colon = treeDocId.indexOf(':')
        if (colon < 0) return null
        val type = treeDocId.substring(0, colon)
        val rel  = treeDocId.substring(colon + 1)
        val root = if (type.equals("primary", ignoreCase = true)) "/storage/emulated/0" else "/storage/$type"
        return "$root/$rel"
    }

    // For file picks: copy to internal staging so libtorrent always gets a stable path.
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val stagingDir = context.filesDir.absolutePath + "/torrent_staging"
        val outputDir  = context.filesDir.absolutePath + "/created_torrents"
        viewModel.createTorrentFromUri(context.contentResolver, uri, stagingDir, outputDir)
    }

    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val path = resolveTreeToPath(uri) ?: return@rememberLauncherForActivityResult
        val outputDir = context.filesDir.absolutePath + "/created_torrents"
        viewModel.createTorrent(path, outputDir)
    }

    // Creating progress dialog
    if (createState is CreateState.Creating) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Creating torrent…") },
            text = { CircularProgressIndicator() },
            confirmButton = {},
        )
    }

    // Result dialog
    if (createState is CreateState.Done || createState is CreateState.Failed) {
        val isSuccess = createState is CreateState.Done
        val result = (createState as? CreateState.Done)?.result
        val errorMsg = (createState as? CreateState.Failed)?.error

        AlertDialog(
            onDismissRequest = { viewModel.dismissCreateResult() },
            title = { Text(if (isSuccess) "Torrent created!" else "Error") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isSuccess && result != null) {
                        Text("Name: ${result.name}", fontSize = 13.sp)
                        Text(
                            text = result.magnetUri,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        Text("Failed: $errorMsg")
                    }
                }
            },
            confirmButton = {
                if (isSuccess && result != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("magnet", result.magnetUri))
                            viewModel.dismissCreateResult()
                        }) { Text("Copy magnet") }
                        TextButton(onClick = {
                            val torrentFile = File(result.torrentFile)
                            val fileUri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                torrentFile,
                            )
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/x-bittorrent"
                                putExtra(Intent.EXTRA_STREAM, fileUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share .torrent"))
                            viewModel.dismissCreateResult()
                        }) { Text("Share .torrent") }
                    }
                } else {
                    TextButton(onClick = { viewModel.dismissCreateResult() }) { Text("OK") }
                }
            },
            dismissButton = {
                if (isSuccess) {
                    TextButton(onClick = { viewModel.dismissCreateResult() }) { Text("Close") }
                }
            },
        )
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp),
        ) {
            MagnetInputBar(
                value = magnetInput,
                onValueChange = { magnetInput = it },
                onAdd = {
                    if (magnetInput.isNotBlank()) {
                        viewModel.addMagnet(magnetInput)
                        magnetInput = ""
                    }
                },
                onCreateFile   = { fileLauncher.launch(arrayOf("*/*")) },
                onCreateFolder = { folderLauncher.launch(null) },
            )

            when (state) {
                MainScreenUiState.Loading ->
                    CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                is MainScreenUiState.Error ->
                    Text("Error: ${(state as MainScreenUiState.Error).throwable.message}")
                is MainScreenUiState.Success -> {
                    val torrents = (state as MainScreenUiState.Success).torrents
                    if (torrents.isEmpty()) {
                        Text(
                            text = "No active torrents.\nPaste a magnet link above to start.",
                            modifier = Modifier.padding(top = 24.dp),
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(300.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 16.dp),
                        ) {
                            items(torrents, key = { it.infoHash }) { torrent ->
                                TorrentCard(
                                    torrent = torrent,
                                    onPause = { viewModel.pause(torrent.infoHash) },
                                    onResume = { viewModel.resume(torrent.infoHash) },
                                    onRemove = { viewModel.remove(torrent.infoHash, deleteFiles = true) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MagnetInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onAdd: () -> Unit,
    onCreateFile: () -> Unit,
    onCreateFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreateMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            label = { Text("Magnet link") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onAdd() }),
        )
        androidx.compose.material3.Button(onClick = onAdd) { Text("Add") }
        Box {
            IconButton(onClick = { showCreateMenu = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Create torrent")
            }
            DropdownMenu(
                expanded = showCreateMenu,
                onDismissRequest = { showCreateMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text("From file") },
                    onClick = { showCreateMenu = false; onCreateFile() },
                )
                DropdownMenuItem(
                    text = { Text("From folder") },
                    onClick = { showCreateMenu = false; onCreateFolder() },
                )
            }
        }
    }
}

@Composable
private fun TorrentCard(
    torrent: TorrentInfo,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showShareMenu by remember { mutableStateOf(false) }
    var showPeers by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pieceHaveColor   = androidx.compose.material3.MaterialTheme.colorScheme.primary
    val pieceActiveColor = Color(0xFF43A047)
    val pieceEmptyColor  = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete torrent?") },
            text = { Text("\"${torrent.name}\" and all its downloaded files will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onRemove() }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(text = torrent.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = if (torrent.isPaused) "paused" else torrent.state,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
            )
            LinearProgressIndicator(
                progress = { torrent.progress },
                modifier = Modifier.fillMaxWidth(),
            )
            if (torrent.piecesMap.isNotEmpty()) {
                Canvas(modifier = Modifier.fillMaxWidth().height(8.dp).padding(top = 2.dp)) {
                    val n = torrent.piecesMap.length.coerceAtLeast(1)
                    val w = size.width / n
                    torrent.piecesMap.forEachIndexed { i, c ->
                        drawRect(
                            color = when (c) {
                                '1' -> pieceHaveColor
                                '2' -> pieceActiveColor
                                else -> pieceEmptyColor
                            },
                            topLeft = Offset(i * w, 0f),
                            size = Size(w + 0.5f, size.height),
                        )
                    }
                }
            }
            Text(
                text = "↓ ${torrent.dlRateKbs} KB/s  ↑ ${torrent.ulRateKbs} KB/s" +
                        "  peers: ${torrent.peers}  ${(torrent.progress * 100).toInt()}%",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 4.dp),
            )

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                // Pause / Resume
                if (torrent.isPaused) {
                    IconButton(onClick = onResume) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Resume")
                    }
                } else {
                    IconButton(onClick = onPause) {
                        Icon(Icons.Filled.Pause, contentDescription = "Pause")
                    }
                }
                // Peers toggle
                IconButton(onClick = { showPeers = !showPeers }) {
                    Icon(
                        if (showPeers) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (showPeers) "Hide peers" else "Show peers",
                    )
                }
                // Open Downloads folder
                IconButton(onClick = {
                    context.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
                }) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = "Open folder")
                }
                // Share magnet / .torrent
                Box {
                    IconButton(onClick = { showShareMenu = true }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share")
                    }
                    DropdownMenu(
                        expanded = showShareMenu,
                        onDismissRequest = { showShareMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Copy magnet") },
                            onClick = {
                                showShareMenu = false
                                val magnet = TorrentManager.getMagnetUri(torrent.infoHash)
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("magnet", magnet))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Share .torrent") },
                            onClick = {
                                showShareMenu = false
                                scope.launch(Dispatchers.IO) {
                                    val outputDir = context.filesDir.absolutePath + "/created_torrents"
                                    val path = TorrentManager.saveTorrentFile(torrent.infoHash, outputDir)
                                    if (path.isNotEmpty()) {
                                        val fileUri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            File(path),
                                        )
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/x-bittorrent"
                                            putExtra(Intent.EXTRA_STREAM, fileUri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        withContext(Dispatchers.Main) {
                                            context.startActivity(Intent.createChooser(intent, "Share .torrent"))
                                        }
                                    }
                                }
                            },
                        )
                    }
                }
                // Delete
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete")
                }
            }

            // Collapsible peer list
            AnimatedVisibility(visible = showPeers) {
                PeerList(peers = torrent.peerList)
            }
        }
    }
}

@Composable
private fun PeerList(peers: List<PeerInfo>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider()
        if (peers.isEmpty()) {
            Text(
                text = "No peers connected",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("IP:port", fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                Text("↓KB/s", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Text("↑KB/s", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Text("  %", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
            peers.forEach { peer ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = peer.ip,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text("%5d".format(peer.dlKbs), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text("%5d".format(peer.ulKbs), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text("%3d".format((peer.progress * 100).toInt()), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TorrentCardPreview() {
    SimpleTorrentTheme {
        TorrentCard(
            torrent = TorrentInfo(
                "abc123", "Big Buck Bunny", "downloading", false, 0.42f, 1024, 128, 7,
                piecesMap = "0".repeat(84) + "1".repeat(84) + "2".repeat(32),
                peerList = listOf(
                    PeerInfo("192.168.1.10:6881", 512, 64, 0.35f),
                    PeerInfo("10.0.0.5:51413", 128, 0, 0.80f),
                ),
            ),
            onPause = {}, onResume = {}, onRemove = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TorrentCardPausedPreview() {
    SimpleTorrentTheme {
        TorrentCard(
            torrent = TorrentInfo("abc123", "Big Buck Bunny", "downloading", true, 0.42f, 0, 0, 0),
            onPause = {}, onResume = {}, onRemove = {},
        )
    }
}
