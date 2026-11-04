package com.stillune.app.health

import com.stillune.app.data.Track
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await

internal object ReviewTranslation {
    private val midnights = TrackReview(
        "897a06ec-7388-42ad-ad5d-cc086121afaa", true, "felipeqq2", "CritiqueBrainz",
        "CC BY-SA 3.0", "https://creativecommons.org/licenses/by-sa/3.0/",
        "Não é um álbum ruim, e melhora bastante conforme vai chegando no final, mas é definitivamente abaixo da média para Taylor Swift. Tem algumas músicas boas, e a maior parte do álbum é decente, mas algumas faixas são tão horríveis que estragam completamente Midnights, especialmente *Midnight Rain* e *Vigilante Shit*. Se algumas poucas faixas fossem substituidas por músicas da versão deluxe (3AM), o álbum seria infinitamente melhor.",
        "pt",
    )

    fun bundledReview(track: Track): TrackReview? = midnights.takeIf {
        track.artist.trim().equals("Taylor Swift", true) && track.album.trim().equals("Midnights", true)
    }

    fun bundled(review: TrackReview): String? = if (
        review.id == midnights.id && review.license == midnights.license && review.excerpt == midnights.excerpt
    ) "这张专辑并不差，越接近结尾越好听，但在泰勒·斯威夫特的作品里，它确实低于平均水平。里面有几首好歌，大部分也还过得去，不过有几首让我觉得很糟，尤其是《Midnight Rain》和《Vigilante Shit》，拉低了整张《Midnights》。如果能用 3AM 豪华版的几首歌换掉其中几首，这张专辑会好得多。" else null

    fun language(tag: String): String? {
        val code = tag.substringBefore('-').substringBefore('_').lowercase()
        return if (code == "zh" || code == "yue") null else TranslateLanguage.fromLanguageTag(code)
    }

    suspend fun translate(text: String, source: String): String {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(source).setTargetLanguage(TranslateLanguage.CHINESE).build()
        val translator = Translation.getClient(options)
        try {
            translator.downloadModelIfNeeded().await()
            return translator.translate(text).await()
        } finally {
            translator.close()
        }
    }
}
