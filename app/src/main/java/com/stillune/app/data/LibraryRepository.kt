package com.stillune.app.data

import android.content.ContentUris
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import com.stillune.app.analysis.AnalysisScheduler
import com.stillune.app.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ScanReport(val found: Int, val skipped: Int, val failed: Int)

@Singleton
class LibraryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: LibraryDatabase,
    private val dao: TrackDao,
    @IoDispatcher private val io: CoroutineDispatcher,
    private val scheduler: AnalysisScheduler? = null,
) {
    val tracks = dao.observe()
    private val scanLock = Mutex()
    private val preferences = context.getSharedPreferences("library", Context.MODE_PRIVATE)
    fun savedFolders(): Set<String> = preferences.getStringSet("folders", emptySet())!!.toSet()
    fun rememberFolder(uri: Uri) {
        preferences.edit().putStringSet("folders", savedFolders() + uri.toString()).apply()
    }

    suspend fun scanMedia(options: ScanOptions, progress: (Int) -> Unit): ScanReport = withContext(io) {
        scanLock.withLock {
            val base = if (Build.VERSION.SDK_INT >= 29) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val columns = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.MIME_TYPE,
                if (Build.VERSION.SDK_INT >= 29) MediaStore.Audio.Media.RELATIVE_PATH else MediaStore.Audio.Media.DATA,
                if (Build.VERSION.SDK_INT >= 29) MediaStore.Audio.Media.VOLUME_NAME else MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DATE_MODIFIED)
            val found = mutableListOf<Track>()
            var skipped = 0
            context.contentResolver.query(base, columns, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    val name = cursor.getString(1) ?: continue
                    if (!AudioFiles.accepts(name, cursor.getString(7))) { skipped++; continue }
                    val uri = ContentUris.withAppendedId(base, cursor.getLong(0))
                    val path = cursor.getString(8).orEmpty()
                    val folder = if (Build.VERSION.SDK_INT >= 29) path else path.substringBeforeLast('/', "") + "/"
                    val volume = cursor.getString(9).orEmpty().replace("external_primary", "primary")
                    val track = Track(uri.toString(), cursor.getString(2).clean(name.substringBeforeLast('.')),
                        cursor.getString(3).clean("未知艺术家"), cursor.getString(4).clean("本地音乐"),
                        cursor.getLong(5), cursor.getLong(6), name, folder, "media", AudioFiles.format(name),
                        identity = if (Build.VERSION.SDK_INT >= 29) "$volume:$folder$name" else uri.toString(),
                        modifiedAt = cursor.getLong(10) * 1000)
                    val identified = if (name.substringAfterLast('.').lowercase() in setOf("m4a", "mp4"))
                        track.copy(format = AudioFiles.format(name, mp4Codec(uri))) else track
                    if (identified.passes(options)) found += identified else skipped++
                    progress(found.size + skipped)
                }
            } ?: error("系统媒体库暂时不可用，请稍后重试。")
            merge(found)
            // Remove only records from this successfully scanned source; never delete phone files.
            val seen = found.map { it.identity }.toSet()
            db.withTransaction { dao.fromSource("media").filter { it.identity !in seen }.forEach { dao.remove(it.uri) } }
            ScanReport(found.size, skipped, 0)
        }
    }

    suspend fun scanFolder(tree: Uri, options: ScanOptions, progress: (Int) -> Unit): ScanReport = withContext(io) {
        scanLock.withLock {
            val root = DocumentFile.fromTreeUri(context, tree) ?: error("无法打开这个文件夹。")
            if (!root.canRead()) error("文件夹授权已失效，请重新选择。")
            val pending = ArrayDeque<DocumentFile>().apply { add(root) }
            val seenDirectories = mutableSetOf<String>()
            val found = mutableListOf<Track>(); var skipped = 0; var failed = 0
            while (pending.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                val directory = pending.removeFirst()
                if (!seenDirectories.add(directory.uri.toString())) continue
                // Query directly so provider errors cannot silently look like an empty successful scan.
                val parentId = DocumentsContract.getDocumentId(directory.uri)
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
                context.contentResolver.query(childrenUri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        currentCoroutineContext().ensureActive()
                        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
                        val name = c.getString(1).orEmpty(); val mime = c.getString(2)
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            DocumentFile.fromSingleUri(context, uri)?.let(pending::add)
                        } else if (AudioFiles.accepts(name, mime)) {
                            val folderPath = try { DocumentsContract.getDocumentId(directory.uri).substringAfter(':').trimEnd('/') + "/" }
                                catch (_: Exception) { directory.name.orEmpty() }
                            val track = readTrack(uri, tree.toString(), folderPath)
                            if (track == null) failed++ else if (track.passes(options)) found += track else skipped++
                            progress(found.size + skipped + failed)
                        }
                    }
                } ?: error("读取文件夹失败，请检查存储设备是否仍然连接。")
            }
            merge(found)
            // Keep entries after individual read failures rather than incorrectly removing valid music.
            if (failed == 0) {
                val identities = found.map { it.identity }.toSet()
                db.withTransaction { dao.fromSource(tree.toString()).filter { it.identity !in identities }.forEach { dao.remove(it.uri) } }
            }
            ScanReport(found.size, skipped, failed)
        }
    }

    suspend fun importFiles(uris: List<Uri>): ScanReport = withContext(io) {
        scanLock.withLock {
            val found = mutableListOf<Track>(); var failed = 0
            for (uri in uris) {
                currentCoroutineContext().ensureActive()
                val track = readTrack(uri, "document", "导入文件")
                if (track != null && AudioFiles.accepts(track.fileName, context.contentResolver.getType(uri))) found += track else failed++
            }
            merge(found)
            ScanReport(found.size, 0, failed)
        }
    }

    private suspend fun merge(found: List<Track>) {
        currentCoroutineContext().ensureActive()
        val previous = dao.observe().first().associateBy { it.identity }
        val merged = found.distinctBy { it.identity }.map { track ->
            // Preserve an already valid URI and its grant/source if this file is rediscovered through SAF.
            previous[track.identity]?.let { track.copy(uri = it.uri, source = it.source,
                sampleRate = maxOf(it.sampleRate, track.sampleRate), bitDepth = maxOf(it.bitDepth, track.bitDepth)) } ?: track
        }
        db.withTransaction { merged.chunked(200).forEach { dao.upsert(it) } }
        scheduler?.enqueue()
    }

    private fun readTrack(uri: Uri, source: String, folder: String): Track? {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "音频"
        var size = 0L
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use {
                if (it.moveToFirst()) { name = it.getString(0) ?: name; size = it.getLong(1) }
            }
            // Validate access now. The URI remains the original file; importing does not transcode or copy it.
            context.contentResolver.openFileDescriptor(uri, "r")?.use { } ?: return null
        } catch (_: Exception) { return null }
        var title = name.substringBeforeLast('.'); var artist = "未知艺术家"; var album = "本地音乐"
        var duration = 0L; var rate = 0; var bits = 0; var codec: String? = null
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).clean(title)
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).clean(artist)
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).clean(album)
            duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
        } catch (_: Exception) { /* LibVLC can play formats the platform metadata reader cannot parse. */ }
        finally { retriever.release() }
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME).orEmpty()
                if (!mime.startsWith("audio/")) continue
                codec = mime
                if (f.containsKey(MediaFormat.KEY_SAMPLE_RATE)) rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                if (f.containsKey("bits-per-sample")) bits = f.getInteger("bits-per-sample")
                break
            }
        } catch (_: Exception) { } finally { extractor.release() }
        val identity = fileIdentity(uri)
        if (name.substringAfterLast('.').lowercase() in setOf("m4a", "mp4")) codec = mp4Codec(uri) ?: codec
        return Track(uri.toString(), title, artist, album, duration, size, name, folder, source,
            AudioFiles.format(name, codec), rate, bits, identity,
            modifiedAt = try { DocumentFile.fromSingleUri(context, uri)?.lastModified() ?: 0 } catch (_: Exception) { 0 })
    }

    private fun mp4Codec(uri: Uri): String? = try {
        context.contentResolver.openFileDescriptor(uri, "r")?.let { fd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { input ->
                Mp4AudioCodec.detect(input.channel)
            }
        }
    } catch (_: Exception) { null }

    private fun fileIdentity(uri: Uri): String = try {
        when {
            uri.authority == "com.android.externalstorage.documents" -> DocumentsContract.getDocumentId(uri)
            Build.VERSION.SDK_INT >= 29 -> {
                val mediaUri = if (uri.authority == "com.android.providers.media.documents") {
                    val id = DocumentsContract.getDocumentId(uri)
                    if (id.startsWith("audio:")) ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id.substringAfter(':').toLong()) else uri
                } else uri
                if (mediaUri.authority != "media") uri.toString()
                else context.contentResolver.query(mediaUri, arrayOf(MediaStore.Audio.Media.VOLUME_NAME,
                    MediaStore.Audio.Media.RELATIVE_PATH, MediaStore.Audio.Media.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) "${c.getString(0).replace("external_primary", "primary")}:${c.getString(1).orEmpty()}${c.getString(2)}"
                    else uri.toString()
                } ?: uri.toString()
            }
            else -> uri.toString()
        }
    } catch (_: Exception) { uri.toString() }
}

private fun String?.clean(fallback: String) = this?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: fallback
