package com.stillune.app.ui

import android.content.Intent
import android.app.Activity
import android.content.ContextWrapper
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.Font
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
import java.util.Calendar
import java.util.Locale

private val StilluneWordmark = FontFamily(Font(R.font.parisienne_regular))

private class PageEntry(val destination: Int, val origin: Offset?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StilluneApp(
    tracks: List<Track>, playback: PlaybackState, scan: ScanState, model: LibraryViewModel,
    onScan: (ScanOptions) -> Unit, onFolder: (ScanOptions) -> Unit, onImport: () -> Unit,
    onImportLyrics: () -> Unit = {},
    onImportKnowledge: () -> Unit = {},
    openPlayerRequest: Int = 0,
    openHealthRequest: Int = 0,
) {
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    var healthOpen by rememberSaveable { mutableStateOf(false) }
    var navOrigin by remember { mutableStateOf<Offset?>(null) }
    var pageOrigin by remember { mutableStateOf(Offset.Zero) }
    var knowledgeOpen by rememberSaveable { mutableStateOf(false) }
    val knowledge by model.knowledge.state.collectAsStateWithLifecycle()
    val knowledgeImport by model.knowledgeImport.collectAsStateWithLifecycle()
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
    var guideOpen by rememberSaveable { mutableStateOf(false) }
    val recentHistory by model.recentHistory.collectAsStateWithLifecycle()
    var queueOpen by rememberSaveable { mutableStateOf(false) }
    var outputOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf("歌曲") }
    var group by rememberSaveable { mutableStateOf<String?>(null) }
    var losslessOnly by rememberSaveable { mutableStateOf(false) }
    val libraryScroll = rememberLazyListState()
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
    val groups = remember(filtered, tab, group) {
        if (tab != "歌曲" && group == null) filtered.groupBy { LibraryGrouping.key(it, tab) }.toList() else emptyList()
    }
    val miniPlayerInset = maxOf(72f, 32f * LocalDensity.current.fontScale + 24f).dp
    val albumColumns = ((LocalConfiguration.current.screenWidthDp - 48) / 152).coerceIn(2, 5)
    val destination = if (healthOpen) 2 else if (playerOpen) 1 else 0
    val entry = remember(destination) { PageEntry(destination, navOrigin) }
    GlassScene(artwork) {
    Scaffold(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground,
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = if (fullScreen || immersive) WindowInsets(0, 0, 0, 0) else WindowInsets.safeDrawing,
        bottomBar = {
            if (!fullScreen && !immersive) Column(Modifier.fillMaxWidth().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                FloatingNavigation(playerOpen, { playerOpen = false; healthOpen = false }, { playerOpen = true; healthOpen = false },
                    healthOpen = healthOpen, onHealth = { healthOpen = true; playerOpen = false },
                    onOrigin = { navOrigin = it })
            }
        }) { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())
            .then(if (immersive) Modifier else Modifier.padding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues()))
            .onGloballyPositioned { pageOrigin = it.positionInRoot() }) {
            LaunchedEffect(destination) { navOrigin = null }
            AnimatedContent(entry, transitionSpec = {
                val exit = if (targetState.origin == null) ExitTransition.None
                    else fadeOut(tween(340, easing = Easing { if (it < 1f) 0f else 1f }))
                (fadeIn(tween(1)) togetherWith exit).using(null).apply { targetContentZIndex = 1f }
            }, label = "library-player") { page ->
                val destination = page.destination
                val reveal by transition.animateFloat(transitionSpec = { tween(340, easing = FastOutSlowInEasing) }, label = "circular-reveal") {
                    if (it == EnterExitState.PreEnter && page.origin != null) 0f else 1f
                }
                Box(Modifier.fillMaxSize().pageReveal(page.origin, { pageOrigin }, { reveal })) {
                if (destination == 2) Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding() + if (playback.track != null) miniPlayerInset else 0.dp)) {
                    HealthPage(model, onRead = { knowledgeOpen = true })
                }
                else if (destination == 1 && immersive) ImmersivePlayer(playback, artwork, ImmersionStyle.valueOf(immersionStyle),
                    onStyle = { immersionStyle = it.name }, onToggle = model::toggle,
                    onPrevious = model::previous, onNext = model::next, onSeek = model::seek, onExit = { immersive = false })
                else if (destination == 1) PlayerPage(playback, model, lyrics, lyricsVisible, artwork, fullScreen,
                    onBack = { fullScreen = false; playerOpen = false }, onFullScreen = { fullScreen = !fullScreen },
                    onImportLyrics = onImportLyrics, onImport = { importOpen = true },
                    onQueue = { queueOpen = true }, onOutput = { outputOpen = true }, onImmersion = { immersionChooser = true },
                    onKnowledge = { knowledgeOpen = true },
                    bottomInset = if (fullScreen) 0.dp else padding.calculateBottomPadding())
                else {
                    val bottom = padding.calculateBottomPadding() + if (playback.track != null) miniPlayerInset else 0.dp
                    LazyColumn(state = libraryScroll, modifier = Modifier.fillMaxSize().progressiveLibraryBlur((bottom - if (playback.track != null) 6.dp else 10.dp).coerceAtLeast(0.dp)),
                        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 12.dp, bottom = bottom + 20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            BoxWithConstraints(Modifier.weight(1f)) {
                                val fontScale = LocalDensity.current.fontScale
                                val size = minOf(42f, maxWidth.value / (3.6f * fontScale))
                                Text("Stillune", color = InkAccent, fontSize = size.sp, fontFamily = StilluneWordmark,
                                    maxLines = 1, softWrap = false)
                            }
                            IconButton(onClick = { historyOpen = true }) { Icon(Icons.Rounded.History, "最近播放", tint = InkMuted) }
                            IconButton(onClick = { guideOpen = true }) { Icon(Icons.AutoMirrored.Rounded.HelpOutline, "使用指南", tint = InkMuted) }
                            IconButton(onClick = { importOpen = true }, enabled = !scan.running) {
                                Icon(Icons.Rounded.Add, "导入音乐")
                            }
                        }
                    }
                    item { LibraryHero(tracks.size, onClick = {
                        if (tracks.isEmpty()) importOpen = true else { model.play(tracks.first()); playerOpen = true }
                    }) }
                    item {
                        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().glassPanel(12.dp, .65f), singleLine = true,
                            placeholder = { Text("搜索歌曲、艺人或专辑", fontSize = 14.sp) },
                            leadingIcon = { Icon(Icons.Rounded.Search, "搜索") },
                            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "清除搜索") } },
                            shape = SquircleShape(12.dp), colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = Color.Transparent, unfocusedContainerColor = Color.Transparent))
                    }
                    if (scan.running) item {
                        Surface(shape = SquircleShape(16.dp), color = GlassSurfaceColor) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text("正在寻找音乐 · 已检查 ${scan.processed} 项", Modifier.weight(1f).padding(start = 12.dp), fontSize = 12.sp, color = InkMuted)
                                TextButton(onClick = model::cancelScan) { Text("取消") }
                            }
                        }
                    }
                    item {
                        LibraryTabs(tab) { tab = it; group = null }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(group?.let { LibraryGrouping.label(it, tab) } ?: "${filtered.size} 首歌曲", Modifier.weight(1f), color = InkMuted,
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
                    } else if (tab == "专辑" && group == null) {
                        items(groups.chunked(albumColumns), key = { "albums:${it.first().first}" }, contentType = { "albums" }) { albums ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                albums.forEach { (key, tracks) ->
                                    key(key) {
                                        LibraryAlbumCard(key, tracks, model::loadArtwork, Modifier.weight(1f)) { group = key }
                                    }
                                }
                                repeat(albumColumns - albums.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    } else if (tab != "歌曲" && group == null) {
                        items(groups, key = { "group:${it.first}" }, contentType = { "group" }) { (key, tracks) ->
                            Surface(onClick = { group = key }, color = Color.Transparent, shape = SquircleShape(12.dp)) {
                                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (tab == "格式") AudioFormatBadge(key, showLabel = false)
                                    else if (tab == "文件夹") Icon(Icons.Rounded.FolderOpen,
                                        null, tint = InkBlue)
                                    Column(Modifier.weight(1f).padding(start = if (tab == "艺术家") 0.dp else 14.dp, end = 14.dp)) {
                                        Text(LibraryGrouping.label(key, tab), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(LibraryGrouping.detail(key, tab, tracks.size), color = InkMuted, fontSize = 12.sp,
                                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                    Icon(Icons.Rounded.ChevronRight, null, tint = InkMuted)
                                }
                            }
                        }
                    } else {
                        items(filtered, key = { it.uri }, contentType = { "track" }) { track ->
                            TrackRow(track, playback.track?.uri == track.uri, playback.playing, model::loadArtwork) { model.play(track, filtered) }
                        }
                    }
                }
                }
                if (destination != 1 && playback.track != null) {
                    Box(Modifier.align(Alignment.BottomCenter).padding(bottom = padding.calculateBottomPadding())) {
                        MiniPlayer(playback, artwork, miniPlayerInset, onOpen = { playerOpen = true; healthOpen = false },
                            onToggle = model::toggle, onNext = model::next)
                    }
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
                model.openKnowledge()
            }) { Text("暂停并阅读") } }, dismissButton = { TextButton(onClick = model.hearing::dismissNotice) { Text("知道了") } })
    }
    if (knowledgeOpen) KnowledgeShelf(knowledge, knowledgeImport,
        onRead = model::openKnowledge, onImport = onImportKnowledge, onClose = { knowledgeOpen = false })
    reading?.let { article -> KnowledgeReader(article, article.id in knowledge.favorites,
        onFavorite = { model.toggleKnowledgeFavorite(article.id) }, onClose = model::closeReading,
        onFinished = { model.finishReading(article.id) }) }
    if (immersionChooser) ModalBottomSheet(onDismissRequest = { immersionChooser = false }, containerColor = GlassSurfaceColor,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("体感沉浸", fontSize = 23.sp, color = InkAccent)
            Text("单击画面显示控制，闲置 10 秒后隐藏。连续轻点画面三下，或用系统返回键退出。", color = InkMuted, fontSize = 13.sp)
            ImmersionStyle.entries.forEach { choice ->
                Surface(onClick = { immersionStyle = choice.name }, color = Color.Transparent, shape = SquircleShape(12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(immersionStyle == choice.name, null)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(choice.title, color = InkAccent, fontSize = 16.sp)
                            Text(when(choice) {
                                ImmersionStyle.PARALLAX -> "倾斜手机，让封面轻轻浮动"
                                ImmersionStyle.RIPPLES -> "真实播放能量与手部运动，化作柔和波纹"
                                ImmersionStyle.PARTICLES -> "封面视差与节奏粒子，随音乐舒展"
                            }, color = InkMuted, fontSize = 12.sp, lineHeight = 19.sp)
                        }
                    }
                }
            }
            Text("波纹与粒子需 Android 的播放波形权限（系统称为“录音”）。仅读取本应用播放会话，不采集麦克风；未授权时保留倾斜效果。", color = InkMuted, fontSize = 12.sp, lineHeight = 19.sp)
            Button(onClick = { immersionChooser = false; immersive = true }, modifier = Modifier.fillMaxWidth()) { Text("进入沉浸") }
        }
    }
    if (importOpen) ImportSheet(scan.running, onDismiss = { importOpen = false }, onScan = { importOpen = false; onScan(it) },
        onFolder = { importOpen = false; onFolder(it) }, onImport = { importOpen = false; onImport() })
    if (guideOpen) GettingStartedSheet(
        onClose = { guideOpen = false },
        onImport = { guideOpen = false; importOpen = true },
        onHealth = { guideOpen = false; healthOpen = true; playerOpen = false },
        onKnowledge = { guideOpen = false; knowledgeOpen = true })
    if (historyOpen) ModalBottomSheet(onDismissRequest = { historyOpen = false }, containerColor = GlassSurfaceColor) {
        Text("最近播放", Modifier.padding(horizontal = 24.dp, vertical = 12.dp), fontSize = 23.sp, color = InkAccent)
        if (recentHistory.isEmpty()) Text("听过的音乐会留在这里", Modifier.padding(24.dp), color = InkMuted)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp).padding(horizontal = 24.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
            items(recentHistory, key = { it.track.uri }) { entry ->
                Column(Modifier.fillMaxWidth().clip(SquircleShape(12.dp))
                    .clickable { model.playHistory(entry); historyOpen = false; playerOpen = true }.padding(vertical = 14.dp)) {
                    Text(entry.track.title, color = InkAccent, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${entry.track.artist} · 听至 ${timeLabel(entry.positionMs)}", color = InkMuted, fontSize = 12.sp)
                }
            }
        }
    }
    if (queueOpen) ModalBottomSheet(onDismissRequest = { queueOpen = false }, containerColor = GlassSurfaceColor) {
        Text("此刻的音乐 · ${playback.queue.size} 首", Modifier.padding(horizontal = 24.dp, vertical = 12.dp), fontSize = 20.sp)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp).padding(horizontal = 24.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
            items(playback.queue, key = { it.uri }) { track ->
                TrackRow(track, track.uri == playback.track?.uri, playback.playing, model::loadArtwork) { model.play(track, playback.queue) }
            }
        }
    }
    if (outputOpen) {
        val context = LocalContext.current
        ModalBottomSheet(onDismissRequest = { outputOpen = false }, containerColor = GlassSurfaceColor) {
            Column(Modifier.padding(horizontal = 28.dp).padding(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(Icons.Rounded.SpeakerGroup, null, tint = InkBlue, modifier = Modifier.size(32.dp))
                Text("输出设备", fontSize = 22.sp)
                Text(playback.output, color = InkAccent)
                Text("跟随系统输出，拔出耳机时暂停。", color = InkMuted, fontSize = 14.sp)
                OutlinedButton(onClick = {
                    try { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) } catch (_: Exception) { }
                }) { Text("打开蓝牙设置") }
            }
        }
    }
}

@Composable
private fun LibraryHero(count: Int, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = SquircleShape(12.dp), color = Color.Transparent) {
        Box(Modifier.fillMaxWidth().height(96.dp).glassPanel().padding(horizontal = 16.dp)) {
            Image(painterResource(R.drawable.stillune_foreground), null,
                Modifier.size(88.dp).align(Alignment.CenterEnd), alpha = .85f)
            Column(Modifier.align(Alignment.CenterStart).padding(end = 84.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (count == 0) "你的音乐" else "开始聆听", fontSize = 20.sp, fontWeight = FontWeight.Medium, color = InkAccent)
                if (count == 0) Text("导入音乐", fontSize = 12.sp, color = InkAccent)
            }
        }
    }
}

@Composable
private fun EmptyLibrary(empty: Boolean, onImport: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.LibraryMusic, null, Modifier.size(32.dp), tint = InkBlue.copy(alpha = .8f))
        Spacer(Modifier.height(14.dp))
        Text(if (empty) "还没有音乐" else "没有找到音乐", fontSize = 17.sp, color = InkAccent)
        Spacer(Modifier.height(8.dp))
        Text(if (empty) "扫描手机，或选一个珍藏音乐的文件夹" else "试试其他关键词，或关闭无损筛选", fontSize = 12.sp, color = InkMuted)
        if (empty) TextButton(onClick = onImport) { Text("导入音乐") }
    }
}

@Composable
private fun LibraryAlbumCard(key: String, tracks: List<Track>, loadArtwork: suspend (Track) -> android.graphics.Bitmap?,
                            modifier: Modifier, onClick: () -> Unit) {
    val cover by produceState<android.graphics.Bitmap?>(null, tracks) {
        value = null
        for (track in tracks) {
            val image = loadArtwork(track)
            if (image != null) {
                value = image
                break
            }
        }
    }
    Column(modifier.clip(SquircleShape(16.dp)).background(Color.White.copy(alpha = .42f))
        .clickable(onClick = onClick).padding(8.dp)) {
        AlbumArtwork(cover, null, Modifier.fillMaxWidth().aspectRatio(1f).clip(SquircleShape(12.dp)))
        Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp)) {
            Text(LibraryGrouping.label(key, "专辑"), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(LibraryGrouping.detail(key, "专辑", tracks.size), color = InkMuted, fontSize = 11.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TrackRow(track: Track, selected: Boolean, playing: Boolean, loadArtwork: suspend (Track) -> android.graphics.Bitmap?, onClick: () -> Unit) {
    val cover by rememberTrackArtwork(track, loadArtwork)
    val detailColor = if (selected) MaterialTheme.colorScheme.onSurface else InkMuted
    Row(Modifier.fillMaxWidth().clip(SquircleShape(14.dp))
        .background(if (selected) InkBlue.copy(alpha = .09f) else Color.Transparent)
        .clickable(onClick = onClick).padding(vertical = 9.dp, horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(SquircleShape(8.dp))
            .background(Color.White.copy(alpha = .035f)), contentAlignment = Alignment.Center) {
            AlbumArtwork(cover, null, Modifier.fillMaxSize())
            if (selected && playing) Icon(Icons.Rounded.GraphicEq, null, Modifier.align(Alignment.BottomEnd).size(18.dp).background(GlassGround.copy(alpha = .7f), SquircleShape(5.dp)), tint = InkAccent)
        }
        Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
            Text(track.title, color = if (selected) InkAccent else MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(5.dp))
            Text("${track.artist} · ${track.album}", color = detailColor, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            AudioFormatBadge(track.format)
            Spacer(Modifier.height(6.dp)); Text(timeLabel(track.durationMs), color = detailColor, fontSize = 11.sp)
        }
    }
}

@Composable
private fun MiniPlayer(state: PlaybackState, artwork: android.graphics.Bitmap?, miniPlayerInset: androidx.compose.ui.unit.Dp, onOpen: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit) {
    Surface(onClick = onOpen, modifier = Modifier.height(miniPlayerInset).padding(horizontal = 16.dp, vertical = 6.dp),
        shape = SquircleShape(20.dp), color = Color.Transparent) {
        Column(Modifier.glassPanel()) {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                AlbumArtwork(artwork, "当前歌曲专辑封面", Modifier.size(44.dp).clip(SquircleShape(8.dp)))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(state.track?.title.orEmpty(), fontSize = 14.sp, lineHeight = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(state.track?.artist.orEmpty(), color = InkMuted, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onToggle) { Icon(if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (state.playing) "暂停" else "播放", tint = InkAccent) }
                IconButton(onClick = onNext, enabled = state.nextIndex != null) { Icon(Icons.Rounded.SkipNext, "下一首", tint = InkMuted) }
            }
            LinearProgressIndicator(progress = { if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth().height(2.dp), color = InkBlue, trackColor = Color.Transparent)
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
                Text(name, color = if (selected == name) InkAccent else InkMuted, fontSize = 14.sp,
                    fontWeight = if (selected == name) FontWeight.Medium else FontWeight.Normal)
            }
        }
    }
}

@Composable
internal fun FloatingNavigation(playerOpen: Boolean, onLibrary: () -> Unit, onPlayer: () -> Unit,
    healthOpen: Boolean = false, onHealth: () -> Unit = {}, onOrigin: (Offset) -> Unit = {}) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        .selectableGroup().semantics { contentDescription = "悬浮导航" },
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        NavItem("乐库", Icons.Rounded.LibraryMusic, !playerOpen && !healthOpen, onOrigin, onLibrary)
        NavItem("此刻", Icons.Rounded.NightsStay, playerOpen && !healthOpen, onOrigin, onPlayer)
        NavItem("护耳", Icons.Rounded.Hearing, healthOpen, onOrigin, onHealth)
    }
}

@Composable
private fun NavItem(label: String, icon: ImageVector, selected: Boolean, onOrigin: (Offset) -> Unit, onClick: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .90f else if (selected) 1.06f else 1f,
        spring(dampingRatio = .8f, stiffness = 550f), label = "nav-press")
    val fill by animateColorAsState(if (selected) Color.White.copy(alpha = .72f) else Color.Transparent, tween(180), label = "nav-selection")
    val origin = remember { arrayOf(Offset.Zero) }
    Row(Modifier.heightIn(min = 52.dp).widthIn(min = 52.dp).clip(SquircleShape(26.dp))
        .background(fill)
        .semantics { contentDescription = label }
        .selectable(selected, interactionSource = interactions, indication = ripple(), role = Role.Tab,
            onClick = { if (!selected) { onOrigin(origin[0]); onClick() } })
        .padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center) {
        Icon(icon, null, Modifier.size(22.dp).onGloballyPositioned {
            origin[0] = it.localToRoot(Offset(it.size.width / 2f, it.size.height / 2f))
        }.graphicsLayer { scaleX = scale; scaleY = scale }, tint = if (selected) InkAccent else InkMuted)
        AnimatedVisibility(selected, enter = expandHorizontally(spring(dampingRatio = .78f, stiffness = 380f)) + fadeIn(),
            exit = shrinkHorizontally(tween(110)) + fadeOut(tween(90))) {
            Text(label, Modifier.padding(start = 8.dp), fontSize = 14.sp, maxLines = 1, softWrap = false, color = InkAccent)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportSheet(busy: Boolean, onDismiss: () -> Unit, onScan: (ScanOptions) -> Unit, onFolder: (ScanOptions) -> Unit, onImport: () -> Unit) {
    var skipShort by rememberSaveable { mutableStateOf(false) }
    var skipSmall by rememberSaveable { mutableStateOf(false) }
    val options = ScanOptions(if (skipShort) 60 else 0, if (skipSmall) 100 else 0)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = GlassSurfaceColor,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("导入音乐", Modifier.weight(1f), fontSize = 23.sp, color = InkAccent)
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "关闭导入", tint = InkMuted) }
            }
            ImportAction(Icons.Rounded.Radar, "扫描手机音乐", "查找系统媒体库，并重新扫描已授权文件夹", !busy) { onScan(options) }
            ImportAction(Icons.Rounded.FolderOpen, "选择音乐文件夹", "递归扫描子文件夹，支持 SD 卡授权目录", !busy) { onFolder(options) }
            ImportAction(Icons.Rounded.AudioFile, "导入音频文件", "从系统文件选择器中选择一首或多首", !busy, onImport)
            HorizontalDivider(color = InkMuted.copy(alpha = .12f))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("跳过不足 60 秒的音频", Modifier.weight(1f), fontSize = 13.sp, color = InkMuted)
                Switch(skipShort, { skipShort = it })
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("跳过小于 100 KB 的文件", Modifier.weight(1f), fontSize = 13.sp, color = InkMuted)
                Switch(skipSmall, { skipSmall = it })
            }
            Text("支持 MP3 · WAV · FLAC · AAC · ALAC 等格式\n系统未收录的音频，可通过文件夹扫描导入。", fontSize = 11.sp, color = InkMuted, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun ImportAction(icon: ImageVector, title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, color = Color.Transparent, shape = SquircleShape(18.dp)) {
        Row(Modifier.fillMaxWidth().glassPanel(18.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = InkBlue, modifier = Modifier.size(26.dp))
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(title, fontSize = 15.sp); Spacer(Modifier.height(5.dp))
                Text(subtitle, fontSize = 11.sp, color = InkMuted, lineHeight = 16.sp)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = InkMuted)
        }
    }
}

fun timeLabel(ms: Long): String = if (ms <= 0) "--:--" else String.format(Locale.ROOT, "%d:%02d", ms / 60000, (ms / 1000) % 60)
