package com.example.localmusic

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val uri: Uri,
    val folder: String
) {
    fun toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(
                    ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"), albumId
                    )
                )
                .build()
        )
        .build()
}

/** Reads all music files on the phone. No internet involved. */
fun loadSongs(context: Context): List<Song> {
    val songs = mutableListOf<Song>()
    val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.DURATION,
        if (Build.VERSION.SDK_INT >= 29) MediaStore.Audio.Media.RELATIVE_PATH
        else MediaStore.Audio.Media.DATA
    )
    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
    val sort = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

    context.contentResolver.query(collection, projection, selection, null, sort)?.use { c ->
        val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
        val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val pathCol = c.getColumnIndexOrThrow(
            if (Build.VERSION.SDK_INT >= 29) MediaStore.Audio.Media.RELATIVE_PATH
            else MediaStore.Audio.Media.DATA
        )
        while (c.moveToNext()) {
            val id = c.getLong(idCol)
            val artist = c.getString(artistCol)
            val rawPath = c.getString(pathCol) ?: ""
            val folder = if (Build.VERSION.SDK_INT >= 29) {
                rawPath.trimEnd('/')                       // e.g. "Music/Rock"
            } else {
                rawPath.substringBeforeLast('/', "")       // drop file name
                    .removePrefix("/storage/emulated/0")
                    .trim('/')
            }
            songs += Song(
                id = id,
                title = c.getString(titleCol) ?: "Unknown title",
                artist = if (artist.isNullOrBlank() || artist == "<unknown>") "Unknown artist" else artist,
                album = c.getString(albumCol) ?: "Unknown album",
                albumId = c.getLong(albumIdCol),
                durationMs = c.getLong(durCol),
                uri = ContentUris.withAppendedId(collection, id),
                folder = folder
            )
        }
    }
    return songs
}

/** Display name for a folder path like "Music/Rock" -> "Rock". */
fun folderName(path: String): String =
    if (path.isEmpty()) "Internal storage" else path.substringAfterLast('/')
