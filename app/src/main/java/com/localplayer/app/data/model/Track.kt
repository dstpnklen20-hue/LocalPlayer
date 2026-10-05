package com.localplayer.app.data.model

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore

/** Трек из MediaStore. Метаданные не дублируются в Room — там хранятся только id. */
data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val trackNumber: Int,
    val discNumber: Int,
    val year: Int,
    /** Абсолютный путь к папке без завершающего «/». */
    val folderPath: String,
    val fileName: String,
    /** Дата добавления в секундах (как в MediaStore). */
    val dateAdded: Long,
    val sizeBytes: Long,
    val mimeType: String,
) {
    val filePath: String get() = if (folderPath.isEmpty()) fileName else "$folderPath/$fileName"
}

val Track.uri: Uri
    get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val year: Int,
    /** Трек, из которого берётся обложка. */
    val coverTrackId: Long,
    val tracks: List<Track>,
)

data class Artist(
    val name: String,
    val albumCount: Int,
    val coverTrackId: Long,
    val tracks: List<Track>,
)

/** Узел дерева папок. [totalTracks] — с учётом вложенных папок. */
data class FolderNode(
    val path: String,
    val name: String,
    val subfolders: List<FolderNode>,
    val tracks: List<Track>,
    val totalTracks: Int,
) {
    /** Все треки папки вместе с вложенными. */
    fun allTracks(): List<Track> = buildList { collectTracks(this@FolderNode, this) }

    private fun collectTracks(node: FolderNode, out: MutableList<Track>) {
        out.addAll(node.tracks)
        node.subfolders.forEach { collectTracks(it, out) }
    }
}

/** Снимок всей библиотеки после фильтрации. */
data class Library(
    val tracks: List<Track>,
    val albums: List<Album>,
    val artists: List<Artist>,
    /** Корень отображаемого дерева папок (null, если треков нет). */
    val folderRoot: FolderNode?,
    val foldersByPath: Map<String, FolderNode>,
) {
    val tracksById: Map<Long, Track> by lazy { tracks.associateBy { it.id } }
    val albumsById: Map<Long, Album> by lazy { albums.associateBy { it.id } }
    val artistsByName: Map<String, Artist> by lazy { artists.associateBy { it.name } }

    companion object {
        val EMPTY = Library(emptyList(), emptyList(), emptyList(), null, emptyMap())
    }
}
