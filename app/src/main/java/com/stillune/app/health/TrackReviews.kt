package com.stillune.app.health

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import com.stillune.app.data.Track
import com.stillune.app.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URL
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HttpsURLConnection
import kotlin.math.abs

internal data class TrackReview(
    val id: String, val album: Boolean, val author: String, val source: String,
    val license: String, val licenseUrl: String, val excerpt: String,
    val language: String,
)

@Singleton
class TrackReviews @Inject constructor(
    @ApplicationContext context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val prefs = context.getSharedPreferences("track-reviews", Context.MODE_PRIVATE)
    private val lock = Mutex()
    private var lastMusicBrainzRequest = 0L

    internal suspend fun lookup(track: Track): List<TrackReview> = lock.withLock {
        runInterruptible(io) {
            val key = JSONObject().put("schema", 2).put("title", track.title).put("artist", track.artist)
                .put("album", track.album).put("duration", track.durationMs).toString()
            val now = System.currentTimeMillis()
            val cached = prefs.getString(key, null)?.let(::JSONObject)
            if (cached != null && now - cached.getLong("time") in 0 until
                (if (cached.getJSONArray("items").length() == 0) 86_400_000L else 604_800_000L)
            ) return@runInterruptible decode(cached.getJSONArray("items"))

            val items = find(track)
            val oldest = prefs.all.entries.minByOrNull { (_, value) ->
                (value as? String)?.let { runCatching { JSONObject(it).optLong("time") }.getOrDefault(0) } ?: 0
            }
            prefs.edit {
                if (prefs.all.size >= 80 && cached == null) oldest?.let { remove(it.key) }
                putString(key, JSONObject().put("time", now).put("items", items).toString())
            }
            decode(items)
        }
    }

    private fun find(track: Track): JSONArray {
        if (track.title.isBlank() || track.artist.isBlank() || track.artist == "未知艺术家") return JSONArray()
        fun quoted(value: String) = "\"" + value.take(200).replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        val album = track.album.takeUnless { it.isBlank() || it == "本地音乐" }
        val query = "recording:${quoted(track.title)} AND artist:${quoted(track.artist)}" +
            (album?.let { " AND release:${quoted(it)}" } ?: "")
        val matches = musicBrainz("recording?fmt=json&limit=100&query=${Uri.encode(query)}")
        if (matches.optInt("count") > 100) return JSONArray()
        val candidates = matches.getJSONArray("recordings").objects().filter { recording ->
            val length = recording.optLong("length")
            val credit = recording.optJSONArray("artist-credit").objects()
                .joinToString("") { it.optString("name") + it.optString("joinphrase") }
            same(recording.optString("title"), track.title) && same(credit, track.artist) &&
                (track.durationMs <= 0 || (length > 0 && abs(length - track.durationMs) <= 4_000)) &&
                (album == null || recording.optJSONArray("releases").objects().any { same(it.optString("title"), album) })
        }
        val single = candidates.singleOrNull()?.let { reviews(it.optString("id"), "recording") } ?: JSONArray()
        if (single.length() > 0 || album == null) return single
        val releases = candidates.flatMap { it.optJSONArray("releases").objects() }
            .filter { same(it.optString("title"), album) }.distinctBy { it.optString("id") }
        val groups = releases.mapNotNull { it.optJSONObject("release-group")?.optString("id") }.distinct()
        val group = if (groups.size == 1 && releases.all { it.has("release-group") }) groups.single()
            else if (groups.isEmpty()) releases.singleOrNull()?.optString("id")?.takeIf { it.matches(UUID) }?.let {
                musicBrainz("release/$it?fmt=json&inc=release-groups").optJSONObject("release-group")?.optString("id")
            } else null
        return group?.let { reviews(it, "release_group") } ?: JSONArray()
    }

    private fun musicBrainz(path: String): JSONObject {
        val elapsed = android.os.SystemClock.elapsedRealtime() - lastMusicBrainzRequest
        if (elapsed < 1_100) Thread.sleep(1_100 - elapsed)
        lastMusicBrainzRequest = android.os.SystemClock.elapsedRealtime()
        return get("https://musicbrainz.org/ws/2/$path")
    }

    private fun reviews(id: String, type: String): JSONArray {
        if (!id.matches(UUID)) return JSONArray()
        val data = get("https://critiquebrainz.org/ws/1/review/?entity_id=$id&entity_type=$type&review_type=review&sort=popularity&limit=25")
        val rows = data.getJSONArray("reviews").objects()
            .filter { it.optString("id").matches(UUID) && it.optString("text").isNotBlank() && !it.isNull("text") &&
                !it.optBoolean("is_hidden") && !it.optBoolean("is_draft") &&
                it.optString("entity_id") == id && it.optString("entity_type") == type }
            .sortedByDescending { it.optString("language") == "zh" }
            .distinctBy { it.optString("id") }.take(4)
        return JSONArray().apply {
            rows.forEach { row ->
                val license = row.optString("license_id").ifBlank { row.optJSONObject("license")?.optString("id").orEmpty() }
                val author = row.optJSONObject("user")?.optString("display_name").orEmpty().take(120)
                val source = row.optString("source").takeUnless { it == "null" }.orEmpty().take(120)
                put(JSONObject().put("id", row.optString("id")).put("album", type == "release_group")
                    .put("author", if (source.isBlank()) author else "")
                    .put("source", source.ifBlank { "CritiqueBrainz" })
                    .put("language", row.optString("language"))
                    .put("license", license)
                    .put("excerpt", if (license == "CC BY-SA 3.0" && author.isNotBlank() && source.isBlank())
                        row.optString("text").take(600) else ""))
            }
        }
    }

    private fun decode(items: JSONArray): List<TrackReview> = items.objects().map {
        val license = it.optString("license")
        TrackReview(it.getString("id"), it.getBoolean("album"), it.optString("author"),
            it.optString("source"), license, licenses[license].orEmpty(), it.optString("excerpt"), it.optString("language"))
    }

    private fun get(url: String): JSONObject {
        val connection = URL(url).openConnection() as HttpsURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("User-Agent", "Stillune/0.12.0 (https://github.com/GadusW/chuyanplan)")
        connection.setRequestProperty("Accept", "application/json")
        try {
            if (connection.responseCode != 200) throw IOException("HTTP ${connection.responseCode}")
            val limit = 1_000_000
            val data = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val result = StringBuilder()
                val buffer = CharArray(4096)
                while (true) {
                    if (Thread.currentThread().isInterrupted) throw InterruptedException()
                    val count = reader.read(buffer)
                    if (count < 0) break
                    if (result.length + count > limit) throw IOException("Response too large")
                    result.append(buffer, 0, count)
                }
                result.toString()
            }
            return JSONObject(data)
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull(::optJSONObject)

    private fun same(a: String, b: String) = normalized(a).equals(normalized(b), ignoreCase = true)

    private fun normalized(text: String) = Normalizer.normalize(text.trim(), Normalizer.Form.NFKC)
        .replace(dashes, "-").replace('’', '\'').replace(spaces, " ")

    private companion object {
        val UUID = Regex("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
        val dashes = Regex("[‐‑‒–—−]")
        val spaces = Regex("\\s+")
        val licenses = mapOf(
            "CC BY-SA 3.0" to "https://creativecommons.org/licenses/by-sa/3.0/",
            "CC BY-NC-SA 3.0" to "https://creativecommons.org/licenses/by-nc-sa/3.0/",
        )
    }
}
