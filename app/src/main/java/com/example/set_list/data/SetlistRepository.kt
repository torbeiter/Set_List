package com.example.set_list.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class SetlistRepository(private val database: AppDatabase) {

    private val drumKitDao = database.drumKitDao()
    private val setlistDao = database.setlistDao()
    private val songDao = database.songDao()

    // ========== Drum Kits ==========

    fun getAllKits(): Flow<List<DrumKit>> = drumKitDao.getAllKits()

    fun getFavoriteKits(): Flow<List<DrumKit>> = drumKitDao.getFavoriteKits()

    suspend fun getKit(kitNumber: Int): DrumKit? = drumKitDao.getKit(kitNumber)

    suspend fun updateKitName(kitNumber: Int, customName: String?) {
        drumKitDao.updateKitName(kitNumber, customName)
    }

    suspend fun updateKitFavorite(kitNumber: Int, isFavorite: Boolean) {
        drumKitDao.updateKitFavorite(kitNumber, isFavorite)
    }

    // ========== Setlists ==========

    fun getAllSetlists(): Flow<List<Setlist>> = setlistDao.getAllSetlists()

    suspend fun getSetlist(id: Long): Setlist? = setlistDao.getSetlist(id)

    private fun getSetlistFlow(id: Long): Flow<Setlist?> = setlistDao.getSetlistFlow(id)

    suspend fun insertSetlist(setlist: Setlist): Long = setlistDao.insertSetlist(setlist)

    suspend fun updateSetlist(setlist: Setlist) {
        setlistDao.updateSetlist(setlist.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteSetlist(setlist: Setlist) = setlistDao.deleteSetlist(setlist)

    suspend fun deleteSetlistById(id: Long) = setlistDao.deleteSetlistById(id)

    // ========== Songs ==========

    private fun getSongsWithKits(setlistId: Long): Flow<List<SongWithKit>> {
        return songDao.getSongsWithKits(setlistId).map { songKitMap ->
            songKitMap.map { (song, kit) ->
                SongWithKit(song, kit)
            }
        }
    }

    suspend fun getSong(id: Long): Song? = songDao.getSong(id)

    suspend fun insertSong(song: Song): Long {
        val position = if (song.position == 0) {
            (songDao.getMaxPosition(song.setlistId) ?: -1) + 1
        } else {
            song.position
        }
        return songDao.insertSong(song.copy(position = position))
    }

    suspend fun updateSong(song: Song) = songDao.updateSong(song)

    suspend fun deleteSong(song: Song) = songDao.deleteSong(song)

    suspend fun reorderSongs(setlistId: Long, songs: List<Song>) {
        songDao.updateSongPositions(songs)
    }

    // ========== Combined Operations ==========

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getSetlistWithSongsFlow(setlistId: Long): Flow<SetlistWithSongs?> {
        return getSetlistFlow(setlistId).flatMapLatest { setlist ->
            if (setlist == null) {
                flowOf(null)
            } else {
                getSongsWithKits(setlistId).map { songs ->
                    SetlistWithSongs(setlist, songs)
                }
            }
        }
    }
}