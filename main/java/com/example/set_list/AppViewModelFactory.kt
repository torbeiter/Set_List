package com.example.set_list

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.set_list.data.SetlistRepository
import com.example.set_list.midi.MidiViewModel
import com.example.set_list.ui.SetlistViewModel

/**
 * ViewModel Factory to provide repositories and application context to ViewModels.
 */
class AppViewModelFactory(
    private val application: Application,
    private val repository: SetlistRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(SetlistViewModel::class.java) -> {
                @Suppress("UNCHECKED_CAST")
                SetlistViewModel(repository) as T
            }
            modelClass.isAssignableFrom(MidiViewModel::class.java) -> {
                @Suppress("UNCHECKED_CAST")
                MidiViewModel(application, repository) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}