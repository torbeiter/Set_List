package com.example.set_list.midi

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MidiDebugScreen(
    navController: NavController,
    viewModel: MidiViewModel
) {
    val connectedDevice by viewModel.connectedDevice.collectAsState()
    val currentKit by viewModel.currentKit.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MIDI Debug") },
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
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (connectedDevice != null)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (connectedDevice != null) {
                            "✓ Connected: ${connectedDevice?.name}"
                        } else {
                            "✗ Not Connected - Go to MIDI Connection first!"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    currentKit?.let {
                        Text(
                            text = "Last command sent: Kit $it",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // KRITISCH: V71 Einstellungen prüfen
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🔴 STOP! Check V71 Settings First",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "On your V71, press SETUP button:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    listOf(
                        "1. Go to: SETUP → MIDI → Rx (Receive) Ch",
                        "   Set to: 10",
                        "",
                        "2. Go to: SETUP → MIDI → Program Change",
                        "   Set to: ON  ← This is CRITICAL!",
                        "",
                        "3. Go to: SETUP → MIDI → Sync",
                        "   Set to: OFF or AUTO",
                        "",
                        "4. Exit SETUP and return to normal mode"
                    ).forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = if (line.contains("Set to:")) FontFamily.Monospace else FontFamily.Default
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "⚠️ If \"Program Change\" is OFF, kit switching will NEVER work!",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Test Button
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Test Kit Switching",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "After fixing V71 settings above, press this button.\nWatch the V71 display - the kit number should change!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            // Sende verschiedene Kits
                            viewModel.switchToKit(1, 9)  // Channel 10, Kit 1
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = connectedDevice != null
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Switch to Kit 1", style = MaterialTheme.typography.titleMedium)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            viewModel.switchToKit(50, 9)  // Channel 10, Kit 50
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = connectedDevice != null
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Switch to Kit 50", style = MaterialTheme.typography.titleMedium)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            viewModel.switchToKit(100, 9)  // Channel 10, Kit 100
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = connectedDevice != null
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Switch to Kit 100", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            // Was passiert wenn es nicht funktioniert
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Still Not Working?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "If kit still doesn't change after pressing buttons above:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    listOf(
                        "1. Double-check V71: MIDI → Program Change = ON",
                        "2. Try turning V71 off and on again",
                        "3. Disconnect and reconnect Bluetooth",
                        "4. Check V71 Rx Channel is really set to 10",
                        "5. Look in Android LogCat for 'TX: Kit' messages"
                    ).forEach { step ->
                        Text(
                            text = step,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }

            // Technische Details für LogCat
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "For Developers: MIDI Details",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = """
                            Check Android Studio LogCat:
                            - Filter: "MidiViewModel"
                            - Look for: "TX: Kit #X on Channel 10"
                            - You should see: "0xC9 0xXX"
                            - After sync: Look for "V71 wakeup sequence"
                            
                            Channel 10 = 0xC9 (0xC0 + 9)
                            Kit number = Second byte (0-indexed)
                            
                            If kit switching stops after sync:
                            → Wait ~20 seconds for wakeup sequence
                            → Check log for "Sent MIDI Reset"
                            → If still broken after 1 minute, reconnect
                        """.trimIndent(),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Alternative: Channel Scanner
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Try Different Channels",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "If you're not sure channel 10 is correct, try these:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    listOf(
                        Pair(9, "Channel 10 (Standard Drums)"),
                        Pair(0, "Channel 1"),
                        Pair(15, "Channel 16")
                    ).forEach { (channel, label) ->
                        OutlinedButton(
                            onClick = {
                                viewModel.switchToKit(25, channel)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = connectedDevice != null
                        ) {
                            Text(label)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}