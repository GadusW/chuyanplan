package com.stillune.app.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stillune.app.analysis.AnalysisCache
import com.stillune.app.data.Track
import com.stillune.app.health.TrackReview

internal data class ReadingCard(val heading: String, val body: String)

internal data class TrackReading(
    val track: Track?,
    val card: ReadingCard,
    val loading: Boolean = false,
    val reviews: List<TrackReview> = emptyList(),
    val status: String? = null,
)

internal val analysisLessons = listOf(
    ReadingCard("频谱｜从左往右听", "打开「音频分析 → 实时」，看频谱的横轴。左边是低频，右边是高频。鼓点响起时，留意左侧有没有短暂抬起。"),
    ReadingCard("响度｜看最近三秒", "在实时响度里先看 Short Term。它反映最近约三秒的响度。比较安静的开头和更热闹的段落，看看读数如何变化。"),
    ReadingCard("声场｜看左右的差别", "打开实时声场的 Lissajous。点云越贴近正斜线，左右声道越相似。图形展开时，可以留意声音在左右两侧的分布。"),
    ReadingCard("电平｜峰值线为什么会停一下", "实时电平的白线短暂保留刚刚到达的峰值。试着对照灰色柱子，看看一声短促的鼓击过去后，两者回落的速度有什么不同。"),
    ReadingCard("频谱图｜把声音变化留在图上", "在实时频谱图里，颜色越亮代表该频段能量越强。试着找一条持续的亮带，再找一个转瞬即逝的亮点。"),
)

internal fun listeningHints(cache: AnalysisCache?): List<String> {
    if (cache == null || cache.state != "ready" || cache.sampleDurationMs <= 0) return emptyList()
    val start = if (cache.sampleStartMs == 0L) "0:00" else timeLabel(cache.sampleStartMs)
    val end = timeLabel(cache.sampleStartMs + cache.sampleDurationMs)
    val prefix = "栖声分析了 $start 至 $end 的片段。"
    return buildList {
        if (cache.sourceChannels >= 2) {
            if ((cache.correlation ?: 0.0) > .8) add(prefix + "这段左右声道比较相似。下次听时，留意哪些声音好像来自正中间。")
            if (cache.correlation?.let { it in -1.0..0.4 } == true) add(prefix + "这段左右声道的差异比较明显。下次听时，可以分别留意两侧的声音。")
        }
        if ((cache.drEstimate ?: 0.0) >= 10.0) add(prefix + "这段的峰值和平均电平相差比较明显。下次听时，留意短促的重音怎样从背景里冒出来。")
    }
}

@Composable
internal fun TrackReadingDialog(reading: TrackReading, onFind: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    var browserError by remember { mutableStateOf(false) }
    fun open(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            browserError = false
        } catch (_: ActivityNotFoundException) {
            browserError = true
        }
    }
    AlertDialog(onDismissRequest = onClose, containerColor = Panel,
        title = { Text(if (reading.track == null) "聆听卡片" else "关于这首歌", color = Moon) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 450.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                reading.track?.let { track ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(track.title, color = Moon, style = MaterialTheme.typography.titleMedium)
                        Text("${track.artist} · ${track.album}", color = Quiet)
                    }
                }
                if (reading.reviews.isEmpty()) {
                    Text(if (reading.card.heading == "下次听，可以留意") reading.card.heading else "一分钟看懂音频分析",
                        color = Mist, style = MaterialTheme.typography.titleSmall)
                    if (reading.card.heading != "下次听，可以留意") Text(reading.card.heading, color = Moon)
                    Text(reading.card.body, lineHeight = 21.sp)
                }
                reading.reviews.forEach { review ->
                    HorizontalDivider(color = Quiet.copy(alpha = .2f))
                    Text(if (review.album) "专辑延伸阅读" else "录音乐评", color = Mist)
                    Text(listOf(review.source, review.author).filter(String::isNotBlank).joinToString(" · "))
                    if (review.excerpt.isNotBlank()) {
                        Text(review.excerpt, lineHeight = 21.sp)
                        Text("原文节选 · ${review.license}", color = Quiet, fontSize = 12.sp)
                        TextButton(onClick = { open(review.licenseUrl) }) { Text("查看许可证") }
                    }
                    TextButton(onClick = { open("https://critiquebrainz.org/review/${review.id}") }) { Text("查看完整乐评") }
                }
                reading.status?.let { Text(it, color = Quiet) }
                if (reading.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (reading.track != null) {
                    if (reading.reviews.isEmpty()) {
                        Text("联网查找会向开放服务发送曲目标签。", color = Quiet, fontSize = 12.sp)
                        TextButton(onClick = onFind, enabled = !reading.loading) { Text("查找开放乐评") }
                    }
                    TextButton(onClick = {
                        val query = listOf(reading.track.title, reading.track.artist, reading.track.album, "乐评")
                            .joinToString(" ")
                        open("https://www.bing.com/search?q=${Uri.encode(query)}")
                    }) { Text("用浏览器找更多") }
                }
                if (browserError) Text("手机上没有可打开链接的应用。", color = Quiet)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("关闭") } })
}
