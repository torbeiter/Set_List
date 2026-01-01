package com.example.set_list.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.set_list.data.DrumKit
import com.example.set_list.ui.SetlistViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditSongScreen(
    navController: NavController,
    viewModel: SetlistViewModel,
    setlistId: Long,
    songId: Long?
) {
    var name by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var selectedKitNumber by remember { mutableStateOf(1) }
    var bpm by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val isEditMode = songId != null

    val kits by viewModel.allKits.collectAsState()
    var isDropdownExpanded by remember { mutableStateOf(false) }

    // Load existing song if editing
    LaunchedEffect(songId) {
        if (isEditMode) {
            val songWithKit = viewModel.selectedSetlist.value?.songs?.find { it.song.id == songId }
            if (songWithKit != null) {
                name = songWithKit.song.name
                artist = songWithKit.song.artist ?: ""
                selectedKitNumber = songWithKit.song.kitNumber
                bpm = songWithKit.song.bpm?.toString() ?: ""
                notes = songWithKit.song.notes ?: ""
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditMode) "Edit Song" else "New Song") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Song Name *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = artist,
                onValueChange = { artist = it },
                label = { Text("Artist (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // --- Kit Selection Dropdown ---
            ExposedDropdownMenuBox(
                expanded = isDropdownExpanded,
                onExpandedChange = { isDropdownExpanded = !isDropdownExpanded }
            ) {
                OutlinedTextField(
                    value = "Kit $selectedKitNumber: ${kits.find { it.kitNumber == selectedKitNumber }?.displayName ?: ""}",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Drum Kit") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDropdownExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )

                ExposedDropdownMenu(
                    expanded = isDropdownExpanded,
                    onDismissRequest = { isDropdownExpanded = false }
                ) {
                    if (kits.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Sync kits in MIDI screen first") },
                            onClick = {},
                            enabled = false
                        )
                    } else {
                        kits.forEach { kit ->
                            DropdownMenuItem(
                                text = { Text("Kit ${kit.kitNumber}: ${kit.displayName}") },
                                onClick = {
                                    selectedKitNumber = kit.kitNumber
                                    isDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = bpm,
                onValueChange = {
                    if (it.isEmpty() || it.all { char -> char.isDigit() }) {
                        bpm = it
                    }
                },
                label = { Text("BPM (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        isLoading = true
                        if (isEditMode) {
                            scope.launch {
                                val song = viewModel.selectedSetlist.value?.songs?.find { it.song.id == songId }?.song
                                if (song != null) {
                                    viewModel.updateSong(
                                        song.copy(
                                            name = name.trim(),
                                            artist = artist.trim().ifBlank { null },
                                            kitNumber = selectedKitNumber,
                                            bpm = bpm.toIntOrNull(),
                                            notes = notes.trim().ifBlank { null }
                                        )
                                    )
                                }
                                navController.popBackStack()
                            }
                        } else {
                            viewModel.addSong(
                                setlistId = setlistId,
                                name = name.trim(),
                                artist = artist.trim().ifBlank { null },
                                kitNumber = selectedKitNumber,
                                bpm = bpm.toIntOrNull(),
                                notes = notes.trim().ifBlank { null }
                            ) {
                                navController.popBackStack()
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank() && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(if (isEditMode) "Save Changes" else "Add Song")
                }
            }
        }
    }
}