package com.localplayer.app.data.mediastore

import com.localplayer.app.data.model.Album
import com.localplayer.app.data.model.Artist
import com.localplayer.app.data.model.FolderNode
import com.localplayer.app.data.model.Library
import com.localplayer.app.data.model.Track
import com.localplayer.app.util.normalized

/** Чистые функции: фильтрация треков и группировка в альбомы, исполнителей и дерево папок. */
object LibraryBuilder {

    fun filter(tracks: List<Track>, minDurationSec: Int, excludedFolders: Set<String>): List<Track> {
        val minMs = minDurationSec * 1000L
        val excluded = excludedFolders.map { it.trim('/') }.filter { it.isNotEmpty() }
        return tracks.filter { track ->
            track.durationMs >= minMs && excluded.none { isInFolder(track.folderPath, it) }
        }
    }

    /**
     * Папка-исключение может быть абсолютной («/storage/emulated/0/WhatsApp/Media»)
     * или относительной («WhatsApp/Media») — совпадение по целым сегментам пути.
     */
    fun isInFolder(folderPath: String, excluded: String): Boolean =
        "/${folderPath.trim('/')}/".contains("/${excluded.trim('/')}/")

    fun build(tracks: List<Track>): Library {
        val albums = tracks.groupBy { it.albumId }.map { (albumId, albumTracks) ->
            val sorted = albumTracks.sortedWith(compareBy({ it.discNumber }, { it.trackNumber }, { it.title.normalized() }))
            Album(
                id = albumId,
                title = sorted.first().album,
                // Исполнитель альбома — самый частый среди его треков
                artist = sorted.groupingBy { it.artist }.eachCount().maxBy { it.value }.key,
                year = sorted.maxOf { it.year },
                coverTrackId = sorted.first().id,
                tracks = sorted,
            )
        }.sortedBy { it.title.normalized() }

        val artists = tracks.groupBy { it.artist }.map { (name, artistTracks) ->
            val sorted = artistTracks.sortedWith(
                compareBy({ it.album.normalized() }, { it.discNumber }, { it.trackNumber }, { it.title.normalized() }),
            )
            Artist(
                name = name,
                albumCount = sorted.distinctBy { it.albumId }.size,
                coverTrackId = sorted.first().id,
                tracks = sorted,
            )
        }.sortedBy { it.name.normalized() }

        val (root, index) = buildFolderTree(tracks)
        return Library(tracks, albums, artists, root, index)
    }

    private class MutableFolder(val path: String) {
        val subfolders = sortedMapOf<String, MutableFolder>()
        val tracks = mutableListOf<Track>()
    }

    /**
     * Строит дерево папок. Корень — самая глубокая общая папка: цепочку
     * «/storage/emulated/0/Music» без треков и ветвлений не показываем.
     */
    private fun buildFolderTree(tracks: List<Track>): Pair<FolderNode?, Map<String, FolderNode>> {
        if (tracks.isEmpty()) return null to emptyMap()
        val top = MutableFolder("")
        for (track in tracks) {
            var node = top
            val segments = track.folderPath.split('/').filter { it.isNotEmpty() }
            for (segment in segments) {
                val childPath = node.path + "/" + segment
                node = node.subfolders.getOrPut(segment) { MutableFolder(childPath) }
            }
            node.tracks += track
        }
        var root = top
        while (root.tracks.isEmpty() && root.subfolders.size == 1) {
            root = root.subfolders.values.first()
        }
        val index = HashMap<String, FolderNode>()
        val rootNode = freeze(root, index)
        return rootNode to index
    }

    private fun freeze(folder: MutableFolder, index: MutableMap<String, FolderNode>): FolderNode {
        val subs = folder.subfolders.values
            .map { freeze(it, index) }
            .sortedBy { it.name.normalized() }
        val node = FolderNode(
            path = folder.path,
            name = folder.path.substringAfterLast('/').ifEmpty { "/" },
            subfolders = subs,
            tracks = folder.tracks.sortedBy { it.fileName.normalized() },
            totalTracks = folder.tracks.size + subs.sumOf { it.totalTracks },
        )
        index[node.path] = node
        return node
    }
}
