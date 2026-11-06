package com.stillune.app.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stillune.app.R
import com.stillune.app.data.Lyrics
import com.stillune.app.data.Track
import com.stillune.app.playback.PlaybackState
import com.stillune.app.playback.RepeatMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Retains the original queue: passing a card advances its cursor, never removes the music. */
@Composable
internal fun AlbumDeck(
    state: PlaybackState, artwork: Bitmap?, lyrics: Lyrics, lyricsVisible: Boolean,
    loadArtwork: suspend (Track) -> Bitmap?, onAdvance: () -> Unit, onLyrics: () -> Unit,
    onSeek: (Long) -> Unit, onImportLyrics: () -> Unit, modifier: Modifier = Modifier,
    showQueue: Boolean = true,
    onPrevious: () -> Unit = {},
) {
    val uri = state.track?.uri
    val remaining = if (state.shuffle) state.shuffleRemaining
        else if (state.index >= 0) state.queue.lastIndex - state.index else 0
    val remainingLabel = if (state.repeat == RepeatMode.OFF) "接下来 $remaining 首" else "本轮剩余 $remaining 首"
    val nextIndex = state.nextIndex?.takeUnless { it == state.index }
    val previousIndex = state.previousIndex
    val canAdvance = showQueue && nextIndex != null
    val canGoBack = showQueue && previousIndex != null
    val canTurn = state.track != null
    val coverIndexes = remember(state.queue, state.index, nextIndex, state.shuffle) {
        if (nextIndex == null) emptyList()
        else if (state.shuffle) listOf(nextIndex)
        else (nextIndex until state.queue.size).take(3).takeWhile { it != state.index }
    }
    var lastTrack by remember { mutableStateOf(uri) }
    var handoff by remember { mutableStateOf<Pair<Int, Float>?>(null) }
    val trackSlide = remember { Animatable(0f) }
    LaunchedEffect(uri) {
        val direction = if (lastTrack == uri || !showQueue) 0 else state.trackDirection
        lastTrack = uri
        val carriedProgress = handoff?.takeIf { it.first == state.index }?.second ?: 0f
        handoff = null
        trackSlide.snapTo(direction * (1f - carriedProgress))
        if (direction != 0) trackSlide.animateTo(0f, tween(240))
    }
    val flip by animateFloatAsState(
        if (lyricsVisible) 180f else 0f, tween(300), label = "album-flip")
    var drag by remember(uri) { mutableFloatStateOf(0f) }
    var fold by remember(uri) { mutableStateOf(Offset.Zero) }
    val showingPeel by remember(uri) { derivedStateOf { fold != Offset.Zero } }
    val backFacing by remember { derivedStateOf { flip >= 90f } }
    LaunchedEffect(backFacing) { if (backFacing) fold = Offset.Zero }
    var settling by remember(uri) { mutableStateOf(false) }
    var motion by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val advance by rememberUpdatedState(onAdvance)
    val goBack by rememberUpdatedState(onPrevious)
    val turnPage by rememberUpdatedState(onLyrics)
    val density = LocalDensity.current
    val corner = with(density) { 64.dp.toPx() }
    DisposableEffect(uri, lyricsVisible) { onDispose { motion?.cancel() } }

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth * (if (showQueue) .86f else .96f), maxHeight - (if (showQueue) 28.dp else 4.dp), 380.dp).coerceAtLeast(48.dp)
        val sidePx = with(density) { side.toPx() }
        fun forwardProgress() = (-drag / sidePx).coerceIn(0f, 1f)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().height(side + 2.dp).testTag("album-deck")
                .semantics {
                    contentDescription = "专辑卡片组"
                    stateDescription = remainingLabel
                    customActions = buildList {
                        if (canAdvance) add(CustomAccessibilityAction("下一首") { advance(); true })
                        if (canGoBack) add(CustomAccessibilityAction("上一首") { goBack(); true })
                    }
                }, contentAlignment = Alignment.Center) {
                if (showQueue && !lyricsVisible) {
                    // Backmost first. Only a bounded set of upcoming covers is ever decoded.
                    for (slot in coverIndexes.indices.reversed()) {
                        val depth = slot + 1
                        val track = state.queue[coverIndexes[slot]]
                        key(track.uri) {
                            val cover by rememberTrackArtwork(track, loadArtwork)
                            AlbumArtwork(cover, null, Modifier.size(side).graphicsLayer {
                                val progress = forwardProgress()
                                val distance = depth - progress
                                translationX = sidePx * (.57f + .16f * (distance - 1f)) * (1f - if (depth == 1) progress else 0f)
                                scaleX = 1f - .12f * distance; scaleY = scaleX
                                rotationY = -54f * distance.coerceAtMost(1.3f)
                                cameraDistance = 16f * density.density
                                alpha = (1f - .17f * distance).coerceIn(.25f, 1f)
                            }.shadow(10.dp, SquircleShape(12.dp)).clip(SquircleShape(12.dp)))
                        }
                    }
                    previousIndex?.let(state.queue::get)?.let { track ->
                        val cover by rememberTrackArtwork(track, loadArtwork)
                        AlbumArtwork(cover, null, Modifier.size(side).graphicsLayer {
                            val back = (drag / sidePx).coerceIn(0f, 1f)
                            translationX = -sidePx * .62f * (1f - back)
                            scaleX = .82f + .18f * back; scaleY = scaleX; rotationY = 58f * (1f - back)
                            cameraDistance = 16f * density.density
                            alpha = (.56f + .44f * back) * (1f - forwardProgress())
                        }.clip(SquircleShape(12.dp)))
                    }
                }
                Box(Modifier.size(side).graphicsLayer {
                    val arrival = trackSlide.value
                    translationX = drag + arrival * sidePx * if (arrival < 0f) .62f else .57f
                    scaleX = 1f - abs(arrival) * if (arrival < 0f) .18f else .12f
                    scaleY = scaleX
                    rotationZ = if (showQueue) (drag / sidePx) * 7f else 0f
                    rotationY = (if (flip <= 90f) flip else flip - 180f) - arrival * 54f
                    cameraDistance = 20f * density.density
                }.shadow(18.dp, SquircleShape(12.dp), ambientColor = Night, spotColor = Night)
                    .clip(SquircleShape(12.dp)).background(Panel).testTag("album-front")
                    .semantics {
                        contentDescription = "当前专辑卡片"
                        stateDescription = if (lyricsVisible) "歌词" else "揭角 ${(fold.getDistance() / (sidePx * 1.414f) * 100).toInt().coerceIn(0, 100)}%"
                    }) {
                    if (backFacing || showingPeel) {
                        AlbumLyricsBack(lyrics, state, backFacing, side < 176.dp, onLyrics, onSeek, onImportLyrics)
                    }
                    if (!backFacing) Box(Modifier.fillMaxSize()
                        .pointerInput(uri, nextIndex, previousIndex, canTurn, showQueue, sidePx) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                if (settling || !canTurn || flip > 0f) return@awaitEachGesture
                                motion?.cancel()
                                val peelCorner = minOf(corner, size.width * .45f, size.height * .45f)
                                val peel = down.position.x >= size.width - peelCorner && down.position.y >= size.height - peelCorner
                                var claimed = peel
                                var cancelled = false
                                val peelTrail = mutableListOf(Offset.Zero)
                                if (peel) { down.consume(); fold = Offset(-4f, -4f) }
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    if (change == null) { cancelled = true; break }
                                    if (!change.pressed) { if (claimed) change.consume(); break }
                                    val delta = change.position - down.position
                                    if (!claimed && change.isConsumed) { cancelled = true; break }
                                    if (!claimed && abs(delta.y) > viewConfiguration.touchSlop && abs(delta.y) > abs(delta.x)) {
                                        cancelled = true; break
                                    }
                                    if (!claimed && showQueue && abs(delta.x) > viewConfiguration.touchSlop) claimed = true
                                    if (claimed) {
                                        change.consume()
                                        if (peel) {
                                            // A long pull may pass the card edge to uncover readable lyrics;
                                            // the card frame itself never follows the finger.
                                            fold = Offset(delta.x.coerceIn(-size.width * 1.9f, -1f), delta.y.coerceIn(-size.height * 1.9f, -1f))
                                            peelTrail.add(fold)
                                            if (peelTrail.size > 80) peelTrail.removeAt(1)
                                        }
                                        else {
                                            val available = if (delta.x < 0) canAdvance else canGoBack
                                            drag = if (available) delta.x.coerceIn(-size.width.toFloat(), size.width.toFloat()) else delta.x * .22f
                                        }
                                    }
                                }
                                if (peel) {
                                    val opened = !cancelled && (peelTrail.size == 1 ||
                                        -fold.x / size.width >= .66f && -fold.y / size.height >= .58f)
                                    if (opened) turnPage()
                                    else {
                                        if (peelTrail.size == 1) peelTrail.add(fold)
                                        motion = scope.launch {
                                            animate(peelTrail.lastIndex.toFloat(), 0f, animationSpec = tween(300)) { value, _ ->
                                                val i = value.toInt().coerceIn(0, peelTrail.lastIndex)
                                                val next = (i + 1).coerceAtMost(peelTrail.lastIndex)
                                                fold = peelTrail[i] + (peelTrail[next] - peelTrail[i]) * (value - i)
                                            }
                                            fold = Offset.Zero
                                        }
                                    }
                                } else if (claimed) {
                                    val step = when {
                                        cancelled -> 0
                                        canAdvance && drag < -size.width * .24f -> 1
                                        canGoBack && drag > size.width * .24f -> -1
                                        else -> 0
                                    }
                                    settling = true
                                    motion = scope.launch {
                                        try {
                                            val target = if (step > 0) nextIndex else previousIndex
                                            if (step != 0 && target != null) {
                                                handoff = target to (abs(drag) / sidePx).coerceAtMost(1f)
                                                if (step > 0) advance() else goBack()
                                            }
                                            animate(drag, 0f, animationSpec = spring(dampingRatio = .82f, stiffness = 430f)) { value, _ -> drag = value }
                                        } finally { settling = false }
                                    }
                                }
                            }
                        }.drawWithContent {
                            if (fold.getDistance() < 1f) {
                                drawContent()
                            } else {
                                val geometry = AlbumFold.create(size.width, size.height, fold)
                                clipPath(geometry.front.path()) { this@drawWithContent.drawContent() }
                                // A straight crease cuts the front. Its reflected polygon is the turned-over paper.
                                val flap = geometry.flap.path()
                                drawPath(flap, Brush.linearGradient(listOf(Color(0xFFEBF4FA), Color(0xFFABC0D6), Color(0xFFF6F4EA)),
                                    start = geometry.tip, end = geometry.midpoint))
                                drawPath(flap, Color.White.copy(alpha = .58f), style = Stroke(.6.dp.toPx()))
                                geometry.crease.takeIf { it.size == 2 }?.let {
                                    drawLine(Night.copy(alpha = .2f), it[0], it[1], 3.dp.toPx())
                                    drawLine(Color.White.copy(alpha = .65f), it[0], it[1], .6.dp.toPx())
                                }
                            }
                        }) {
                        AlbumArtwork(artwork, "${state.track?.album ?: "本地音乐"}专辑封面", Modifier.fillMaxSize())
                        if (canTurn) Box(Modifier.align(Alignment.BottomEnd).size(48.dp).semantics {
                            contentDescription = "揭角查看歌词"
                            onClick(label = "查看歌词") { onLyrics(); true }
                        }.drawBehind {
                            val p = Path().apply { moveTo(size.width - 18.dp.toPx(), size.height); lineTo(size.width, size.height - 18.dp.toPx()); lineTo(size.width, size.height); close() }
                            drawPath(p, Color.White.copy(alpha = .7f))
                        })
                    }
                }
            }
            if (showQueue) Text(remainingLabel, Modifier.padding(top = 6.dp).testTag("album-remaining"), color = Quiet, fontSize = 11.sp)
        }
    }
}

@Composable
internal fun rememberTrackArtwork(track: Track?, loader: suspend (Track) -> Bitmap?): State<Bitmap?> =
    produceState<Bitmap?>(null, track?.uri, track?.modifiedAt, track?.sizeBytes) {
        value = null
        if (track != null) value = loader(track)
    }

@Composable
internal fun AlbumArtwork(bitmap: Bitmap?, description: String?, modifier: Modifier = Modifier) {
    if (bitmap != null) Image(bitmap.asImageBitmap(), description, modifier, contentScale = ContentScale.Crop)
    else Box(modifier.background(Brush.linearGradient(listOf(Panel, Night))), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.stillune_foreground), description, Modifier.fillMaxSize())
    }
}

@Composable
private fun AlbumLyricsBack(lyrics: Lyrics, state: PlaybackState, interactive: Boolean, compact: Boolean, onClose: () -> Unit,
                            onSeek: (Long) -> Unit, onImport: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF334F6B), Night))).padding(if (compact) 6.dp else 12.dp)) {
        if (interactive) {
            TextButton(onClick = onClose, modifier = Modifier.align(Alignment.End)) { Text("返回封面", fontSize = 12.sp) }
            LyricsPane(lyrics, state.positionMs, state.track != null, onSeek, onImport, Modifier.weight(1f).fillMaxWidth(), compact = compact)
        } else {
            val active = lyrics.activeIndex(state.positionMs).coerceAtLeast(0)
            val lines = lyrics.lines.drop(active).take(4)
            Spacer(Modifier.weight(1f))
            if (lines.isEmpty()) Text(if (state.track == null) "从乐库选择音乐" else "暂无歌词", color = Moon, fontSize = 20.sp)
            else lines.forEachIndexed { i, line ->
                Text(line.text, Modifier.padding(vertical = 8.dp), color = if (i == 0) Moon else Quiet,
                    fontSize = 20.sp, fontWeight = if (i == 0) FontWeight.Medium else FontWeight.Normal, maxLines = 2)
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

/** Half-plane clipping and reflection keep every point tied to one fold line. */
internal data class AlbumFold(val front: List<Offset>, val flap: List<Offset>, val crease: List<Offset>, val tip: Offset, val midpoint: Offset) {
    companion object {
        fun create(width: Float, height: Float, drag: Offset): AlbumFold {
            val corner = Offset(width, height)
            val tip = corner + drag
            val midpoint = (corner + tip) / 2f
            val normal = corner - tip
            val square = listOf(Offset.Zero, Offset(width, 0f), corner, Offset(0f, height))
            fun distance(p: Offset) = (p - midpoint).x * normal.x + (p - midpoint).y * normal.y
            val crease = mutableListOf<Offset>()
            fun clip(front: Boolean): List<Offset> = buildList {
                for (i in square.indices) {
                    val a = square[i]
                    val b = square[(i + 1) % square.size]
                    val da = distance(a)
                    val db = distance(b)
                    if (if (front) da <= 0 else da >= 0) {
                        add(a)
                    }
                    if ((da < 0 && db > 0) || (da > 0 && db < 0)) {
                        val hit = a + (b - a) * (da / (da - db))
                        add(hit)
                        if (front) crease.add(hit)
                    }
                }
            }
            val front = clip(true)
            val flap = clip(false).map { it - normal * (2f * distance(it) / (normal.x * normal.x + normal.y * normal.y).coerceAtLeast(.001f)) }
            return AlbumFold(front, flap, crease, tip, midpoint)
        }
    }
}

private fun List<Offset>.path() = Path().apply {
    firstOrNull()?.let { moveTo(it.x, it.y) }
    drop(1).forEach { lineTo(it.x, it.y) }; close()
}
