package com.stillune.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.stillune.app.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

@Singleton
class PlayerAssetsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private data class Cover(val bitmap: Bitmap?)
    private val covers = object : android.util.LruCache<String, Cover>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Cover) = value.bitmap?.allocationByteCount ?: 1024
    }
    private val coverReads = Semaphore(3)
    private val preferences = context.getSharedPreferences("player_preferences", Context.MODE_PRIVATE)
    fun lyricsVisible() = preferences.getBoolean("show_lyrics", false)
    fun analysisMode() = preferences.getBoolean("analysis_mode", false)
    fun setAnalysisMode(enabled: Boolean) { preferences.edit().putBoolean("analysis_mode", enabled).apply() }
    fun setLyricsVisible(visible: Boolean) { preferences.edit().putBoolean("show_lyrics", visible).apply() }
    fun associateLyrics(track: Track, uri: Uri) {
        preferences.edit().putString("lyrics:${track.uri}", uri.toString()).apply()
    }
    suspend fun lyrics(track: Track): Lyrics = withContext(io) {
        try {
            val explicit = preferences.getString("lyrics:${track.uri}", null)?.let(Uri::parse)
            val uri = explicit ?: findSidecar(track) ?: return@withContext Lyrics()
            currentCoroutineContext().ensureActive()
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }.orEmpty()
            if (name.substringAfterLast('.').lowercase() !in setOf("lrc", "txt"))
                return@withContext Lyrics(message = "请选择 LRC 或 TXT 歌词文件。")
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(512 * 1024 + 1)
                var count = 0
                while (count < buffer.size) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer, count, buffer.size - count)
                    if (read < 0) break
                    count += read
                }
                buffer.copyOf(count)
            }
                ?: return@withContext Lyrics(message = "无法打开歌词，请重新导入。")
            if (bytes.size > 512 * 1024) return@withContext Lyrics(message = "歌词文件过大，请选择小于 512 KB 的文本。")
            val charset = when {
                bytes.size >= 2 && bytes[0] == 0xff.toByte() && bytes[1] == 0xfe.toByte() -> Charsets.UTF_16LE
                bytes.size >= 2 && bytes[0] == 0xfe.toByte() && bytes[1] == 0xff.toByte() -> Charsets.UTF_16BE
                else -> Charsets.UTF_8
            }
            val text = try { charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString() }
                catch (_: java.nio.charset.CharacterCodingException) { String(bytes, java.nio.charset.Charset.forName("GB18030")) }
            LrcParser.parse(text, name)
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { Lyrics(message = "歌词读取授权已失效，或文件已移动，请重新导入。") }
    }

    private suspend fun findSidecar(track: Track): Uri? {
        // Audio permission alone cannot read arbitrary .lrc files. Only inspect persistently granted trees.
        if (!track.identity.contains(':')) return null
        val parent = track.identity.substringBeforeLast('/', "")
        val base = track.fileName.substringBeforeLast('.')
        val names = listOf("$base.lrc", "$base.txt")
        for (permission in context.contentResolver.persistedUriPermissions) {
            currentCoroutineContext().ensureActive()
            val tree = permission.uri
            if (!permission.isReadPermission || tree.authority != "com.android.externalstorage.documents" || !DocumentsContract.isTreeUri(tree)) continue
            val root = DocumentsContract.getTreeDocumentId(tree).trimEnd('/')
            if (parent != root && !parent.startsWith("$root/")) continue
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
            val matches = mutableMapOf<String, Uri>()
            context.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    val name = cursor.getString(1).orEmpty()
                    names.firstOrNull { it.equals(name, true) }?.let {
                        matches[it] = DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0))
                    }
                }
            }
            names.firstNotNullOfOrNull { matches[it] }?.let { return it }
        }
        return null
    }

    private fun artworkKey(track: Track) = "${track.uri}:${track.modifiedAt}:${track.sizeBytes}"
    fun cachedArtwork(track: Track): Bitmap? = covers.get(artworkKey(track))?.bitmap

    suspend fun artwork(track: Track): Bitmap? = withContext(io) {
        val key = artworkKey(track)
        covers.get(key)?.let { return@withContext it.bitmap }
        coverReads.withPermit {
            covers.get(key)?.let { return@withPermit it.bitmap }
            val bitmap = readArtwork(track)
            currentCoroutineContext().ensureActive()
            covers.put(key, Cover(bitmap))
            bitmap
        }
    }

    private suspend fun readArtwork(track: Track): Bitmap? {
        val reader = MediaMetadataRetriever()
        return try {
            reader.setDataSource(context, Uri.parse(track.uri))
            val bytes = reader.embeddedPicture ?: return null
            if (bytes.size > 12 * 1024 * 1024) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 768) sample *= 2
            currentCoroutineContext().ensureActive()
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { null }
        finally { reader.release() }
    }
}
