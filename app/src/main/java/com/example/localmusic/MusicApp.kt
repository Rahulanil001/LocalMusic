package com.example.localmusic

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MusicApp(player: Player) {
    val context = LocalContext.current
    val audioPermission = if (Build.VERSION.SDK_INT >= 33)
        Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, audioPermission) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val audioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    var songs by remember { mutableStateOf(emptyList<Song>()) }
    var loading by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(0) }            // 0 = Songs, 1 = Folders
    var openFolder by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { if (!granted) audioLauncher.launch(audioPermission) }

    LaunchedEffect(granted) {
        if (granted) {
            loading = true
            songs = withContext(Dispatchers.IO) { loadSongs(context) }
            loading = false
            // Ask for notification permission after storage permission
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val ui = rememberPlayerUiState(player)
    val current = remember(songs, ui.currentMediaId) {
        songs.firstOrNull { it.id.toString() == ui.currentMediaId }
    }
    val filtered = remember(songs, query) {
        if (query.isBlank()) songs else songs.filter {
            it.title.contains(query, true) || it.artist.contains(query, true) ||
                it.album.contains(query, true)
        }
    }

    val folders = remember(filtered) {
        filtered.groupBy { it.folder }.toList().sortedBy { it.first.lowercase() }
    }
    val visibleSongs = remember(filtered, tab, openFolder) {
        if (tab == 1 && openFolder != null) filtered.filter { it.folder == openFolder } else filtered
    }

    // Back button inside a folder returns to the folder list
    BackHandler(enabled = !showPlayer && tab == 1 && openFolder != null) { openFolder = null }

    fun playList(list: List<Song>, index: Int) {
        player.setMediaItems(list.map { it.toMediaItem() }, index, 0L)
        player.prepare()
        player.play()
    }

    if (showPlayer && current != null) {
        BackHandler { showPlayer = false }
        FullPlayer(current, ui, player, onClose = { showPlayer = false })
        return
    }

    Scaffold(
        topBar = {
          Column {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search songs, artists, albums") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, "Clear") }
                    }
                },
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    .padding(top = 24.dp)
            )
            TabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0; openFolder = null },
                    text = { Text("Songs") }
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1; openFolder = null },
                    text = { Text("Folders") }
                )
            }
          }
        },
        bottomBar = {
            if (current != null) {
                MiniPlayer(current, ui, player, onOpen = { showPlayer = true })
            }
        },
        floatingActionButton = {
            if (visibleSongs.isNotEmpty() && !(tab == 1 && openFolder == null)) {
                ExtendedFloatingActionButton(
                    onClick = {
                        player.shuffleModeEnabled = true
                        playList(visibleSongs, visibleSongs.indices.random())
                    },
                    icon = { Icon(Icons.Filled.Shuffle, null) },
                    text = { Text("Shuffle") }
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                !granted -> Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Permission is needed to find the music on your phone.",
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = { audioLauncher.launch(audioPermission) },
                        modifier = Modifier.padding(top = 16.dp)
                    ) { Text("Grant permission") }
                }
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                filtered.isEmpty() -> Text(
                    if (songs.isEmpty()) "No music found on this device." else "No matches.",
                    Modifier.align(Alignment.Center)
                )
                // Folder list
                tab == 1 && openFolder == null -> LazyColumn(Modifier.fillMaxSize()) {
                    items(folders, key = { it.first }) { (path, list) ->
                        ListItem(
                            headlineContent = {
                                Text(folderName(path), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = {
                                Text(
                                    "${list.size} ${if (list.size == 1) "song" else "songs"}" +
                                        if (path.isNotEmpty()) " • $path" else "",
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingContent = {
                                Icon(
                                    Icons.Filled.Folder, null,
                                    Modifier.size(40.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            modifier = Modifier.clickable { openFolder = path }
                        )
                    }
                }
                // Song list (all songs, or the songs inside one folder)
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    val folderPath = openFolder
                    if (tab == 1 && folderPath != null) {
                        item {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { openFolder = null }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to folders")
                                }
                                Text(
                                    folderName(folderPath),
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    items(visibleSongs, key = { it.id }) { song ->
                        SongRow(
                            song = song,
                            isCurrent = song.id == current?.id,
                            onClick = { playList(visibleSongs, visibleSongs.indexOf(song)) }
                        )
                    }
                    // Space so the last item isn't hidden behind the shuffle button
                    item { Box(Modifier.size(80.dp)) }
                }
            }
        }
    }
}

@Composable
fun SongRow(song: Song, isCurrent: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(
                song.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (isCurrent) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
            )
        },
        supportingContent = {
            Text(
                "${song.artist} • ${formatTime(song.durationMs)}",
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            AlbumArt(song, Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)), sizePx = 128)
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
fun MiniPlayer(song: Song, ui: PlayerUiState, player: Player, onOpen: () -> Unit) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Column {
            LinearProgressIndicator(
                progress = {
                    if (ui.duration > 0) (ui.position.toFloat() / ui.duration).coerceIn(0f, 1f) else 0f
                },
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AlbumArt(song, Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)), sizePx = 128)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                        song.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                IconButton(onClick = { player.seekToPreviousMediaItem() }) {
                    Icon(Icons.Filled.SkipPrevious, "Previous")
                }
                IconButton(onClick = { if (ui.isPlaying) player.pause() else player.play() }) {
                    Icon(
                        if (ui.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        if (ui.isPlaying) "Pause" else "Play"
                    )
                }
                IconButton(onClick = { player.seekToNextMediaItem() }) {
                    Icon(Icons.Filled.SkipNext, "Next")
                }
            }
        }
    }
}

@Composable
fun FullPlayer(song: Song, ui: PlayerUiState, player: Player, onClose: () -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val sliderValue = if (dragging) dragValue else ui.position.toFloat()

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp).padding(top = 32.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.KeyboardArrowDown, "Close player", Modifier.size(32.dp))
                }
                Text(
                    "Now playing", Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium
                )
                Box(Modifier.size(48.dp)) // balances the close button
            }

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                AlbumArt(
                    song,
                    Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp)),
                    sizePx = 800
                )
            }

            Text(
                song.title, style = MaterialTheme.typography.headlineSmall,
                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
            )
            Text(
                "${song.artist} • ${song.album}", style = MaterialTheme.typography.bodyMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Slider(
                value = sliderValue.coerceIn(0f, ui.duration.toFloat().coerceAtLeast(1f)),
                onValueChange = { dragging = true; dragValue = it },
                onValueChangeFinished = {
                    player.seekTo(dragValue.toLong())
                    ui.position = dragValue.toLong()
                    dragging = false
                },
                valueRange = 0f..ui.duration.toFloat().coerceAtLeast(1f),
                modifier = Modifier.padding(top = 16.dp)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(sliderValue.toLong()), style = MaterialTheme.typography.labelMedium)
                Text(formatTime(ui.duration), style = MaterialTheme.typography.labelMedium)
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { player.shuffleModeEnabled = !ui.shuffle }) {
                    Icon(
                        Icons.Filled.Shuffle, "Shuffle",
                        tint = if (ui.shuffle) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { player.seekToPreviousMediaItem() }) {
                    Icon(Icons.Filled.SkipPrevious, "Previous", Modifier.size(36.dp))
                }
                FilledIconButton(
                    onClick = { if (ui.isPlaying) player.pause() else player.play() },
                    modifier = Modifier.size(72.dp),
                    colors = IconButtonDefaults.filledIconButtonColors()
                ) {
                    Icon(
                        if (ui.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        if (ui.isPlaying) "Pause" else "Play",
                        Modifier.size(40.dp)
                    )
                }
                IconButton(onClick = { player.seekToNextMediaItem() }) {
                    Icon(Icons.Filled.SkipNext, "Next", Modifier.size(36.dp))
                }
                IconButton(onClick = {
                    player.repeatMode = when (ui.repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                }) {
                    Icon(
                        if (ui.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne
                        else Icons.Filled.Repeat,
                        "Repeat",
                        tint = if (ui.repeatMode != Player.REPEAT_MODE_OFF)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Album art from the phone's own files (embedded cover or MediaStore thumbnail). */
@Composable
fun AlbumArt(song: Song?, modifier: Modifier = Modifier, sizePx: Int = 256) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, song?.id, sizePx) {
        value = if (song == null) null
        else withContext(Dispatchers.IO) { loadArt(context, song, sizePx) }
    }
    Box(
        modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(
                Icons.Filled.MusicNote, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxSize(0.5f)
            )
        }
    }
}

private fun loadArt(context: Context, song: Song, sizePx: Int): ImageBitmap? = try {
    if (Build.VERSION.SDK_INT >= 29) {
        context.contentResolver.loadThumbnail(song.uri, Size(sizePx, sizePx), null).asImageBitmap()
    } else {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, song.uri)
            retriever.embeddedPicture?.let {
                BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap()
            }
        } finally {
            retriever.release()
        }
    }
} catch (e: Exception) {
    null
}

fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
