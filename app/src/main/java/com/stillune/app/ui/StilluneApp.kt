package com.stillune.app.ui

import android.content.Intent
import android.app.Activity
import android.content.ContextWrapper
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stillune.app.R
import com.stillune.app.data.*
import com.stillune.app.playback.*
import com.stillune.app.immersion.ImmersionStyle
import com.stillune.app.health.AppreciationLibrary
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StilluneApp(
    tracks: List<Track>, playback: PlaybackState, scan: ScanState, model: LibraryViewModel,
    onScan: (ScanOptions) -> Unit, onFolder: (ScanOptions) -> Unit, onImport: () -> Unit,
    onImportLyrics: () -> Unit = {},
    openPlayerRequest: Int = 0,
    openHealthRequest: Int = 0,
) {
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    var healthOpen by rememberSaveable { mutableStateOf(false) }
    var articleId by rememberSaveable { mutableStateOf<String?>(null) }
    val reading by model.reading.collectAsStateWithLifecycle()
    val hearingLive by model.hearing.live.collectAsStateWithLifecycle()
    LaunchedEffect(openPlayerRequest) { if (openPlayerRequest > 0) { playerOpen = true; healthOpen = false } }
    var fullScreen by rememberSaveable { mutableStateOf(false) }
    var immersive by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(openHealthRequest) { if (openHealthRequest > 0) { healthOpen = true; playerOpen = false; immersive = false; fullScreen = false } }
    var immersionChooser by rememberSaveable { mutableStateOf(false) }
    var immersionStyle by rememberSaveable { mutableStateOf(ImmersionStyle.PARALLAX.name) }
    val lyrics by model.lyrics.collectAsStateWithLifecycle()
    val artwork by model.artwork.collectAsStateWithLifecycle()
    val lyricsVisible by model.lyricsVisible.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { generateSequence(context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull() }
    DisposableEffect(fullScreen, playerOpen, immersive) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        if ((fullScreen || immersive) && playerOpen) {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else controller?.show(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    var importOpen by rememberSaveable { mutableStateOf(false) }
    var historyOpen by rememberSaveable { mutableStateOf(false) }
    val recentHistory by model.recentHistory.collectAsStateWithLifecycle()
    var queueOpen by rememberSaveable { mutableStateOf(false) }
    var outputOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf("歌曲") }
    var group by rememberSaveable { mutableStateOf<String?>(null) }
    var losslessOnly by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(scan.message, playerOpen, healthOpen) {
        // Scan summaries wait for the library so they cannot cover player transport buttons.
        if (!playerOpen && !healthOpen) scan.message?.let { snackbar.showSnackbar(it); model.clearMessage() }
    }
    LaunchedEffect(playback.error) { playback.error?.let { snackbar.showSnackbar(it); model.clearError() } }
    BackHandler(playerOpen || healthOpen || group != null) {
        when { immersive -> immersive = false; fullScreen -> fullScreen = false; healthOpen -> healthOpen = false; playerOpen -> playerOpen = false; else -> group = null }
    }
    val filtered = remember(tracks, query, group, tab, losslessOnly) {
        tracks.filter {
            (!losslessOnly || it.lossless) &&
                (query.isBlank() || listOf(it.title, it.artist, it.album, it.fileName).any { text -> text.contains(query, true) }) &&
                (group == null || LibraryGrouping.key(it, tab) == group)
        }
    }
    Scaffold(containerColor = Night, snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = if (fullScreen || immersive) WindowInsets(0, 0, 0, 0) else WindowInsets.safeDrawing,
        bottomBar = {
            if (!fullScreen && !immersive) Column(Modifier.navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedVisibility(!playerOpen && playback.track != null, enter = fadeIn() + expandVertically(), exit = shrinkVertically() + fadeOut()) {
                    MiniPlayer(playback, onOpen = { playerOpen = true; healthOpen = false }, onToggle = model::toggle, onNext = model::next)
                }
                FloatingNavigation(playerOpen, { playerOpen = false; healthOpen = false }, { playerOpen = true; healthOpen = false },
                    healthOpen = healthOpen, onHealth = { healthOpen = true; playerOpen = false })
            }
        }) { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding(),
            bottom = if (playerOpen || healthOpen) padding.calculateBottomPadding() else 0.dp)
            .then(if (immersive) Modifier else Modifier.padding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues()))
            .background(Brush.verticalGradient(listOf(Color(0xFF1D2F4B), Night, Night)))) {
            if (!playerOpen) MoonlitBackdrop(null, Modifier.matchParentSize())
            AnimatedContent(if (healthOpen) 2 else if (playerOpen) 1 else 0, transitionSpec = {
                (fadeIn(tween(240)) + slideInHorizontally(tween(280)) { it / 10 }) togetherWith fadeOut(tween(160))
            }, label = "library-player") { destination ->
                if (destination == 2) HealthPage(model, onRead = { articleId = it })
                else if (destination == 1 && immersive) ImmersivePlayer(playback, artwork, ImmersionStyle.valueOf(immersionStyle),
                    onStyle = { immersionStyle = it.name }, onToggle = model::toggle,
                    onPrevious = model::previous, onNext = model::next, onSeek = model::seek, onExit = { immersive = false })
                else if (destination == 1) PlayerPage(playback, model, lyrics, lyricsVisible, artwork, fullScreen,
                    onBack = { fullScreen = false; playerOpen = false }, onFullScreen = { fullScreen = !fullScreen },
                    onImportLyrics = onImportLyrics, onImport = { importOpen = true },
                    onQueue = { queueOpen = true }, onOutput = { outputOpen = true }, onImmersion = { immersionChooser = true },
                    onAboutTrack = { model.openReading(playback.track) })
                else LazyColumn(contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 12.dp,
                    bottom = padding.calculateBottomPadding() + 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("栖声", fontSize = 28.sp, fontWeight = FontWeight.Normal, letterSpacing = 2.sp)
                                Text("Stillune", color = Quiet, fontSize = 12.sp)
                            }
                            IconButton(onClick = { historyOpen = true }) { Icon(Icons.Rounded.History, "最近播放", tint = Quiet) }
                            TextButton(onClick = { importOpen = true }, enabled = !scan.running) {
                                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text("导入音乐")
                            }
                        }
                    }
                    item { LibraryHero(tracks.size, onClick = {
                        if (tracks.isEmpty()) importOpen = true else { model.play(tracks.first()); playerOpen = true }
                    }) }
                    item {
                        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                            placeholder = { Text("寻找一首想听的歌", fontSize = 14.sp) },
                            leadingIcon = { Icon(Icons.Rounded.Search, "搜索") },
                            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "清除搜索") } },
                            shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = Color.Transparent, unfocusedContainerColor = Color.White.copy(alpha = .04f)))
                    }
                    if (scan.running) item {
                        Surface(shape = RoundedCornerShape(16.dp), color = Panel) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text("正在寻找音乐 · 已检查 ${scan.processed} 项", Modifier.weight(1f).padding(start = 12.dp), fontSize = 12.sp, color = Quiet)
                                TextButton(onClick = model::cancelScan) { Text("取消") }
                            }
                        }
                    }
                    item {
                        LibraryTabs(tab) { tab = it; group = null }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (group != null) LibraryGrouping.label(group!!, tab) else "${filtered.size} 首本地珍藏", Modifier.weight(1f), color = Quiet,
                                fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (filtered.isNotEmpty() && (tab == "歌曲" || group != null)) {
                                TextButton(onClick = { model.play(filtered.first(), filtered) }) {
                                    Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp))
                                    Text("播放全部", fontSize = 12.sp)
                                }
                            }
                            FilterChip(losslessOnly, { losslessOnly = !losslessOnly }, label = { Text("无损", fontSize = 12.sp) })
                        }
                        if (group != null) TextButton(onClick = { group = null }) { Icon(Icons.Rounded.ChevronLeft, null); Text("返回${tab}列表") }
                    }
                    if (filtered.isEmpty()) item {
                        EmptyLibrary(tracks.isEmpty(), onImport = { importOpen = true })
                    } else if (tab != "歌曲" && group == null) {
                        val groups = filtered.groupBy { LibraryGrouping.key(it, tab) }
                        items(groups.entries.toList(), key = { "group:${it.key}" }, contentType = { "group" }) { entry ->
                            Surface(onClick = { group = entry.key }, color = Color.Transparent, shape = RoundedCornerShape(12.dp)) {
                                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(when (tab) { "专辑" -> Icons.Rounded.Album; "艺术家" -> Icons.Rounded.PersonOutline
                                        "格式" -> Icons.Rounded.AudioFile; else -> Icons.Rounded.FolderOpen }, null, tint = Mist)
                                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                                        Text(LibraryGrouping.label(entry.key, tab), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(LibraryGrouping.detail(entry.key, tab, entry.value.size), color = Quiet, fontSize = 12.sp,
                                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                    Icon(Icons.Rounded.ChevronRight, null, tint = Quiet)
                                }
                            }
                        }
                    } else {
                        items(filtered, key = { it.uri }, contentType = { "track" }) { track ->
                            TrackRow(track, playback.track?.uri == track.uri, playback.playing) { model.play(track, filtered) }
                        }
                    }
                }
            }
        }
    }
    hearingLive.notice?.let { notice ->
        if (reading == null) AlertDialog(onDismissRequest = model.hearing::dismissNotice, title = { Text(notice.title) },
            text = { Text(notice.message) },
            confirmButton = { TextButton(onClick = {
                model.pauseForRest(); model.hearing.dismissNotice()
                immersive = false; fullScreen = false; playerOpen = false; healthOpen = true
                model.openReading(notice.track)
            }) { Text("暂停并阅读") } }, dismissButton = { TextButton(onClick = model.hearing::dismissNotice) { Text("知道了") } })
    }
    reading?.let { TrackReadingDialog(it, model::findReviews, model::closeReading) }
    articleId?.let { id -> AppreciationLibrary.articles.firstOrNull { it.id == id }?.let { article ->
        AppreciationReader(article, onClose = { articleId = null })
    } }
    if (immersionChooser) ModalBottomSheet(onDismissRequest = { immersionChooser = false }, containerColor = Panel,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("体感沉浸", fontSize = 23.sp, color = Moon)
            Text("单击画面显示控制，闲置 10 秒后隐藏。连续轻点画面三下，或用系统返回键退出。", color = Quiet, fontSize = 13.sp)
            ImmersionStyle.entries.forEach { choice ->
                Surface(onClick = { immersionStyle = choice.name }, color = Color.Transparent, shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(immersionStyle == choice.name, null)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(choice.title, color = Moon, fontSize = 16.sp)
                            Text(when(choice) {
                                ImmersionStyle.PARALLAX -> "倾斜手机，让封面轻轻浮动"
                                ImmersionStyle.RIPPLES -> "真实播放能量与手部运动，化作柔和波纹"
                                ImmersionStyle.PARTICLES -> "封面视差与节奏粒子，随音乐舒展"
                            }, color = Quiet, fontSize = 12.sp, lineHeight = 19.sp)
                        }
                    }
                }
            }
            Text("波纹与粒子需 Android 的播放波形权限（系统称为“录音”）。仅读取本应用播放会话，不采集麦克风；未授权时保留倾斜效果。", color = Quiet, fontSize = 12.sp, lineHeight = 19.sp)
            Button(onClick = { immersionChooser = false; immersive = true }, modifier = Modifier.fillMaxWidth()) { Text("进入沉浸") }
        }
    }
    if (importOpen) ImportSheet(scan.running, onDismiss = { importOpen = false }, onScan = { importOpen = false; onScan(it) },
        onFolder = { importOpen = false; onFolder(it) }, onImport = { importOpen = false; onImport() })
    if (historyOpen) ModalBottomSheet(onDismissRequest = { historyOpen = false }, containerColor = Panel) {
        Text("最近播放", Modifier.padding(horizontal = 24.dp, vertical = 12.dp), fontSize = 23.sp, color = Moon)
        if (recentHistory.isEmpty()) Text("听过的音乐会留在这里", Modifier.padding(24.dp), color = Quiet)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp).padding(horizontal = 24.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
            items(recentHistory, key = { it.track.uri }) { entry ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .clickable { model.playHistory(entry); historyOpen = false; playerOpen = true }.padding(vertical = 14.dp)) {
                    Text(entry.track.title, color = Moon, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${entry.track.artist} · 听至 ${timeLabel(entry.positionMs)}", color = Quiet, fontSize = 12.sp)
                }
            }
        }
    }
    if (queueOpen) ModalBottomSheet(onDismissRequest = { queueOpen = false }, containerColor = Panel) {
        Text("此刻的音乐 · ${playback.queue.size} 首", Modifier.padding(horizontal = 24.dp, vertical = 12.dp), fontSize = 20.sp)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp).padding(horizontal = 24.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
            items(playback.queue, key = { it.uri }) { track ->
                TrackRow(track, track.uri == playback.track?.uri, playback.playing) { model.play(track, playback.queue) }
            }
        }
    }
    if (outputOpen) {
        val context = LocalContext.current
        ModalBottomSheet(onDismissRequest = { outputOpen = false }, containerColor = Panel) {
            Column(Modifier.padding(horizontal = 28.dp).padding(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(Icons.Rounded.SpeakerGroup, null, tint = Mist, modifier = Modifier.size(32.dp))
                Text("让声音，落在耳边", fontSize = 22.sp)
                Text(playback.output, color = Moon)
                Text("音频跟随手机系统选择的输出设备。连接蓝牙或有线耳机后即可播放；拔出耳机会暂停。", color = Quiet, fontSize = 14.sp)
                Text("无损文件按原格式解码；蓝牙传输编码与系统重采样由设备决定。", color = Quiet, fontSize = 12.sp)
                OutlinedButton(onClick = {
                    try { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) } catch (_: Exception) { }
                }) { Text("打开蓝牙设置") }
            }
        }
    }
}

@Composable
private fun LibraryHero(count: Int, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), color = Color.Transparent) {
        Box(Modifier.fillMaxWidth().height(96.dp)) {
            Image(painterResource(R.drawable.stillune_foreground), null,
                Modifier.size(88.dp).align(Alignment.CenterEnd), alpha = .85f)
            Column(Modifier.align(Alignment.CenterStart).padding(end = 84.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("把音乐，留在身边。", fontSize = 19.sp, fontWeight = FontWeight.Light, color = Moon)
                Text(if (count == 0) "导入第一首音乐" else "$count 首本地音乐 · 开始聆听", fontSize = 12.sp, color = Quiet)
            }
        }
    }
}

@Composable
private fun EmptyLibrary(empty: Boolean, onImport: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.LibraryMusic, null, Modifier.size(32.dp), tint = Mist.copy(alpha = .8f))
        Spacer(Modifier.height(14.dp))
        Text(if (empty) "音乐还在等你带来" else "暂时没有找到音乐", fontSize = 17.sp, color = Moon)
        Spacer(Modifier.height(8.dp))
        Text(if (empty) "扫描手机，或选一个珍藏音乐的文件夹" else "试试其他关键词，或关闭无损筛选", fontSize = 12.sp, color = Quiet)
        if (empty) TextButton(onClick = onImport) { Text("寻找本地音乐  →") }
    }
}

@Composable
private fun TrackRow(track: Track, selected: Boolean, playing: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
        .background(if (selected) Mist.copy(alpha = .09f) else Color.Transparent)
        .clickable(onClick = onClick).padding(vertical = 9.dp, horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = .035f)), contentAlignment = Alignment.Center) {
            Icon(if (selected && playing) Icons.Rounded.GraphicEq else Icons.Rounded.Album, null, tint = if (selected) Moon else Quiet, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
            Text(track.title, color = if (selected) Moon else MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(5.dp))
            Text("${track.artist} · ${track.album}", color = Quiet, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(track.format, color = if (track.lossless) Mist else Quiet, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp)); Text(timeLabel(track.durationMs), color = Quiet, fontSize = 11.sp)
        }
    }
}

@Composable
private fun MiniPlayer(state: PlaybackState, onOpen: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit) {
    Surface(onClick = onOpen, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp), color = Color(0xFF26364C)) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Album, null, Modifier.size(34.dp), tint = Moon)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(state.track?.title.orEmpty(), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(state.track?.artist.orEmpty(), color = Quiet, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onToggle) { Icon(if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (state.playing) "暂停" else "播放", tint = Moon) }
                IconButton(onClick = onNext) { Icon(Icons.Rounded.SkipNext, "下一首", tint = Quiet) }
            }
            LinearProgressIndicator(progress = { if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth().height(2.dp), color = Mist, trackColor = Color.Transparent)
        }
    }
}

@Composable
internal fun LibraryTabs(selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
        LibraryGrouping.dimensions.forEach { name ->
            TextButton(onClick = { onSelect(name) },
                modifier = Modifier.semantics { this.selected = selected == name; role = Role.Tab },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                Text(name, color = if (selected == name) Moon else Quiet, fontSize = 14.sp,
                    fontWeight = if (selected == name) FontWeight.Medium else FontWeight.Normal)
            }
        }
    }
}

@Composable
internal fun FloatingNavigation(playerOpen: Boolean, onLibrary: () -> Unit, onPlayer: () -> Unit,
    healthOpen: Boolean = false, onHealth: () -> Unit = {}) {
    Row(Modifier.padding(horizontal = 24.dp, vertical = 10.dp).widthIn(max = 400.dp).fillMaxWidth()
        .clip(RoundedCornerShape(32.dp)).background(Color(0xFF263950).copy(alpha = .82f))
        .border(1.dp, Color.White.copy(alpha = .13f), RoundedCornerShape(32.dp))
        .padding(6.dp).semantics { contentDescription = "悬浮导航" },
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        NavItem("乐库", Icons.Rounded.LibraryMusic, !playerOpen && !healthOpen, Modifier.weight(1f), onLibrary)
        NavItem("此刻", Icons.Rounded.NightsStay, playerOpen && !healthOpen, Modifier.weight(1f), onPlayer)
        NavItem("护耳", Icons.Rounded.Hearing, healthOpen, Modifier.weight(1f), onHealth)
    }
}

@Composable
private fun NavItem(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .90f else if (selected) 1.06f else 1f,
        spring(dampingRatio = .8f, stiffness = 550f), label = "nav-press")
    val lift by animateFloatAsState(if (selected) -1.5f else 0f, tween(180), label = "nav-lift")
    val fill by animateColorAsState(if (selected) Moon.copy(alpha = .12f) else Color.Transparent, tween(180), label = "nav-selection")
    Row(modifier.heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp)).background(fill)
        .selectable(selected, interactionSource = interactions, indication = ripple(), role = Role.Tab, onClick = onClick)
        .padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center) {
        Icon(icon, null, Modifier.size(22.dp).graphicsLayer {
            scaleX = scale; scaleY = scale; translationY = lift.dp.toPx()
        }, tint = if (selected) Moon else Quiet)
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 13.sp, maxLines = 1, softWrap = false, color = if (selected) Moon else Quiet)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportSheet(busy: Boolean, onDismiss: () -> Unit, onScan: (ScanOptions) -> Unit, onFolder: (ScanOptions) -> Unit, onImport: () -> Unit) {
    var skipShort by rememberSaveable { mutableStateOf(false) }
    var skipSmall by rememberSaveable { mutableStateOf(false) }
    val options = ScanOptions(if (skipShort) 60 else 0, if (skipSmall) 100 else 0)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Panel,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("让音乐，在这里栖息", Modifier.weight(1f), fontSize = 23.sp, color = Moon)
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "关闭导入", tint = Quiet) }
            }
            Text("只建立本地索引，不移动或修改原文件。", color = Quiet, fontSize = 12.sp)
            ImportAction(Icons.Rounded.Radar, "扫描手机音乐", "查找系统媒体库，并重新扫描已授权文件夹", !busy) { onScan(options) }
            ImportAction(Icons.Rounded.FolderOpen, "选择音乐文件夹", "递归扫描子文件夹，支持 SD 卡授权目录", !busy) { onFolder(options) }
            ImportAction(Icons.Rounded.AudioFile, "导入音频文件", "从系统文件选择器中选择一首或多首", !busy, onImport)
            HorizontalDivider(color = Quiet.copy(alpha = .12f))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("跳过不足 60 秒的音频", Modifier.weight(1f), fontSize = 13.sp, color = Quiet)
                Switch(skipShort, { skipShort = it })
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("跳过小于 100 KB 的文件", Modifier.weight(1f), fontSize = 13.sp, color = Quiet)
                Switch(skipSmall, { skipSmall = it })
            }
            Text("支持 MP3 · WAV · FLAC · AAC · ALAC 等格式\n系统未收录的音频，可通过文件夹扫描导入。", fontSize = 11.sp, color = Quiet, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun ImportAction(icon: ImageVector, title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, color = Night.copy(alpha = .5f), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Mist, modifier = Modifier.size(26.dp))
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(title, fontSize = 15.sp); Spacer(Modifier.height(5.dp))
                Text(subtitle, fontSize = 11.sp, color = Quiet, lineHeight = 16.sp)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = Quiet)
        }
    }
}

fun timeLabel(ms: Long): String = if (ms <= 0) "--:--" else String.format(Locale.ROOT, "%d:%02d", ms / 60000, (ms / 1000) % 60)
