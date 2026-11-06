package com.stillune.app.ui

import android.graphics.Bitmap
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.stillune.app.data.Lyrics
import com.stillune.app.playback.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow

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
    val stepRequests = remember { MutableSharedFlow<Int>(extraBufferCapacity = 1) }
    val onStep: (Int) -> Unit = { direction ->
        if (!analysisMode && (if (direction < 0) state.previousIndex else state.nextIndex) != state.index) {
            stepRequests.tryEmit(direction)
        } else if (direction < 0) model.previousTrack() else model.next()
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val compact = maxHeight - bottomInset < 640.dp
        val horizontal = 24.dp
        Column(Modifier.fillMaxSize().padding(bottom = bottomInset).then(if (fullScreen) Modifier.displayCutoutPadding() else Modifier)
            .padding(horizontal = horizontal, vertical = if (fullScreen) 20.dp else 8.dp)) {
            Box(Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) { Icon(Icons.Rounded.KeyboardArrowDown, "收起播放页", tint = InkMuted) }
                Column(Modifier.align(Alignment.Center).padding(horizontal = 96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("栖声 · Stillune", fontSize = 13.sp, color = InkAccent, fontWeight = FontWeight.Medium)
                    Text(state.track?.album ?: "本地聆听", color = InkMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                if (state.track != null) IconButton(onClick = onKnowledge) {
                    Icon(Icons.AutoMirrored.Rounded.MenuBook, "聆听知识", tint = InkMuted)
                }
                IconButton(onClick = onFullScreen) {
                    Icon(if (fullScreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                        if (fullScreen) "退出全屏" else "全屏播放", tint = InkMuted)
                }
                }
            }
            Row(Modifier.align(Alignment.CenterHorizontally)) {
                listOf("播放模式" to false, "音频分析" to true).forEach { (label, enabled) ->
                    Column(Modifier.widthIn(min = 96.dp).heightIn(min = 48.dp)
                        .selectable(analysisMode == enabled, role = Role.Tab) { model.setAnalysisMode(enabled) },
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(label, Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            color = if (analysisMode == enabled) InkAccent else InkMuted, fontSize = 13.sp)
                        Box(Modifier.width(22.dp).height(2.dp).background(if (analysisMode == enabled) InkAccent else Color.Transparent, CircleShape))
                    }
                }
                TextButton(onClick = onImmersion, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("体感沉浸", fontSize = 13.sp, color = InkMuted)
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
                        else PlayerVisual(lyrics, lyricsVisible, artwork, state, model, onImportLyrics, Modifier.fillMaxSize(), showQueue = true, stepRequests = stepRequests)
                    }
                    Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
                        if (analysisMode && realtime) RealtimeTransport(state,model,onImportLyrics,onQueue,onOutput)
                        else {
                            if (analysisMode) TrackHeading(state)
                            TransportPanel(state, model, true, lyricsVisible, onImport, onImportLyrics, onQueue, onOutput, onStep, dense = true)
                        }
                    }
                }
            } else {
                Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = if (compact) 2.dp else 6.dp), contentAlignment = Alignment.Center) {
                    if (analysisMode) Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (lyricsVisible) Box(Modifier.fillMaxWidth().height(200.dp)) {
                            PlayerVisual(lyrics, true, artwork, state, model, onImportLyrics, Modifier.fillMaxSize())
                        } else if (!realtime) Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Box(Modifier.size(64.dp)) { PlayerVisual(lyrics, false, artwork, state, model, onImportLyrics, Modifier.fillMaxSize()) }
                            Column {
                                Text(state.track?.album ?: "本地音乐", color = InkMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        AnalysisSwitcher(analysis, state, model.realtimeAnalysis, model::requestAnalysis, model::seek,realtime,{realtime=it})
                    } else PlayerVisual(lyrics, lyricsVisible, artwork, state, model, onImportLyrics, Modifier.fillMaxSize(), showQueue = true, stepRequests = stepRequests)
                }
                if (analysisMode && realtime) RealtimeTransport(state,model,onImportLyrics,onQueue,onOutput)
                else {
                    if (analysisMode) TrackHeading(state)
                    TransportPanel(state, model, compact || analysisMode, lyricsVisible, onImport, onImportLyrics, onQueue, onOutput, onStep)
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
        HorizontalDivider(color=InkMuted.copy(alpha=.15f))
        Row(Modifier.fillMaxWidth().padding(top=8.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(state.track?.title ?: "先选择一首音乐",Modifier.weight(1f),color=InkAccent,fontSize=13.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
            Text("${playerTime(state.positionMs)} / ${playerTime(state.durationMs)}",color=InkMuted,fontSize=10.sp,modifier=Modifier.padding(start=8.dp))
        }
        PlaybackSeekBar(drag ?: state.positionMs.toFloat().coerceIn(0f, state.durationMs.coerceAtLeast(1).toFloat()),
            state.durationMs, "实时分析播放进度", { drag = it },
            { drag?.let { model.seek(it.toLong()) }; drag = null })
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=model::shuffle) {Icon(Icons.Rounded.Shuffle,"随机播放",tint=if (state.shuffle) InkAccent else InkMuted)}
            IconButton(onClick=model::previousTrack,enabled=state.previousIndex!=null) {Icon(Icons.Rounded.SkipPrevious,"上一首",tint=InkAccent)}
            FilledIconButton(onClick=model::toggle,enabled=state.track!=null, shape=SquircleShape(14.dp)) {
                Icon(if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,if (state.playing) "暂停" else "播放")
            }
            IconButton(onClick=model::next,enabled=state.nextIndex!=null) {Icon(Icons.Rounded.SkipNext,"下一首",tint=InkAccent)}
            IconButton(onClick=model::repeat) {Icon(if (state.repeat==RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                "切换循环模式",tint=if (state.repeat==RepeatMode.OFF) InkMuted else InkAccent)}
            Box {
                IconButton(onClick={menu=true}) {Icon(Icons.Rounded.MoreVert,"实时分析播放选项",tint=InkMuted)}
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
                         showQueue: Boolean = false, stepRequests: Flow<Int> = emptyFlow()) {
    AlbumDeck(state, artwork, lyrics, visible, model::loadArtwork, model::next,
        model::toggleLyrics, model::seek, onImportLyrics, modifier, showQueue, onPrevious = model::previousTrack, stepRequests = stepRequests, cachedArtwork = model::cachedArtwork)
}

@Composable
private fun TrackHeading(state: PlaybackState) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Text(state.track?.title ?: "未播放", color = InkAccent, fontSize = 19.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(state.track?.artist ?: "从乐库选择音乐", Modifier.weight(1f), color = InkMuted, fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            state.track?.let { AudioFormatBadge(it.format) }
        }
    }
}

@Composable
private fun TransportPanel(state: PlaybackState, model: LibraryViewModel, compact: Boolean, lyricsVisible: Boolean,
                           onImport: () -> Unit, onImportLyrics: () -> Unit, onQueue: () -> Unit, onOutput: () -> Unit,
                           onStep: (Int) -> Unit, dense: Boolean = false) {
    Column(Modifier.fillMaxWidth().glassPanel(22.dp, .84f).padding(horizontal = 10.dp)) {
        PlayerControls(state, model, compact, onImport, onStep, inlineTime = dense)
        PlayerActions(lyricsVisible, model::toggleLyrics, state, onImportLyrics, onQueue, onOutput, dense)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerControls(state: PlaybackState, model: LibraryViewModel, compact: Boolean, onImport: () -> Unit,
                           onStep: (Int) -> Unit, inlineTime: Boolean) {
    var dragging by remember(state.track?.uri) { mutableStateOf<Float?>(null) }
    val duration = state.durationMs.coerceAtLeast(1).toFloat()
    val position = dragging?.toLong() ?: state.positionMs
    val elapsed = playerTime(position)
    val remaining = if (state.durationMs > 0) "−${playerTime((state.durationMs - position).coerceAtLeast(0))}" else "--:--"
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (inlineTime) Text(elapsed, color = InkMuted, fontSize = 11.sp)
            PlaybackSeekBar(dragging ?: state.positionMs.toFloat().coerceIn(0f, duration), state.durationMs,
                "播放进度", { dragging = it }, { dragging?.let { model.seek(it.toLong()) }; dragging = null },
                Modifier.weight(1f))
            if (inlineTime) Text(remaining, color = InkMuted, fontSize = 11.sp)
        }
        if (!inlineTime) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(elapsed, color = InkMuted, fontSize = 11.sp)
            Text(remaining, color = InkMuted, fontSize = 11.sp)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = if (compact) 1.dp else 5.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            IconToggleButton(state.shuffle, { model.shuffle() }, enabled = state.track != null) {
                Icon(Icons.Rounded.Shuffle, "随机播放", Modifier.size(21.dp), tint = if (state.shuffle) InkAccent else InkMuted)
            }
            IconButton(onClick = { onStep(-1) }, enabled = state.previousIndex != null) { Icon(Icons.Rounded.SkipPrevious, "上一首", Modifier.size(32.dp)) }
            FilledIconButton(onClick = { if (state.track == null) onImport() else model.toggle() }, modifier = Modifier.size(if (compact) 52.dp else 60.dp).glassPanel(20.dp, 1.5f),
                shape = SquircleShape(20.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Transparent, contentColor = InkAccent)) {
                if (state.loading) CircularProgressIndicator(Modifier.size(26.dp), color = InkAccent, strokeWidth = 2.dp)
                else Icon(if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (state.playing) "暂停" else "播放", Modifier.size(34.dp))
            }
            IconButton(onClick = { onStep(1) }, enabled = state.nextIndex != null) { Icon(Icons.Rounded.SkipNext, "下一首", Modifier.size(32.dp)) }
            IconButton(onClick = model::repeat, enabled = state.track != null) {
                Icon(if (state.repeat == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    "循环模式：${when (state.repeat) { RepeatMode.ALL -> "列表循环"; RepeatMode.ONE -> "单曲循环"; RepeatMode.OFF -> "顺序播放" }}",
                    Modifier.size(21.dp), tint = if (state.repeat == RepeatMode.OFF) InkMuted else InkAccent)
            }
        }
    }
}

@Composable
private fun PlayerActions(lyricsVisible: Boolean, toggleLyrics: () -> Unit, state: PlaybackState,
                          onImportLyrics: () -> Unit, onQueue: () -> Unit, onOutput: () -> Unit, dense: Boolean) {
    Box(Modifier.fillMaxWidth().padding(bottom = if (dense) 0.dp else 4.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = if (dense) 0.dp else 3.dp), verticalAlignment = Alignment.CenterVertically) {
            IconToggleButton(lyricsVisible, { toggleLyrics() }) {
                Icon(Icons.Rounded.Lyrics, "显示歌词", tint = if (lyricsVisible) InkAccent else InkMuted)
            }
            TextButton(onClick = onOutput, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 2.dp)) {
                Icon(Icons.Rounded.SpeakerGroup, null, Modifier.size(14.dp), tint = InkMuted)
                Text("  ${state.output.substringBefore(" ·")}", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = InkMuted)
            }
            if (lyricsVisible) IconButton(onClick = onImportLyrics, enabled = state.track != null) {
                Icon(Icons.AutoMirrored.Rounded.NoteAdd, "导入歌词", tint = InkMuted, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onQueue) { Icon(Icons.AutoMirrored.Rounded.QueueMusic, "播放队列", tint = InkMuted) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LyricsPane(lyrics: Lyrics, positionMs: Long, hasTrack: Boolean, onSeek: (Long) -> Unit,
                        onImport: () -> Unit, modifier: Modifier = Modifier, onReturnToCover: (() -> Unit)? = null,
                        compact: Boolean = false, cardBack: Boolean = false) {
    val list = rememberLazyListState()
    val current = lyrics.activeIndex(positionMs)
    // Do not interrupt a user's scroll. Following resumes on the next line change after the gesture.
    LaunchedEffect(current, lyrics) {
        if (!list.isScrollInProgress && current >= 0) list.animateScrollToItem((current - 1).coerceAtLeast(0))
    }
    val returnGesture = if (onReturnToCover != null) Modifier.clickable(onClickLabel = "返回封面", onClick = onReturnToCover) else Modifier
    if (lyrics.lines.isEmpty() && compact) Column(
        modifier.then(returnGesture).verticalScroll(rememberScrollState()).padding(horizontal = 8.dp)
    ) {
        Text(lyrics.message ?: if (hasTrack) "暂无歌词" else "未播放", color = InkAccent, fontSize = 13.sp)
        if (hasTrack) TextButton(onClick = onImport) { Text("导入歌词", fontSize = 12.sp) }
    } else if (lyrics.lines.isEmpty()) Column(modifier.then(returnGesture).padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.Lyrics, null, Modifier.size(32.dp), tint = InkBlue.copy(alpha = .65f))
        Spacer(Modifier.height(18.dp))
        Text(if (hasTrack) "暂无歌词" else "先选一首音乐", fontSize = 20.sp, color = InkAccent)
        Text(lyrics.message ?: "导入本地 LRC / TXT 歌词\n已授权文件夹中的同名歌词会自动读取", Modifier.padding(top = 12.dp),
            color = InkMuted, fontSize = 12.sp, lineHeight = 21.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (hasTrack) TextButton(onClick = onImport, modifier = Modifier.padding(top = 14.dp)) { Text("选择歌词文件") }
    } else LazyColumn(modifier.then(returnGesture).clip(SquircleShape(12.dp))
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(Brush.verticalGradient(0f to Color.Transparent, .08f to Color.White,
                .86f to Color.White, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
        }, state = list,
        contentPadding = PaddingValues(top = if (compact || cardBack) 2.dp else 36.dp, bottom = if (compact) 16.dp else 100.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact || cardBack) 8.dp else 22.dp)) {
        itemsIndexed(lyrics.lines, key = { index, _ -> index }) { index, line ->
            val active = !lyrics.synced || index == current
            val color by animateColorAsState(if (active) InkAccent else InkMuted, tween(240), label = "lyric-highlight")
            Text(line.text, Modifier.fillMaxWidth().clip(SquircleShape(8.dp))
                .then(if (cardBack && onReturnToCover != null) Modifier.combinedClickable(
                    onClickLabel = "返回封面", onClick = onReturnToCover,
                    onLongClickLabel = "跳转到这句歌词", onLongClick = line.timeMs?.let { time -> { onSeek(time) } }
                ) else if (onReturnToCover == null) Modifier.clickable(enabled = line.timeMs != null) { line.timeMs?.let(onSeek) }
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
                color = color, fontSize = if (compact) 16.sp else if (cardBack) 20.sp else 23.sp, lineHeight = if (compact) 22.sp else if (cardBack) 27.sp else 34.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium)
        }
    }
}

private fun playerTime(ms: Long) = String.format(java.util.Locale.ROOT, "%d:%02d", ms.coerceAtLeast(0) / 60000, ms.coerceAtLeast(0) / 1000 % 60)
