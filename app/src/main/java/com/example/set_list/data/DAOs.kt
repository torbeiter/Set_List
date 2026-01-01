package com.example.set_list.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DrumKitDao {
    @Query("SELECT * FROM drum_kits ORDER BY kitNumber ASC")
    fun getAllKits(): Flow<List<DrumKit>>

    @Query("SELECT * FROM drum_kits WHERE isFavorite = 1 ORDER BY kitNumber ASC")
    fun getFavoriteKits(): Flow<List<DrumKit>>

    @Query("SELECT * FROM drum_kits WHERE kitNumber = :kitNumber")
    suspend fun getKit(kitNumber: Int): DrumKit?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKit(kit: DrumKit)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKits(kits: List<DrumKit>)

    @Update
    suspend fun updateKit(kit: DrumKit)

    @Query("UPDATE drum_kits SET customName = :customName WHERE kitNumber = :kitNumber")
    suspend fun updateKitName(kitNumber: Int, customName: String?)

    @Query("UPDATE drum_kits SET isFavorite = :isFavorite WHERE kitNumber = :kitNumber")
    suspend fun updateKitFavorite(kitNumber: Int, isFavorite: Boolean)
}

@Dao
interface SetlistDao {
    @Query("SELECT * FROM setlists ORDER BY updatedAt DESC")
    fun getAllSetlists(): Flow<List<Setlist>>

    @Query("SELECT * FROM setlists WHERE id = :id")
    suspend fun getSetlist(id: Long): Setlist?

    @Query("SELECT * FROM setlists WHERE id = :id")
    fun getSetlistFlow(id: Long): Flow<Setlist?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetlist(setlist: Setlist): Long

    @Update
    suspend fun updateSetlist(setlist: Setlist)

    @Delete
    suspend fun deleteSetlist(setlist: Setlist)

    @Query("DELETE FROM setlists WHERE id = :id")
    suspend fun deleteSetlistById(id: Long)
}

@Dao
interface SongDao {
    @Query("SELECT * FROM songs WHERE setlistId = :setlistId ORDER BY position ASC")
    fun getSongsForSetlist(setlistId: Long): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getSong(id: Long): Song?

    @Transaction
    @Query("""
        SELECT s.*, dk.* 
        FROM songs AS s
        INNER JOIN drum_kits AS dk ON s.kitNumber = dk.kitNumber 
        WHERE s.setlistId = :setlistId 
        ORDER BY s.position ASC
    """)
    fun getSongsWithKits(setlistId: Long): Flow<Map<Song, DrumKit>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: Song): Long

    @Update
    suspend fun updateSong(song: Song)

    @Delete
    suspend fun deleteSong(song: Song)

    @Query("DELETE FROM songs WHERE id = :id")
    suspend fun deleteSongById(id: Long)

    @Query("SELECT MAX(position) FROM songs WHERE setlistId = :setlistId")
    suspend fun getMaxPosition(setlistId: Long): Int?

    @Query("UPDATE songs SET position = :position WHERE id = :songId")
    suspend fun updateSongPosition(songId: Long, position: Int)

    @Transaction
    suspend fun updateSongPositions(songs: List<Song>) {
        songs.forEachIndexed { index, song ->
            updateSongPosition(song.id, index)
        }
    }
}