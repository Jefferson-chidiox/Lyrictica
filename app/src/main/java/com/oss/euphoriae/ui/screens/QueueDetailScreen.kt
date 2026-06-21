package com.oss.euphoriae.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.lyrictica.audio.PlaybackQueue
import com.lyrictica.audio.QueueSource
import com.lyrictica.audio.asSavedQueueSnapshot
import com.oss.euphoriae.data.model.Playlist
import com.oss.euphoriae.data.model.Song
import com.oss.euphoriae.ui.components.AddSongsToPlaylistDialog
import com.oss.euphoriae.ui.components.DeleteSongDialog
import com.oss.euphoriae.ui.components.SongListItem

private const val SAVED_QUEUE_CATEGORY = "Saved Queue"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun QueueDetailScreen(
    initialQueue: PlaybackQueue,
    savedQueues: List<PlaybackQueue>,
    playlists: List<Playlist>,
    activeQueueKey: String?,
    currentPlayingSongId: Long?,
    onBackClick: () -> Unit,
    onQueueSelected: (PlaybackQueue) -> Unit,
    onQueueKeySelected: (String) -> Unit,
    onSaveQueue: (PlaybackQueue) -> Unit,
    onSongClick: (Song, PlaybackQueue) -> Unit,
    onCreatePlaylistFromSongs: (String, List<Song>) -> Unit,
    onAddSongsToPlaylist: (Long, List<Song>) -> Unit,
    onAddToFavorites: (Song) -> Unit,
    onDeleteSong: (Song) -> Unit,
    onRemoveFromQueue: (String, Long) -> Unit,
    onDeleteQueue: (String) -> Unit,
    onReorderQueue: (String, Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    var queueToDelete by remember { mutableStateOf<PlaybackQueue?>(null) }
    var addToPlaylistTarget by remember { mutableStateOf<Song?>(null) }
    var deleteTargetSong by remember { mutableStateOf<Song?>(null) }
    var songsSortMode by rememberSaveable { mutableStateOf(SongsSortMode.ALPHABETICAL) }
    val savedQueueList = remember(savedQueues) {
        savedQueues
            .filter { (it.source as? QueueSource.Custom)?.category == SAVED_QUEUE_CATEGORY }
            .sortedByDescending { it.lastUsedAt }
    }

    var selectedQueueKey by rememberSaveable(initialQueue.key) {
        mutableStateOf(initialQueue.key)
    }

    val selectedQueue = remember(selectedQueueKey, initialQueue, savedQueueList) {
        savedQueueList.firstOrNull { it.key == selectedQueueKey } ?: initialQueue
    }
    val isMostPlayed = selectedQueue.source.key.startsWith("most_played_")
    val displayQueue = remember(selectedQueue, songsSortMode, isMostPlayed) {
        if (selectedQueue.isCustomOrder || isMostPlayed) {
            selectedQueue
        } else {
            selectedQueue.copy(songs = sortSongsForDisplay(selectedQueue.songs, songsSortMode))
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = selectedQueue.source.label,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = selectedQueue.source.category,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    val isMostPlayed = selectedQueue.source.key.startsWith("most_played_")
                    val isAllSongs = selectedQueue.source.key == "all_songs"
                    if (!selectedQueue.isCustomOrder) {
                        if (isMostPlayed) {
                            TextButton(onClick = {
                                val nextKey = when (selectedQueue.source.key) {
                                    "most_played_week" -> "most_played_month"
                                    "most_played_month" -> "most_played_all_time"
                                    else -> "most_played_week"
                                }
                                onQueueKeySelected(nextKey)
                            }) {
                                val toggleLabel = when (selectedQueue.source.key) {
                                    "most_played_week" -> "Week"
                                    "most_played_month" -> "Month"
                                    else -> "All Time"
                                }
                                Text(
                                    text = toggleLabel,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else if (isAllSongs) {
                            TextButton(onClick = { songsSortMode = songsSortMode.next() }) {
                                Text(
                                    text = songsSortMode.buttonLabel,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Queues"
                            )
                        }

                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Save queue") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    onSaveQueue(displayQueue.asSavedQueueSnapshot())
                                    menuOpen = false
                                }
                            )

                            if (savedQueueList.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No saved queues") },
                                    enabled = false,
                                    onClick = {}
                                )
                            } else {
                                savedQueueList.forEach { queue ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = queue.source.label,
                                                    fontWeight = if (queue.key == selectedQueue.key) FontWeight.Bold else FontWeight.Normal
                                                )
                                                Text(
                                                    text = queue.source.category,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        leadingIcon = {
                                            if (queue.key == selectedQueue.key) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null
                                                )
                                            }
                                        },
                                        trailingIcon = {
                                            IconButton(onClick = {
                                                queueToDelete = queue
                                                menuOpen = false
                                            }) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete queue"
                                                )
                                            }
                                        },
                                        onClick = {
                                            selectedQueueKey = queue.key
                                            onQueueSelected(queue)
                                            menuOpen = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        val isActiveQueue = selectedQueue.key == activeQueueKey
        val selectedSongs = displayQueue.songs
        val listState = rememberLazyListState()

        androidx.compose.runtime.LaunchedEffect(
            selectedQueue.key,
            currentPlayingSongId,
            selectedSongs.map { it.id },
            isActiveQueue
        ) {
            if (isActiveQueue && currentPlayingSongId != null) {
                val index = selectedSongs.indexOfFirst { it.id == currentPlayingSongId }
                if (index >= 0) {
                    listState.animateScrollToItem(index)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (selectedSongs.isEmpty()) {
                EmptyQueueState(
                    title = "No songs here",
                    message = "This queue is empty right now.",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp)
                )
            } else {
                ReorderableQueueList(
                    songs = selectedSongs,
                    listState = listState,
                    onMove = if (selectedQueue.isCustomOrder) {
                        { from, to -> onReorderQueue(selectedQueue.key, from, to) }
                    } else {
                        null
                    },
                    currentPlayingSongId = if (isActiveQueue) currentPlayingSongId else null,
                    queue = displayQueue,
                    onSongClick = onSongClick,
                    onAddToPlaylists = { addToPlaylistTarget = it },
                    onCreatePlaylist = { song -> onCreatePlaylistFromSongs(song.title, listOf(song)) },
                    onAddToFavorites = onAddToFavorites,
                    onDeletePermanently = { deleteTargetSong = it },
                    onRemoveFromQueue = { song -> onRemoveFromQueue(selectedQueue.key, song.id) },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

    }

    if (addToPlaylistTarget != null) {
        val targetSong = addToPlaylistTarget!!
        AddSongsToPlaylistDialog(
            playlists = playlists,
            songs = listOf(targetSong),
            onDismiss = { addToPlaylistTarget = null },
            onPlaylistSelected = { playlistItem ->
                onAddSongsToPlaylist(playlistItem.id, listOf(targetSong))
                addToPlaylistTarget = null
            },
            onCreatePlaylistClick = {
                onCreatePlaylistFromSongs(targetSong.title, listOf(targetSong))
                addToPlaylistTarget = null
            }
        )
    }

    if (deleteTargetSong != null) {
        val targetSong = deleteTargetSong!!
        DeleteSongDialog(
            song = targetSong,
            onDismiss = { deleteTargetSong = null },
            onConfirm = {
                onDeleteSong(targetSong)
                deleteTargetSong = null
            }
        )
    }

    if (queueToDelete != null) {
        val targetQueue = queueToDelete!!
        AlertDialog(
            onDismissRequest = { queueToDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Delete queue?") },
            text = {
                Text("Remove \"${targetQueue.source.label}\" from saved queues?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val fallbackQueue = savedQueueList.firstOrNull { it.key != targetQueue.key }
                        if (selectedQueue.key == targetQueue.key && fallbackQueue != null) {
                            selectedQueueKey = fallbackQueue.key
                            onQueueSelected(fallbackQueue)
                        }
                        onDeleteQueue(targetQueue.key)
                        queueToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { queueToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReorderableQueueList(
    songs: List<Song>,
    listState: LazyListState,
    onMove: ((Int, Int) -> Unit)?,
    currentPlayingSongId: Long?,
    queue: PlaybackQueue,
    onSongClick: (Song, PlaybackQueue) -> Unit,
    onAddToPlaylists: (Song) -> Unit,
    onCreatePlaylist: (Song) -> Unit,
    onAddToFavorites: (Song) -> Unit,
    onDeletePermanently: (Song) -> Unit,
    onRemoveFromQueue: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    val reorderState = rememberQueueDragDropState(listState, onMove ?: { _, _ -> })
    val dragModifier = if (onMove != null) {
        Modifier.pointerInput(reorderState) {
            detectDragGesturesAfterLongPress(
                onDragStart = { offset -> reorderState.onDragStart(offset) },
                onDrag = { _, dragAmount ->
                    reorderState.onDrag(dragAmount)
                },
                onDragEnd = {
                    reorderState.onDragInterrupted()
                },
                onDragCancel = {
                    reorderState.onDragInterrupted()
                }
            )
        }
    } else {
        Modifier
    }

    LazyColumn(
        modifier = modifier.then(dragModifier),
        state = listState,
        contentPadding = PaddingValues(bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(
            items = songs,
            key = { index, song -> song.lazyItemKey(index) }
        ) { index, song ->
            val isDragging = index == reorderState.currentIndexOfDraggedItem
            val displacement = reorderState.elementDisplacement.takeIf { isDragging }

            val itemModifier = Modifier
                .padding(horizontal = 8.dp)
                .zIndex(if (isDragging) 1f else 0f)
                .graphicsLayer {
                    translationY = displacement ?: 0f
                    scaleX = if (isDragging) 1.01f else 1f
                    scaleY = if (isDragging) 1.01f else 1f
                    alpha = if (isDragging) 0.98f else 1f
                }

            SongListItem(
                song = song,
                onClick = { onSongClick(song, queue) },
                isPlaying = currentPlayingSongId == song.id,
                onAddToPlaylists = { onAddToPlaylists(song) },
                onCreatePlaylist = { onCreatePlaylist(song) },
                onAddToFavorites = { onAddToFavorites(song) },
                onDeletePermanently = { onDeletePermanently(song) },
                onRemoveFromQueue = { onRemoveFromQueue(song) },
                modifier = itemModifier
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Stable
private class QueueDragDropState(
    val listState: LazyListState,
    private val onMove: (Int, Int) -> Unit
) {
    private var draggedDistance by mutableFloatStateOf(0f)
    private var initiallyDraggedElement by mutableStateOf<LazyListItemInfo?>(null)
    var currentIndexOfDraggedItem by mutableStateOf<Int?>(null)
        private set
    var previousIndexOfDraggedItem by mutableStateOf<Int?>(null)
        private set

    val elementDisplacement: Float?
        get() = currentIndexOfDraggedItem
            ?.let { index ->
                listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
            }
            ?.let { item ->
                val initialOffset = initiallyDraggedElement?.offset ?: 0
                initialOffset + draggedDistance - item.offset
            }

    fun onDragStart(offset: androidx.compose.ui.geometry.Offset) {
        listState.layoutInfo.visibleItemsInfo
            .firstOrNull { item -> offset.y.toInt() in item.offset..item.offsetEnd }
            ?.also { item ->
                currentIndexOfDraggedItem = item.index
                initiallyDraggedElement = item
                draggedDistance = 0f
                previousIndexOfDraggedItem = null
            }
    }

    fun onDrag(offset: androidx.compose.ui.geometry.Offset) {
        val currentIndex = currentIndexOfDraggedItem ?: return
        draggedDistance += offset.y

        val currentItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == currentIndex }
            ?: return
        val currentCenter = (initiallyDraggedElement?.offset ?: currentItem.offset).toFloat() + draggedDistance + currentItem.size / 2f

        val targetItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            item.index != currentIndex && currentCenter in item.offset.toFloat()..item.offsetEnd.toFloat()
        } ?: return

        previousIndexOfDraggedItem = currentIndex
        onMove(currentIndex, targetItem.index)
        currentIndexOfDraggedItem = targetItem.index
        initiallyDraggedElement = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetItem.index }
        draggedDistance = 0f
    }

    fun onDragInterrupted() {
        draggedDistance = 0f
        currentIndexOfDraggedItem = null
        previousIndexOfDraggedItem = null
        initiallyDraggedElement = null
    }
}

@Composable
private fun rememberQueueDragDropState(
    listState: LazyListState,
    onMove: (Int, Int) -> Unit
): QueueDragDropState = remember(listState, onMove) {
    QueueDragDropState(listState, onMove)
}

private val LazyListItemInfo.offsetEnd: Int
    get() = offset + size

@Composable
private fun EmptyQueueState(
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
