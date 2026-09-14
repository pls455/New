package com.nova.music.data

import android.content.ContentResolver
import android.provider.MediaStore

class MusicRepository(private val resolver: ContentResolver) {
    fun loadTracks(): List<Track> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val result = mutableListOf<Track>()
        resolver.query(
            collection,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        )?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val title = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val album = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val duration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumId = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            while (cursor.moveToNext()) {
                val trackId = cursor.getLong(id)
                result += Track(
                    id = trackId,
                    uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.buildUpon()
                        .appendPath(trackId.toString()).build(),
                    title = cursor.getString(title) ?: "Unknown title",
                    artist = cursor.getString(artist) ?: "Unknown artist",
                    album = cursor.getString(album) ?: "Unknown album",
                    durationMs = cursor.getLong(duration),
                    albumId = cursor.getLong(albumId)
                )
            }
        }
        return result
    }
}
