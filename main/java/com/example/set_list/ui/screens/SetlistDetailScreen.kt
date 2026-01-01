package com.example.set_list.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.set_list.data.Song
import com.example.set_list.data.SongWithKit
import com.example.set_list.midi.MidiViewModel
import com.example.set_list.ui.SetlistViewModel
import com.example.set_list.ui.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetlistDetailScreen(
    navController: NavController,
    viewModel: SetlistViewModel,
    midiViewModel: MidiViewModel,
    setlistId: Long
) {
    val setlistWithSongs by viewModel.selectedSetlist.collectAsState()
    val setlist = setlistWithSongs?.setlist
    val songs = remember(setlistWithSongs) { mutableStateListOf(*(setlistWithSongs?.songs?.toTypedArray() ?: emptyArray())) }

    val connectedDevice by midiViewModel.connectedDevice.collectAsState()
    val currentKit by midiViewModel.currentKit.collectAsState()
    val connectionStatus by midiViewModel.connectionStatus.collectAsState()
    val errorMessage by midiViewModel.errorMessage.collectAsState()

    var showDeleteDialog by remember { mutableStateOf<Song?>(null) }
    var selectedSongIndex by remember { mutableStateOf<Int?>(null) }

    val listState = rememberLazyListState()
    var draggedItemIndex by remember { mutableStateOf<Int?>(null) }
    var currentDragY by remember { mutableStateOf(0f) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(setlistId) {
        viewModel.selectSetlist(setlistId)
    }

    LaunchedEffect(setlistWithSongs) {
        songs.clear()
        setlistWithSongs?.songs?.let { songs.addAll(it) }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(message = it)
            midiViewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(setlist?.name ?: "Setlist") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { navController.navigate(Screen.MidiConnection.route) }
                    ) {
                        Icon(
                            Icons.Default.Bluetooth,
                            "MIDI",
                            tint = if (connectedDevice != null) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Screen.AddEditSong.createRoute(setlistId)) }
            ) {
                Icon(Icons.Default.Add, "Add Song")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Connection Status Banner
            if (connectedDevice != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = connectionStatus,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        currentKit?.let {
                            Text(
                                text = " (Kit $it)",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = connectionStatus,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            if (songs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No songs yet",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            "Tap + to add your first song",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { offset ->
                                    val visibleItems = listState.layoutInfo.visibleItemsInfo
                                    draggedItemIndex = visibleItems.firstOrNull { item ->
                                        offset.y.toInt() in item.offset..(item.offset + item.size)
                                    }?.index
                                    currentDragY = offset.y
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    currentDragY += dragAmount.y
                                    val visibleItems = listState.layoutInfo.visibleItemsInfo
                                    val overItem = visibleItems.firstOrNull { item ->
                                        currentDragY.toInt() in item.offset..(item.offset + item.size) && item.index != draggedItemIndex
                                    }
                                    if (overItem != null && draggedItemIndex != null) {
                                        val from = draggedItemIndex!!
                                        val to = overItem.index
                                        val temp = songs[from]
                                        songs.removeAt(from)
                                        songs.add(to, temp)
                                        draggedItemIndex = to
                                    }
                                },
                                onDragEnd = {
                                    if (draggedItemIndex != null) {
                                        viewModel.reorderSongs(setlistId, songs.map { it.song })
                                    }
                                    draggedItemIndex = null
                                    currentDragY = 0f
                                },
                                onDragCancel = {
                                    draggedItemIndex = null
                                    currentDragY = 0f
                                }
                            )
                        }
                ) {
                    itemsIndexed(
                        items = songs,
                        key = { _, songWithKit -> songWithKit.song.id }
                    ) { index, songWithKit ->
                        SongCard(
                            songWithKit = songWithKit,
                            isSelected = selectedSongIndex == index,
                            isConnected = connectedDevice != null,
                            onClick = {
                                selectedSongIndex = index
                                if (connectedDevice != null) {
                                    midiViewModel.switchToKit(songWithKit.song.kitNumber)
                                }
                            },
                            onEdit = { navController.navigate(Screen.AddEditSong.createRoute(setlistId, songWithKit.song.id)) },
                            onDelete = { showDeleteDialog = songWithKit.song }
                        )
                    }
                }
            }
        }
    }

    showDeleteDialog?.let { song ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Song?") },
            text = { Text("Are you sure you want to delete \"${song.name}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSong(song)
                        showDeleteDialog = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongCard(
    songWithKit: SongWithKit,
    isSelected: Boolean,
    isConnected: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val song = songWithKit.song
    val kit = songWithKit.kit

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected) {
                    Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                } else {
                    Modifier
                }
            ),
        colors = if (isSelected) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSelected) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = song.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (song.artist != null) {
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Kit ${kit.kitNumber}: ${kit.displayName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    song.bpm?.let { bpm ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Speed,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$bpm BPM",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (song.notes != null) {
                    Text(
                        text = song.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, "Menu")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = {
                            showMenu = false
                            onEdit()
                        },
                        leadingIcon = { Icon(Icons.Default.Edit, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, null) }
                    )
                }
            }
        }
    }
}
