package com.example.set_list.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.set_list.ui.SetlistViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditSetlistScreen(
    navController: NavController,
    viewModel: SetlistViewModel,
    setlistId: Long?
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    val isEditMode = setlistId != null
    val scope = rememberCoroutineScope()

    // Load existing setlist if editing
    LaunchedEffect(setlistId) {
        if (isEditMode) {
            try {
                scope.launch {
                    val setlist = viewModel.setlists.first().find { it.id == setlistId }
                    if (setlist != null) {
                        name = setlist.name
                        description = setlist.description ?: ""
                    }
                }
            } catch (e: Exception) {
                // Log error or show toast/snackbar
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditMode) "Edit Setlist" else "New Setlist") },
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
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Setlist Name") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        if (isEditMode) {
                            viewModel.updateSetlist(
                                com.example.set_list.data.Setlist(
                                    id = setlistId!!,
                                    name = name.trim(),
                                    description = description.trim().ifBlank { null }
                                )
                            )
                        } else {
                            viewModel.addSetlist(
                                name = name.trim(),
                                description = description.trim().ifBlank { null }
                            )
                        }
                        navController.popBackStack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank()
            ) {
                Text(if (isEditMode) "Save Changes" else "Add Setlist")
            }
        }
    }
}