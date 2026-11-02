package com.stillune.app.data

object LibraryGrouping {
    val dimensions = listOf("歌曲", "专辑", "艺术家", "文件夹", "格式")
    fun key(track: Track, dimension: String): String = when (dimension) {
        "专辑" -> "${track.album}\u001f${track.artist}"
        "艺术家" -> track.artist
        "文件夹" -> (if (track.identity.startsWith("content:")) track.source else track.identity.substringBefore(':', "primary")) +
            "\u001f" + track.folder.ifBlank { "其他文件夹" }
        "格式" -> track.format.ifBlank { "未知格式" }.uppercase()
        else -> track.uri
    }
    fun label(key: String, dimension: String): String = when (dimension) {
        "专辑" -> key.substringBefore('\u001f')
        "文件夹" -> key.substringAfter('\u001f').trimEnd('/').substringAfterLast('/')
        else -> key
    }
    fun detail(key: String, dimension: String, count: Int): String = when (dimension) {
        "专辑" -> "${key.substringAfter('\u001f', "未知艺术家")} · $count 首"
        "文件夹" -> "${key.substringAfter('\u001f').trimEnd('/')} · $count 首"
        else -> "$count 首音乐"
    }
}
