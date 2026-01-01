package com.example.set_list

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.set_list.data.SetlistRepository
import com.example.set_list.midi.MidiViewModel
import com.example.set_list.ui.SetlistViewModel

class AppViewModelFactory(private val application: Application, private val repository: SetlistRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(SetlistViewModel::class.java) -> {
                SetlistViewModel(repository) as T
            }
            modelClass.isAssignableFrom(MidiViewModel::class.java) -> {
                MidiViewModel(application, repository) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
