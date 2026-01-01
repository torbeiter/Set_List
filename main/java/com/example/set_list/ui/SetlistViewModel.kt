package com.example.set_list.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.set_list.data.DrumKit
import com.example.set_list.data.Setlist
import com.example.set_list.data.SetlistRepository
import com.example.set_list.data.SetlistWithSongs
import com.example.set_list.data.Song
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class SetlistViewModel(private val repository: SetlistRepository) : ViewModel() {

    val setlists: StateFlow<List<Setlist>> = repository.getAllSetlists()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allKits: StateFlow<List<DrumKit>> = repository.getAllKits()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _selectedSetlistId = MutableStateFlow<Long?>(null)

    val selectedSetlist: StateFlow<SetlistWithSongs?> = _selectedSetlistId.flatMapLatest { id ->
        if (id == null) {
            kotlinx.coroutines.flow.flowOf(null)
        } else {
            repository.getSetlistWithSongsFlow(id)
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    fun selectSetlist(setlistId: Long) {
        _selectedSetlistId.value = setlistId
    }

    fun addSetlist(name: String, description: String?) {
        viewModelScope.launch {
            repository.insertSetlist(Setlist(name = name, description = description))
        }
    }

    fun updateSetlist(setlist: Setlist) {
        viewModelScope.launch {
            repository.updateSetlist(setlist)
        }
    }

    fun deleteSetlist(setlist: Setlist) {
        viewModelScope.launch {
            repository.deleteSetlist(setlist)
        }
    }

    fun addSong(setlistId: Long, name: String, artist: String?, kitNumber: Int, bpm: Int?, notes: String?, onSongAdded: () -> Unit) {
        viewModelScope.launch {
            val newSong = Song(
                setlistId = setlistId,
                name = name,
                artist = artist,
                kitNumber = kitNumber,
                bpm = bpm,
                notes = notes,
                position = 0 // Position will be auto-assigned by repository
            )
            repository.insertSong(newSong)
            onSongAdded()
        }
    }

    fun updateSong(song: Song) {
        viewModelScope.launch {
            repository.updateSong(song)
        }
    }

    fun deleteSong(song: Song) {
        viewModelScope.launch {
            repository.deleteSong(song)
        }
    }

    fun reorderSongs(setlistId: Long, songs: List<Song>) {
        viewModelScope.launch {
            repository.reorderSongs(setlistId, songs)
        }
    }
}