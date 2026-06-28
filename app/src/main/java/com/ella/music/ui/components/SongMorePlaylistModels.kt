package com.ella.music.ui.components

import com.ella.music.R
import com.ella.music.data.model.FAVORITES_PLAYLIST_ID
import com.ella.music.data.model.UserPlaylist
import java.util.Locale

internal enum class AddPlaylistSortMode(val labelRes: Int) {
    Custom(R.string.playlist_sort_custom),
    UpdatedAt(R.string.playlist_sort_updated_at),
    Name(R.string.playlist_sort_name),
    SongCount(R.string.playlist_sort_song_count);

    fun next(): AddPlaylistSortMode = entries[(ordinal + 1) % entries.size]
}

internal fun List<UserPlaylist>.sortedForAddToPlaylist(mode: AddPlaylistSortMode): List<UserPlaylist> {
    val favorites = firstOrNull { it.id == FAVORITES_PLAYLIST_ID }
    val others = filterNot { it.id == FAVORITES_PLAYLIST_ID }
    val sortedOthers = when (mode) {
        AddPlaylistSortMode.Custom -> others
        AddPlaylistSortMode.UpdatedAt -> others.sortedWith(
            compareByDescending<UserPlaylist> { it.updatedAt }
                .thenByDescending { it.createdAt }
                .thenBy { it.name.lowercase(Locale.ROOT) }
                .thenBy { it.id }
        )
        AddPlaylistSortMode.Name -> others.sortedWith(
            compareBy<UserPlaylist> { it.name.lowercase(Locale.ROOT) }
                .thenByDescending { it.updatedAt }
                .thenByDescending { it.createdAt }
                .thenBy { it.id }
        )
        AddPlaylistSortMode.SongCount -> others.sortedWith(
            compareByDescending<UserPlaylist> { it.songs.size }
                .thenByDescending { it.updatedAt }
                .thenByDescending { it.createdAt }
                .thenBy { it.name.lowercase(Locale.ROOT) }
                .thenBy { it.id }
        )
    }
    return listOfNotNull(favorites) + sortedOthers
}
