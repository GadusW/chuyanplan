package com.stillune.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stillune.app.analysis.*
import com.stillune.app.playback.PlaybackState
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.*

@Composable
internal fun AnalysisContent(cache: AnalysisCache?, state: PlaybackState, onRequest: () -> Unit, onSeek: (Long) -> Unit,
                             modifier: Modifier = Modifier) {
    var explain by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("声音画像", fontSize = 17.sp, color = Moon, fontWeight = FontWeight.Medium)
            }
            IconButton(onClick = { explain = true }) { Icon(Icons.Rounded.Info, "分析结果说明", tint = Quiet, modifier = Modifier.size(20.dp)) }
        }
        if (state.track == null || cache?.state != "ready") GlassSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(when { state.track == null -> "先选一首音乐"; cache?.state == "failed" -> "暂时无法分析这首音乐"
                    cache?.state == "analyzing" -> "正在描绘声音的纹理"; else -> "等待一个安静的时刻" }, color = Moon, fontSize = 15.sp)
                Text(when { state.track == null -> "歌曲入库后，会在闲时建立声音画像。"
                    cache?.state == "failed" -> cache.error ?: "文件暂不可读，播放不受影响。"
                    state.playing || state.loading -> "分析会让出播放资源，暂停后或后台空闲时继续。"
                    else -> "新入库先分析少量歌曲，其余在充电空闲时补齐；也可优先分析这首。" }, color = Quiet, fontSize = 12.sp, lineHeight = 19.sp)
                if (cache?.state == "analyzing") LinearProgressIndicator(Modifier.fillMaxWidth(), color = Mist)
                if (state.track != null) TextButton(onClick = onRequest, enabled = !state.playing && !state.loading && cache?.state != "analyzing") {
                    Text(if (state.playing || state.loading) "暂停后可分析" else if (cache?.state == "failed") "重新分析" else "优先分析这首")
                }
            }
        } else {
            SpectrumSeekbar(cache, state.positionMs, onSeek)
            AnalysisSection {
                Column(Modifier.padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("动态起伏", color = Quiet, fontSize = 12.sp)
                        Text("DR 片段估算", color = Quiet, fontSize = 11.sp)
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(cache.drEstimate?.let { "%.1f".format(Locale.ROOT, it) } ?: "—", color = Moon, fontSize = 30.sp)
                        Text("  dB", color = Quiet, fontSize = 11.sp, modifier = Modifier.padding(bottom = 5.dp))
                        Spacer(Modifier.weight(1f))
                        Text(when { cache.drEstimate == null -> "片段静音或过短"; cache.drEstimate < 8 -> "声音较紧凑"
                            cache.drEstimate < 12 -> "起伏较均衡"; else -> "动态较充足" }, color = Mist, fontSize = 13.sp, modifier = Modifier.padding(bottom = 5.dp))
                    }
                    Text("取约 1/3 处 ${cache.sampleDurationMs / 1000.0} 秒 · 非音质评分", color = Quiet, fontSize = 11.sp)
                }
            }
            StereoField(cache)
            Text("${if (cache.sourceChannels == 1) "单声道" else if (cache.sourceChannels > 2) "多声道下混为立体声" else "立体声统计"} · 片段从 ${cache.sampleStartMs / 1000} 秒开始",
                color = Quiet, fontSize = 11.sp)
        }
    }
    if (explain) AlertDialog(onDismissRequest = { explain = false }, title = { Text("怎样读懂声音画像") },
        text = { Text("频谱是整曲离线抽样 FFT 概览，横向是时间，纵向从低频到高频，点按可跳转。\n\nDR 使用约 1/3 处最多 20 秒片段，以 3 秒窗、较响窗口和峰值估算动态；不是整曲认证 DR，也不是 LUFS 或 LRA。\n\n声场图与相关性来自同一片段。接近 +1 更相似，接近 −1 反相较多；展开程度不是音质优劣。\n\n分析使用 44.1 kHz / 16-bit 立体声 PCM，不改变播放链路。") },
        confirmButton = { TextButton(onClick = { explain = false }) { Text("知道了") } },
        dismissButton = { TextButton(onClick = { explain = false; onRequest() },
            enabled = state.track != null && !state.playing && !state.loading) { Text("重新分析") } })
}

@Composable
internal fun SpectrumSeekbar(cache: AnalysisCache, positionMs: Long, onSeek: (Long) -> Unit) {
    val image = remember(cache.spectrum) {
        val colors = IntArray(AudioAccumulator.COLUMNS * AudioAccumulator.BANDS)
        for (x in 0 until AudioAccumulator.COLUMNS) for (band in 0 until AudioAccumulator.BANDS) {
            val value = (cache.spectrum.getOrElse(x*AudioAccumulator.BANDS+band) { 0 }.toInt() and 255)/255f
            val color = lerp(Color(0xFF1B2B43), lerp(Mist, Moon, value), value.pow(.7f))
            colors[(AudioAccumulator.BANDS-1-band)*AudioAccumulator.COLUMNS+x] = color.toArgb()
        }
        Bitmap.createBitmap(colors, AudioAccumulator.COLUMNS, AudioAccumulator.BANDS, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    val fraction = (positionMs.toFloat()/cache.durationMs.coerceAtLeast(1)).coerceIn(0f, 1f)
    GlassSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("整曲频谱", color = Moon, fontSize = 13.sp)
            }
            Box(Modifier.fillMaxWidth().height(88.dp).clip(SquircleShape(12.dp))
                .pointerInput(cache.durationMs) { detectTapGestures { onSeek((it.x/size.width).coerceIn(0f,1f).times(cache.durationMs).toLong()) } }
                .semantics { contentDescription = "整曲频谱进度"; progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                    setProgress { onSeek((it.coerceIn(0f,1f)*cache.durationMs).toLong()); true } }) {
                Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.Low)
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(Color(0xFF101B30).copy(alpha = .38f), size = androidx.compose.ui.geometry.Size(size.width*fraction, size.height))
                    drawLine(Moon, Offset(size.width*fraction, 0f), Offset(size.width*fraction,size.height), 2.dp.toPx())
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("0:00", color = Quiet, fontSize = 11.sp)
                Text("20 Hz — 20 kHz", color = Quiet, fontSize = 11.sp)
                Text("${cache.durationMs/60000}:${"%02d".format(Locale.ROOT,cache.durationMs/1000%60)}", color = Quiet, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun StereoField(cache: AnalysisCache) {
    val points = remember(cache.scatter) {
        val data = ByteBuffer.wrap(cache.scatter).order(ByteOrder.LITTLE_ENDIAN)
        buildList { while (data.remaining() >= 4) add(Offset(data.short/32767f, data.short/32767f)) }
    }
    AnalysisSection {
        Column(Modifier.padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("立体声声场", color = Moon, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Canvas(Modifier.size(104.dp).semantics { contentDescription = "离线李萨如声场统计图" }) {
                    val center = Offset(size.width/2,size.height/2); val radius = size.minDimension*.46f
                    drawCircle(Quiet.copy(alpha=.1f),radius,center,style=androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                    drawLine(Quiet.copy(alpha=.15f), Offset(center.x,0f), Offset(center.x,size.height), 1f)
                    drawLine(Quiet.copy(alpha=.15f), Offset(0f,center.y), Offset(size.width,center.y), 1f)
                    points.forEach { drawCircle(Mist.copy(alpha=.3f),1.4.dp.toPx(),Offset(center.x+it.x*radius,center.y-it.y*radius)) }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(when { cache.width == null -> "片段无有效信号"; cache.sourceChannels == 1 -> "单声道，声像居中"
                        cache.width < .25 -> "声像更集中"; cache.width < .65 -> "声像适度展开"; else -> "两侧分布较多" }, color = Mist, fontSize = 13.sp)
                    Text("展开程度  ${cache.width?.let { "${(it*100).roundToInt()}%" } ?: "—"}", color = Quiet, fontSize = 11.sp)
                    Text("声道相关  ${cache.correlation?.let { "%+.2f".format(Locale.ROOT,it) } ?: "—"}", color = Quiet, fontSize = 11.sp)
                    if ((cache.correlation ?: 1.0) < -.2) Text("反相成分较多，单声道可能抵消", color = Moon, fontSize = 10.sp)
                }
            }
            Text("代表片段的静态统计 · 非实时示波器", color = Quiet, fontSize = 11.sp)
        }
    }
}

@Composable
private fun AnalysisSection(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = Quiet.copy(alpha = .14f))
        content()
    }
}
