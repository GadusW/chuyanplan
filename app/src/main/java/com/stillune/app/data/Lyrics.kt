package com.stillune.app.data

data class LyricLine(val timeMs: Long?, val text: String)
data class Lyrics(val lines: List<LyricLine> = emptyList(), val source: String = "", val message: String? = null) {
    val synced get() = lines.any { it.timeMs != null }
    fun activeIndex(positionMs: Long): Int = lines.indexOfLast { it.timeMs != null && it.timeMs <= positionMs }
}

object LrcParser {
    private val timestamp = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")
    private val offsetTag = Regex("\\[offset:([+-]?\\d+)]", RegexOption.IGNORE_CASE)
    private val metadata = Regex("^\\[(?:ar|ti|al|by|re|ve|length|offset):.*]$", RegexOption.IGNORE_CASE)
    fun parse(text: String, source: String = ""): Lyrics {
        val offset = offsetTag.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0
        val timed = mutableListOf<LyricLine>()
        val plain = mutableListOf<LyricLine>()
        text.removePrefix("\uFEFF").lineSequence().take(10000).forEach { raw ->
            val line = raw.trim()
            val stamps = timestamp.findAll(line).toList()
            if (stamps.isNotEmpty()) {
                val content = timestamp.replace(line, "").trim()
                if (content.isNotEmpty()) for (stamp in stamps) {
                    val seconds = stamp.groupValues[2].toLong()
                    if (seconds >= 60) continue
                    val fraction = stamp.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0
                    val time = stamp.groupValues[1].toLong() * 60000 + seconds * 1000 + fraction + offset
                    timed += LyricLine(time.coerceAtLeast(0), content)
                }
            } else if (line.isNotBlank() && !metadata.matches(line)) plain += LyricLine(null, line)
        }
        return Lyrics(if (timed.isNotEmpty()) timed.sortedBy { it.timeMs } else plain, source)
    }
}
