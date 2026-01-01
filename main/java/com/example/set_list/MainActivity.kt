package com.example.set_list

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.set_list.data.AppDatabase
import com.example.set_list.data.SetlistRepository
import com.example.set_list.midi.BluetoothMidiScreen
import com.example.set_list.midi.MidiDebugScreen
import com.example.set_list.midi.MidiViewModel
import com.example.set_list.ui.SetlistViewModel
import com.example.set_list.ui.navigation.Screen
import com.example.set_list.ui.screens.AddEditSetlistScreen
import com.example.set_list.ui.screens.AddEditSongScreen
import com.example.set_list.ui.screens.SetlistDetailScreen
import com.example.set_list.ui.screens.SetlistListScreen
import com.example.set_list.ui.theme.SetListTheme

class MainActivity : ComponentActivity() {

    private lateinit var viewModelFactory: AppViewModelFactory

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database = AppDatabase.getDatabase(applicationContext)
        val repository = SetlistRepository(database)
        viewModelFactory = AppViewModelFactory(application, repository)

        setContent {
            SetListTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SetListApp(viewModelFactory)
                }
            }
        }
    }
}

@Composable
fun SetListApp(viewModelFactory: ViewModelProvider.Factory) {
    val navController = rememberNavController()

    val setlistViewModel: SetlistViewModel = viewModel(factory = viewModelFactory)
    val midiViewModel: MidiViewModel = viewModel(factory = viewModelFactory)

    NavHost(
        navController = navController,
        startDestination = Screen.SetlistList.route
    ) {
        composable(Screen.SetlistList.route) {
            SetlistListScreen(
                navController = navController,
                viewModel = setlistViewModel,
                onNavigateToMidi = { navController.navigate(Screen.MidiConnection.route) }
            )
        }

        composable(Screen.MidiConnection.route) {
            BluetoothMidiScreen(
                viewModel = midiViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.MidiDebug.route) {
            MidiDebugScreen(
                navController = navController,
                viewModel = midiViewModel
            )
        }

        composable(
            route = Screen.SetlistDetail.route,
            arguments = listOf(navArgument("setlistId") { type = NavType.LongType })
        ) { backStackEntry ->
            val setlistId = backStackEntry.arguments?.getLong("setlistId") ?: return@composable
            SetlistDetailScreen(
                navController = navController,
                viewModel = setlistViewModel,
                midiViewModel = midiViewModel,
                setlistId = setlistId
            )
        }

        composable(
            route = Screen.AddEditSetlist.route,
            arguments = listOf(navArgument("setlistId") {
                type = NavType.LongType
                defaultValue = -1L
            })
        ) { backStackEntry ->
            val setlistIdArg = backStackEntry.arguments?.getLong("setlistId") ?: -1L
            val setlistId = if (setlistIdArg == -1L || setlistIdArg <= 0) null else setlistIdArg
            AddEditSetlistScreen(
                navController = navController,
                viewModel = setlistViewModel,
                setlistId = setlistId
            )
        }

        composable(
            route = Screen.AddEditSong.route,
            arguments = listOf(
                navArgument("setlistId") { type = NavType.LongType },
                navArgument("songId") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { backStackEntry ->
            val setlistId = backStackEntry.arguments?.getLong("setlistId") ?: return@composable
            val songIdArg = backStackEntry.arguments?.getLong("songId") ?: -1L
            val songId = if (songIdArg == -1L || songIdArg <= 0) null else songIdArg
            AddEditSongScreen(
                navController = navController,
                viewModel = setlistViewModel,
                setlistId = setlistId,
                songId = songId
            )
        }
    }
}