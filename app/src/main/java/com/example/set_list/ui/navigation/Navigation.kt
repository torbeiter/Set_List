package com.example.set_list.ui.navigation

sealed class Screen(val route: String) {
    object SetlistList : Screen("setlist_list")
    object SetlistDetail : Screen("setlist_detail/{setlistId}") {
        fun createRoute(setlistId: Long) = "setlist_detail/$setlistId"
    }
    object AddEditSetlist : Screen("add_edit_setlist?setlistId={setlistId}") {
        fun createRoute(setlistId: Long? = null) = if (setlistId != null) {
            "add_edit_setlist?setlistId=$setlistId"
        } else {
            "add_edit_setlist"
        }
    }
    object AddEditSong : Screen("add_edit_song?setlistId={setlistId}&songId={songId}") {
        fun createRoute(setlistId: Long, songId: Long? = null) = if (songId != null) {
            "add_edit_song?setlistId=$setlistId&songId=$songId"
        } else {
            "add_edit_song?setlistId=$setlistId"
        }
    }
    object MidiConnection : Screen("midi_connection")
    object MidiDebug : Screen("midi_debug")
}
