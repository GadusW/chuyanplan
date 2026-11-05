package com.stillune.app.ui

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.stillune.app.R
import com.stillune.app.data.Lyrics
import com.stillune.app.playback.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Glass is composed from a blurred ambient image behind a translucent, sharply drawn control layer. */
@Composable
internal fun MoonlitBackdrop(artwork: Bitmap?, modifier: Modifier = Modifier) {
    Box(modifier.background(Brush.linearGradient(listOf(Color(0xFF344B65), Night, Color(0xFF254C68))))) {
        val blur = if (Build.VERSION.SDK_INT >= 31) Modifier.blur(56.dp, BlurredEdgeTreatment.Unbounded) else Modifier
        Box(Modifier.matchParentSize().then(blur).alpha(.7f)) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Brush.radialGradient(listOf(Mist.copy(alpha = .55f), Color.Transparent),
                    center = Offset(size.width * 1.02f, size.height * .42f), radius = size.width * .85f),
                    size.width * .85f, Offset(size.width * 1.02f, size.height * .42f))
                drawCircle(Brush.radialGradient(listOf(Moon.copy(alpha = .36f), Color.Transparent),
                    center = Offset(-size.width * .2f, size.height * .07f), radius = size.width * .85f),
                    size.width * .85f, Offset(-size.width * .2f, size.height * .07f))
            }
            if (artwork != null && Build.VERSION.SDK_INT >= 31) Image(artwork.asImageBitmap(), null, Modifier.fillMaxSize().alpha(.16f), contentScale = ContentScale.Crop)
            else if (Build.VERSION.SDK_INT >= 31) Image(painterResource(R.drawable.stillune_foreground), null,
                Modifier.fillMaxSize().graphicsLayer(scaleX = 2.3f, scaleY = 2.3f).alpha(.18f), contentScale = ContentScale.Crop)
        }
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Night.copy(alpha = .02f), Night.copy(alpha = .25f)))))
    }
}

@Composable
internal fun GlassSurface(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.glassPanel(16.dp),
        content = content)
}

@Composable
internal fun PlayerPage(
    state: PlaybackState, model: LibraryViewModel, lyrics: Lyrics, lyricsVisible: Boolean,
    artwork: Bitmap?, fullScreen: Boolean, onBack: () -> Unit, onFullScreen: () -> Unit,
    onImportLyrics: () -> Unit, onImport: () -> Unit, onQueue: () -> Unit, onOutput: () -> Unit,
    onImmersion: () -> Unit = {},
    onKnowledge: () -> Unit = {},
    bottomInset: Dp = 0.dp,
) {
    val analysisMode by model.analysisMode.collectAsStateWithLifecycle()
    val analysis by model.analysis.collectAsStateWithLifecycle()
    var realtime by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val compact = maxHeight - bottomInset < 640.dp
        val horizontal = 24.dp
        Column(Modifier.fillMaxSize().padding(bottom = bottomInset).then(if (fullScreen) Modifier.displayCutoutPadding() else Modifier)
            .padding(horizontal = horizontal, vertical = if (fullScreen) 20.dp else 8.dp)) {
            Box(Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) { Icon(Icons.Rounded.KeyboardArrowDown, "收起播放页", tint = Quiet) }
                Column(Modifier.align(Alignment.Center).padding(horizontal = 96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("栖声 · Stillune", fontSize = 13.sp, color = Moon, fontWeight = FontWeight.Medium)
                    Text(state.track?.album ?: "本地聆听", color = Quiet, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                if (state.track != null) IconButton(onClick = onKnowledge) {
                    Icon(Icons.AutoMirrored.Rounded.MenuBook, "聆听知识", tint = Quiet)
                }
                IconButton(onClick = onFullScreen) {
                    Icon(if (fullScreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                        if (fullScreen) "退出全屏" else "全屏播放", tint = Quiet)
                }
                }
            }
            Row(Modifier.align(Alignment.CenterHorizontally).padding(vertical = 4.dp)) {
                listOf("播放模式" to false, "音频分析" to true).forEach { (label, enabled) ->
                    Column(Modifier.widthIn(min = 96.dp).heightIn(min = 48.dp)
                        .selectable(analysisMode == enabled, role = Role.Tab) { model.setAnalysisMode(enabled) },
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(label, Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            color = if (analysisMode == enabled) Moon else Quiet, fontSize = 13.sp)
                        Box(Modifier.width(22.dp).height(2.dp).background(if (analysisMode == enabled) Moon else Color.Transparent, CircleShape))
                    }
                }
                TextButton(onClick = onImmersion, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("体感沉浸", fontSize = 13.sp, color = Quiet)
                }
            }
            if (landscape) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        if (analysisMode) Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            if (!realtime) Box(Modifier.fillMaxWidth().height(if (lyricsVisible) 180.dp else 80.dp)) {
                                PlayerVisual(lyrics, lyricsVisible, artwork, state, model, onImportLyrics, Modifier.fillMaxSize())
                            }
                            AnalysisSwitcher(analysis, state, model.realtimeAnalysis, model::requestAnalysis, model::seek,realtime,{realtime=it})
                        }
                        else PlayerVisual(lyrics, lyricsVisible, artwork, state, model, onImportLyrics, Modifier.fillMaxSize(), showQueue = true)
                    }
                    Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
                        if (analysisMode && realtime) RealtimeTransport(state,model,onImportLyrics,onQueue,onOutput)
                        else {
                            TrackHeading(state, compact = true, analysisMode)
                            PlayerControls(state, model, compact = true, onImport)
                            PlayerActions(lyricsVisible, model::toggleLyrics, state, onImportLyrics, onQueue, onOutput)
                        }
                    }
                }
            } else {
                Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = if (compact) 6.dp else 16.dp), contentAlignment = Alignment.Center) {
                    if (analysisMode) Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (lyricsVisible) Box(Modifier.fillMaxWidth().height(200.dp)) {
                            PlayerVisual(lyrics, true, artwork, state, model, onImportLyrics, Modifier.fillMaxSize())
                        } else if (!realtime) Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Box(Modifier.size(64.dp)) { PlayerVisual(lyrics, false, artwork, state, model, onImportLyrics, Modifier.fillMaxSize()) }
                            Column {
                                Text(state.track?.album ?: "本地音乐", color = Quiet, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        AnalysisSwitcher(analysis, state, model.realtimeAnalysis, model::requestAnalysis, model::seek,realtime,{realtime=it})
                    } else PlayerVisual(lyrics, lyricsVisible, artwork, state, model, onImportLyrics, Modifier.fillMaxSize(), showQueue = true)
                }
                if (analysisMode && realtime) RealtimeTransport(state,model,onImportLyrics,onQueue,onOutput)
                else {
                    TrackHeading(state, compact, analysisMode)
                    PlayerControls(state, model, compact || analysisMode, onImport)
                    PlayerActions(lyricsVisible, model::toggleLyrics, state, onImportLyrics, onQueue, onOutput)
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun RealtimeTransport(state: PlaybackState, model: LibraryViewModel, onImportLyrics: () -> Unit,
                              onQueue: () -> Unit, onOutput: () -> Unit) {
    var menu by remember {mutableStateOf(false)}
    var drag by remember(state.track?.uri) {mutableStateOf<Float?>(null)}
    Column(Modifier.fillMaxWidth().padding(top=8.dp)) {
        HorizontalDivider(color=Quiet.copy(alpha=.15f))
        Row(Modifier.fillMaxWidth().padding(top=8.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(state.track?.title ?: "先选择一首音乐",Modifier.weight(1f),color=Moon,fontSize=13.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
            Text("${playerTime(state.positionMs)} / ${playerTime(state.durationMs)}",color=Quiet,fontSize=10.sp,modifier=Modifier.padding(start=8.dp))
        }
        Slider(value=drag ?: (state.positionMs.toFloat()/state.durationMs.coerceAtLeast(1)).coerceIn(0f,1f),
            onValueChange={drag=it},onValueChangeFinished={drag?.let {model.seek((it*state.durationMs).toLong())};drag=null},
            enabled=state.track!=null && state.durationMs>0,
            modifier=Modifier.fillMaxWidth().height(32.dp).semantics {contentDescription="实时分析播放进度"},
            thumb={Box(Modifier.size(9.dp).background(Moon,CircleShape))},
            track={slider ->SliderDefaults.Track(slider,Modifier.height(3.dp),
                colors=SliderDefaults.colors(activeTrackColor=Moon.copy(alpha=.8f),inactiveTrackColor=Quiet.copy(alpha=.22f)),
                thumbTrackGapSize=0.dp,drawStopIndicator=null)})
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=model::shuffle) {Icon(Icons.Rounded.Shuffle,"随机播放",tint=if (state.shuffle) Moon else Quiet)}
            IconButton(onClick=model::previous,enabled=state.track!=null) {Icon(Icons.Rounded.SkipPrevious,"上一首",tint=Moon)}
            FilledIconButton(onClick=model::toggle,enabled=state.track!=null) {
                Icon(if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,if (state.playing) "暂停" else "播放")
            }
            IconButton(onClick=model::next,enabled=state.track!=null) {Icon(Icons.Rounded.SkipNext,"下一首",tint=Moon)}
            IconButton(onClick=model::repeat) {Icon(if (state.repeat==RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                "切换循环模式",tint=if (state.repeat==RepeatMode.OFF) Quiet else Moon)}
            Box {
                IconButton(onClick={menu=true}) {Icon(Icons.Rounded.MoreVert,"实时分析播放选项",tint=Quiet)}
                DropdownMenu(menu,{menu=false}) {
                    DropdownMenuItem(text={Text("封面 / 歌词")},onClick={menu=false; model.toggleLyrics()})
                    DropdownMenuItem(text={Text("导入歌词")},onClick={menu=false;onImportLyrics()})
                    DropdownMenuItem(text={Text("播放队列")},onClick={menu=false;onQueue()})
                    DropdownMenuItem(text={Text("输出设备")},onClick={menu=false;onOutput()})
                }
            }
        }
    }
}

@Composable
private fun PlayerVisual(lyrics: Lyrics, visible: Boolean, artwork: Bitmap?, state: PlaybackState,
                         model: LibraryViewModel, onImportLyrics: () -> Unit, modifier: Modifier,
                         showQueue: Boolean = false) {
    AlbumDeck(state, artwork, lyrics, visible, model::loadArtwork, model::advanceAlbum,
        model::toggleLyrics, model::seek, onImportLyrics, modifier, showQueue)
}

@Composable
private fun TrackHeading(state: PlaybackState, compact: Boolean, analysisMode: Boolean = false) {
    if (analysisMode) {
        Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Text(state.track?.title ?: "让声音停泊", color = Moon, fontSize = 19.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(state.track?.let { "${it.artist} · ${it.format}" } ?: "从乐库选择音乐",
                Modifier.padding(top = 4.dp), color = Quiet, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        return
    }
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        AnimatedContent(state.track?.title ?: "让声音停泊", label = "track-title") { title ->
            Text(title, fontSize = if (compact) 22.sp else 27.sp, lineHeight = 34.sp, fontWeight = FontWeight.Medium,
                maxLines = 2, overflow = TextOverflow.Ellipsis, color = Color(0xFFF4F2EE))
        }
        Text(state.track?.artist ?: "从乐库选择音乐", Modifier.padding(top = 6.dp),
            color = Quiet, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        state.track?.let { track ->
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (track.lossless) Icons.Rounded.GraphicEq else Icons.Rounded.MusicNote, null, Modifier.size(13.dp), tint = Mist)
                Text(buildString {
                    append("  ${track.format}")
                    if (track.lossless) append(" · 无损音源")
                    if (track.sampleRate > 0) append(" · ${track.sampleRate / 1000f} kHz")
                }, color = Mist, fontSize = 11.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerControls(state: PlaybackState, model: LibraryViewModel, compact: Boolean, onImport: () -> Unit) {
    var dragging by remember(state.track?.uri) { mutableStateOf<Float?>(null) }
    val duration = state.durationMs.coerceAtLeast(1).toFloat()
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Column(Modifier.fillMaxWidth()) {
        Slider(value = dragging ?: state.positionMs.toFloat().coerceIn(0f, duration),
            onValueChange = { dragging = it }, onValueChangeFinished = { dragging?.let { model.seek(it.toLong()) }; dragging = null },
            valueRange = 0f..duration, enabled = state.durationMs > 0,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).semantics { contentDescription = "播放进度" },
            interactionSource = interaction,
            thumb = { Box(Modifier.size(10.dp).background(Moon, CircleShape)) },
            track = { slider -> SliderDefaults.Track(slider, Modifier.height(3.dp),
                colors = SliderDefaults.colors(activeTrackColor = Moon.copy(alpha = .8f), inactiveTrackColor = Quiet.copy(alpha = .22f)),
                thumbTrackGapSize = 0.dp, drawStopIndicator = null) })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(playerTime(dragging?.toLong() ?: state.positionMs), color = Quiet, fontSize = 11.sp)
            Text(if (state.durationMs > 0) "−${playerTime((state.durationMs - (dragging?.toLong() ?: state.positionMs)).coerceAtLeast(0))}" else "--:--", color = Quiet, fontSize = 11.sp)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = if (compact) 4.dp else 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            IconToggleButton(state.shuffle, { model.shuffle() }, enabled = state.track != null) {
                Icon(Icons.Rounded.Shuffle, "随机播放", Modifier.size(21.dp), tint = if (state.shuffle) Moon else Quiet)
            }
            IconButton(onClick = model::previous, enabled = state.track != null) { Icon(Icons.Rounded.SkipPrevious, "上一首", Modifier.size(32.dp)) }
            FilledIconButton(onClick = { if (state.track == null) onImport() else model.toggle() }, modifier = Modifier.size(if (compact) 60.dp else 68.dp).glassPanel(34.dp, 1.5f),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Transparent, contentColor = Moon)) {
                if (state.loading) CircularProgressIndicator(Modifier.size(26.dp), color = Moon, strokeWidth = 2.dp)
                else Icon(if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (state.playing) "暂停" else "播放", Modifier.size(34.dp))
            }
            IconButton(onClick = model::next, enabled = state.track != null) { Icon(Icons.Rounded.SkipNext, "下一首", Modifier.size(32.dp)) }
            IconButton(onClick = model::repeat, enabled = state.track != null) {
                Icon(if (state.repeat == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    "循环模式：${when (state.repeat) { RepeatMode.ALL -> "列表循环"; RepeatMode.ONE -> "单曲循环"; RepeatMode.OFF -> "顺序播放" }}",
                    Modifier.size(21.dp), tint = if (state.repeat == RepeatMode.OFF) Quiet else Moon)
            }
        }
    }
}

@Composable
private fun PlayerActions(lyricsVisible: Boolean, toggleLyrics: () -> Unit, state: PlaybackState,
                          onImportLyrics: () -> Unit, onQueue: () -> Unit, onOutput: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            IconToggleButton(lyricsVisible, { toggleLyrics() }) {
                Icon(Icons.Rounded.Lyrics, "显示歌词", tint = if (lyricsVisible) Moon else Quiet)
            }
            TextButton(onClick = onOutput, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 2.dp)) {
                Icon(Icons.Rounded.SpeakerGroup, null, Modifier.size(14.dp), tint = Quiet)
                Text("  ${state.output.substringBefore(" ·")}", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Quiet)
            }
            if (lyricsVisible) IconButton(onClick = onImportLyrics, enabled = state.track != null) {
                Icon(Icons.AutoMirrored.Rounded.NoteAdd, "导入歌词", tint = Quiet, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onQueue) { Icon(Icons.AutoMirrored.Rounded.QueueMusic, "播放队列", tint = Quiet) }
        }
    }
}

@Composable
internal fun LyricsPane(lyrics: Lyrics, positionMs: Long, hasTrack: Boolean, onSeek: (Long) -> Unit,
                        onImport: () -> Unit, modifier: Modifier = Modifier, onReturnToCover: (() -> Unit)? = null) {
    val list = rememberLazyListState()
    val current = lyrics.activeIndex(positionMs)
    // Do not interrupt a user's scroll. Following resumes on the next line change after the gesture.
    LaunchedEffect(current, lyrics) {
        if (!list.isScrollInProgress && current >= 0) list.animateScrollToItem((current - 1).coerceAtLeast(0))
    }
    val returnGesture = if (onReturnToCover != null) Modifier.clickable(onClickLabel = "返回封面", onClick = onReturnToCover) else Modifier
    if (lyrics.lines.isEmpty()) Column(modifier.then(returnGesture).padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.Lyrics, null, Modifier.size(32.dp), tint = Mist.copy(alpha = .65f))
        Spacer(Modifier.height(18.dp))
        Text(if (hasTrack) "暂无歌词" else "先选一首音乐", fontSize = 20.sp, color = Moon)
        Text(lyrics.message ?: "导入本地 LRC / TXT 歌词\n已授权文件夹中的同名歌词会自动读取", Modifier.padding(top = 12.dp),
            color = Quiet, fontSize = 12.sp, lineHeight = 21.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (hasTrack) TextButton(onClick = onImport, modifier = Modifier.padding(top = 14.dp)) { Text("选择歌词文件") }
    } else LazyColumn(modifier.then(returnGesture).clip(RoundedCornerShape(12.dp))
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(Brush.verticalGradient(0f to Color.Transparent, .08f to Color.White,
                .86f to Color.White, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
        }, state = list,
        contentPadding = PaddingValues(top = 36.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        itemsIndexed(lyrics.lines, key = { index, _ -> index }) { index, line ->
            val active = !lyrics.synced || index == current
            val color by animateColorAsState(if (active) Moon else Quiet, tween(240), label = "lyric-highlight")
            Text(line.text, Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                .then(if (onReturnToCover == null) Modifier.clickable(enabled = line.timeMs != null) { line.timeMs?.let(onSeek) }
                else Modifier.pointerInput(line.timeMs, onReturnToCover) {
                    detectTapGestures(onTap = {
                        if (onReturnToCover != null) onReturnToCover() else line.timeMs?.let(onSeek)
                    }, onDoubleTap = { line.timeMs?.let(onSeek) })
                })
                .padding(horizontal = 10.dp, vertical = 5.dp)
                .semantics {
                    selected = lyrics.synced && index == current
                    onClick(label = if (onReturnToCover != null) "返回封面" else "跳转到这句歌词") {
                        if (onReturnToCover != null) onReturnToCover() else line.timeMs?.let(onSeek)
                        true
                    }
                    if (line.timeMs != null) customActions = listOf(CustomAccessibilityAction("跳转到这句歌词") { onSeek(line.timeMs); true })
                },
                color = color, fontSize = 23.sp, lineHeight = 34.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium)
        }
    }
}

private fun playerTime(ms: Long) = String.format(java.util.Locale.ROOT, "%d:%02d", ms.coerceAtLeast(0) / 60000, ms.coerceAtLeast(0) / 1000 % 60)
