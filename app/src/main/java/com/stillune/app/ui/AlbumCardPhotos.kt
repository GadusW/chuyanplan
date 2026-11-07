package com.stillune.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/** App-private copies keep card photos available after the original file moves. */
internal class AlbumCardPhotos(private val context: Context) {
    private val preferences = context.getSharedPreferences("album_card_photos", Context.MODE_PRIVATE)
    private val directory = File(context.filesDir, "album-card-photos")

    fun photos(track: String): List<String> {
        val saved = JSONArray(preferences.getString("photos:$track", "[]"))
        return List(saved.length(), saved::getString)
    }

    fun preferred(track: String): String? = preferences.getString("preferred:$track", null)

    fun prefer(track: String, photo: String?) {
        preferences.edit().putString("preferred:$track", photo).apply()
    }

    suspend fun remove(track: String, photo: String) = withContext(Dispatchers.IO) {
        synchronized(preferences) {
            val saved = photos(track)
            if (photo !in saved) return@withContext
            preferences.edit()
                .putString("photos:$track", JSONArray(saved - photo).toString())
                .apply {
                    if (preferred(track) == photo) remove("preferred:$track")
                }
                .apply()
        }
        File(directory, photo).delete()
        Unit
    }

    suspend fun load(photo: String): Bitmap? = withContext(Dispatchers.IO) {
        BitmapFactory.decodeFile(File(directory, photo).path)
    }

    suspend fun add(track: String, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val file = File(directory, UUID.randomUUID().toString())
        try {
            val bitmap = decode(uri) ?: return@withContext false
            directory.mkdirs()
            try {
                file.outputStream().use {
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) throw IOException()
                }
            } finally {
                bitmap.recycle()
            }
            synchronized(preferences) {
                preferences.edit().putString("photos:$track", JSONArray(photos(track) + file.name).toString()).apply()
            }
            true
        } catch (error: CancellationException) {
            file.delete()
            throw error
        } catch (_: Exception) {
            file.delete()
            false
        }
    }

    private fun decode(uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= 28) return decodeOriented(uri)
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longestEdge = maxOf(bounds.outWidth, bounds.outHeight)
        if (longestEdge <= 0) return null
        var sample = 1
        while (longestEdge / sample > 1080) sample *= 2
        return resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }

    @RequiresApi(28)
    private fun decodeOriented(uri: Uri): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val sample = maxOf(info.size.width, info.size.height) / 1080
            decoder.setTargetSampleSize(sample.coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
}
