package com.example.set_list.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Drum Kit Model
 * Represents a drum kit on the Roland V71
 */
@Entity(tableName = "drum_kits")
data class DrumKit(
    @PrimaryKey val kitNumber: Int, // 1-128
    val name: String,
    val customName: String? = null, // User can rename kits
    val isFavorite: Boolean = false
) {
    // Display name: use custom name if set, otherwise default name
    val displayName: String
        get() = customName ?: name
}

/**
 * Setlist Model
 */
@Entity(tableName = "setlists")
data class Setlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Song Model
 * Each song belongs to a setlist and has an assigned drum kit
 */
@Entity(
    tableName = "songs",
    foreignKeys = [
        ForeignKey(
            entity = Setlist::class,
            parentColumns = ["id"],
            childColumns = ["setlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = DrumKit::class,
            parentColumns = ["kitNumber"],
            childColumns = ["kitNumber"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("setlistId"), Index("kitNumber")]
)
data class Song(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val setlistId: Long,
    val name: String,
    val artist: String? = null,
    val kitNumber: Int, // Which drum kit to use
    val position: Int, // Order in setlist
    val notes: String? = null,
    val bpm: Int? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Setlist with Songs (for queries)
 */
data class SetlistWithSongs(
    val setlist: Setlist,
    val songs: List<SongWithKit>
)

/**
 * Song with Kit info (for display)
 */
data class SongWithKit(
    val song: Song,
    val kit: DrumKit
)