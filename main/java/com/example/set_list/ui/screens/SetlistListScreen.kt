package com.example.set_list.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.set_list.data.Setlist
import com.example.set_list.ui.SetlistViewModel
import com.example.set_list.ui.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetlistListScreen(
    navController: NavController,
    viewModel: SetlistViewModel,
    onNavigateToMidi: () -> Unit
) {
    val setlists by viewModel.setlists.collectAsState()
    var showDeleteDialog by remember { mutableStateOf<Setlist?>(null) }
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Setlists") },
                actions = {
                    IconButton(onClick = onNavigateToMidi) {
                        Icon(Icons.Default.Bluetooth, "MIDI Connection")
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, "More Options")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("MIDI Debug") },
                            onClick = {
                                showMenu = false
                                navController.navigate(Screen.MidiDebug.route)
                            },
                            leadingIcon = { Icon(Icons.Default.BugReport, null) }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Screen.AddEditSetlist.createRoute()) }
            ) {
                Icon(Icons.Default.Add, "Add Setlist")
            }
        }
    ) { paddingValues ->
        if (setlists.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
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
                        "No setlists yet",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        "Tap + to create your first setlist",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(setlists, key = { it.id }) { setlist ->
                    SetlistCard(
                        setlist = setlist,
                        onClick = {
                            viewModel.selectSetlist(setlist.id)
                            navController.navigate(Screen.SetlistDetail.createRoute(setlist.id))
                        },
                        onEdit = {
                            navController.navigate(Screen.AddEditSetlist.createRoute(setlist.id))
                        },
                        onDelete = { showDeleteDialog = setlist }
                    )
                }
            }
        }
    }

    // Delete Confirmation Dialog
    showDeleteDialog?.let { setlist ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Setlist?") },
            text = { Text("Are you sure you want to delete \"${setlist.name}\"? This will also delete all songs in the setlist.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSetlist(setlist)
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
fun SetlistCard(
    setlist: Setlist,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = setlist.name,
                    style = MaterialTheme.typography.titleLarge
                )
                if (setlist.description != null) {
                    Text(
                        text = setlist.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
