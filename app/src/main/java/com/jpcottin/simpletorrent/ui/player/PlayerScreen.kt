package com.jpcottin.simpletorrent.ui.player

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.media3.common.Player
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.material3.Text
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.jpcottin.simpletorrent.theme.SimpleTorrentTheme
import java.io.File
import java.util.Locale

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(filePath: String, title: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isPreview = LocalInspectionMode.current
    val prefs = remember { context.getSharedPreferences("player_positions", Context.MODE_PRIVATE) }
    var isBuffering by remember { mutableStateOf(true) }
    var showSubtitleMenu by remember { mutableStateOf(false) }
    var subtitles by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    val player = remember {
        if (isPreview) return@remember null
        val videoFile = File(filePath)
        val videoDir = videoFile.parentFile
        val baseNameNoExt = videoFile.nameWithoutExtension

        // Find all .srt files in the same directory
        val srtConfigs = videoDir?.listFiles { file ->
            file.isFile && file.extension == "srt"
        }?.mapNotNull { srtFile ->
            // Extract language code from filename pattern: *_xx.srt or subtitle_xx.srt
            val match = Regex("([a-z]{2})\\.srt$").find(srtFile.name)
            if (match != null) {
                val languageCode = match.groupValues[1]
                val displayLanguage = Locale(languageCode).displayLanguage
                subtitles = subtitles + (languageCode to displayLanguage)
                MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(srtFile))
                    .setMimeType("application/x-subrip")
                    .setLanguage(languageCode)
                    .setLabel(displayLanguage)
                    .build()
            } else null
        } ?: emptyList()

        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.Builder()
                .setUri(Uri.fromFile(videoFile))
                .setSubtitleConfigurations(srtConfigs)
                .build()

            setMediaItem(mediaItem)
            prepare()
            val savedPosition = prefs.getLong(filePath, 0L)
            if (savedPosition > 0L) seekTo(savedPosition)
            playWhenReady = true
        }
    }

    DisposableEffect(player) {
        if (player == null) return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    DisposableEffect(lifecycleOwner, player) {
        if (player == null) return@DisposableEffect onDispose {}
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    prefs.edit().putLong(filePath, player.currentPosition).apply()
                    player.pause()
                }
                Lifecycle.Event.ON_RESUME -> player.play()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            prefs.edit().putLong(filePath, player.currentPosition).apply()
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (player != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        setShowNextButton(false)
                        setShowPreviousButton(false)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = title,
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        if (isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
            )
        }
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(WindowInsets.safeDrawing.asPaddingValues()),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
            )
        }
        if (subtitles.isNotEmpty() && player != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(WindowInsets.safeDrawing.asPaddingValues()),
            ) {
                IconButton(onClick = { showSubtitleMenu = !showSubtitleMenu }) {
                    Icon(
                        Icons.Default.ClosedCaption,
                        contentDescription = "Subtitles",
                        tint = Color.White,
                    )
                }
                DropdownMenu(
                    expanded = showSubtitleMenu,
                    onDismissRequest = { showSubtitleMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("None") },
                        onClick = {
                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                .setPreferredTextLanguages()
                                .build()
                            showSubtitleMenu = false
                        },
                    )
                    subtitles.forEach { (languageCode, language) ->
                        DropdownMenuItem(
                            text = { Text(language) },
                            onClick = {
                                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                    .setPreferredTextLanguages(languageCode)
                                    .build()
                                showSubtitleMenu = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "Player — buffering", showBackground = true)
@Composable
private fun PlayerScreenBufferingPreview() {
    SimpleTorrentTheme {
        PlayerScreen(
            filePath = "/storage/emulated/0/Download/BigBuckBunny.mp4",
            title = "BigBuckBunny.mp4",
            onBack = {},
        )
    }
}
