package com.stillune.app.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import com.stillune.app.R
import com.stillune.app.data.Lyrics
import com.stillune.app.data.Track
import com.stillune.app.playback.PlaybackState
import com.stillune.app.playback.RepeatMode
import com.stillune.app.playback.QueuePolicy
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.roundToInt

/** Retains the original queue: passing a card advances its cursor, never removes the music. */
@Composable
internal fun AlbumDeck(
    state: PlaybackState, artwork: Bitmap?, lyrics: Lyrics, lyricsVisible: Boolean,
    loadArtwork: suspend (Track) -> Bitmap?, onAdvance: () -> Unit, onLyrics: () -> Unit,
    onSeek: (Long) -> Unit, onImportLyrics: () -> Unit, modifier: Modifier = Modifier,
    showQueue: Boolean = true,
    onPrevious: () -> Unit = {},
    stepRequests: Flow<Int> = emptyFlow(),
    cachedArtwork: (Track) -> Bitmap? = { null },
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
    val flip by animateFloatAsState(
        if (lyricsVisible) 180f else 0f, tween(300), label = "album-flip")
    var drag by remember(uri, state.index) { mutableFloatStateOf(0f) }
    val direction by remember(uri, state.index) { derivedStateOf { -drag.sign.toInt() } }
    val backFacing by remember { derivedStateOf { flip >= 90f } }
    var settling by remember(uri, state.index) { mutableStateOf(false) }
    var motion by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val advance by rememberUpdatedState(onAdvance)
    val goBack by rememberUpdatedState(onPrevious)
    val turnPage by rememberUpdatedState(onLyrics)
    val density = LocalDensity.current
    DisposableEffect(uri, state.index) { onDispose { motion?.cancel() } }

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth * (if (showQueue) .80f else .96f), maxHeight - (if (showQueue) 28.dp else 4.dp), 380.dp).coerceAtLeast(48.dp)
        val sidePx = with(density) { side.toPx() }
        val pitch = sidePx + with(density) { 10.dp.toPx() }
        val showPreview = showQueue && !state.shuffle && state.queue.size > 2
        val slots = remember(state.index, state.queue.size, nextIndex, previousIndex, state.repeat, showQueue, showPreview, direction) {
            buildMap {
                put(state.index, 0)
                if (showQueue) {
                    nextIndex?.let { next ->
                        put(next, 1)
                        if (showPreview) QueuePolicy.next(next, state.queue.size, state.repeat, false)
                            ?.takeUnless { it == next || it == state.index }?.let { put(it, 2) }
                    }
                }
                if (showQueue) previousIndex?.let { previous ->
                    if (direction < 0 || previous !in this) put(previous, -1)
                }
            }
        }
        fun settle(step: Int) {
            if (settling) return
            val target = when {
                step > 0 -> nextIndex
                step < 0 -> previousIndex
                else -> null
            }
            val targetUri = target?.let(state.queue::getOrNull)?.uri
            if (step != 0 && targetUri == null) return
            settling = true
            motion = scope.launch {
                try {
                    if (targetUri != null) {
                        if (lyricsVisible) turnPage()
                        snapshotFlow { flip }.first { it == 0f }
                        val finish = -step * sidePx
                        val distance = (abs(finish - drag) / sidePx).coerceIn(0f, 1f)
                        animate(drag, finish, animationSpec = tween((distance * 300f).toInt())) { value, _ -> drag = value }
                        if (step > 0) advance() else goBack()
                        // Restore the card if the service has not acknowledged the step.
                        delay(350)
                    }
                    animate(drag, 0f, animationSpec = spring(dampingRatio = .82f, stiffness = 430f)) { value, _ -> drag = value }
                } finally {
                    settling = false
                }
            }
        }
        // Bind the collector to this rail. A retained callback can animate the
        // previous track's offset even though the transport command still works.
        LaunchedEffect(stepRequests, showQueue, uri, state.index, nextIndex, previousIndex, sidePx, lyricsVisible) {
            if (showQueue) stepRequests.collect { settle(it) }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().height(side + 2.dp)
                .drawWithContent {
                    if (!showQueue) drawContent()
                    else {
                        val edge = (size.width - sidePx) / 2f
                        val peek = if (showPreview) minOf(edge, 36.dp.toPx()) else 3.dp.toPx()
                        clipRect(left = edge - 3.dp.toPx(), top = -20.dp.toPx(),
                            right = size.width - edge + peek, bottom = size.height + 20.dp.toPx()) {
                            this@drawWithContent.drawContent()
                        }
                    }
                }.testTag("album-deck")
                .semantics {
                    contentDescription = "专辑卡片组"
                    stateDescription = remainingLabel
                    customActions = buildList {
                        if (canAdvance) add(CustomAccessibilityAction("下一首") { settle(1); true })
                        if (canGoBack) add(CustomAccessibilityAction("上一首") { settle(-1); true })
                    }
                }, contentAlignment = Alignment.Center) {
                val input = if (backFacing) Modifier else Modifier
                    .pointerInput(uri, state.index, nextIndex, previousIndex, canTurn, showQueue, sidePx, lyricsVisible) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (settling || !canTurn || flip > 0f) return@awaitEachGesture
                            var dragging = false
                            var cancelled = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null) { cancelled = true; break }
                                if (!change.pressed) { if (dragging) change.consume(); break }
                                val delta = change.position - down.position
                                if (!dragging) {
                                    if (change.isConsumed || abs(delta.y) > viewConfiguration.touchSlop && abs(delta.y) > abs(delta.x)) {
                                        cancelled = true
                                        break
                                    }
                                    if (abs(delta.x) > viewConfiguration.touchSlop) {
                                        if (!showQueue) { cancelled = true; break }
                                        dragging = true
                                    }
                                }
                                if (dragging) {
                                    change.consume()
                                    val available = if (delta.x < 0) canAdvance else canGoBack
                                    drag = if (available) delta.x.coerceIn(-sidePx, sidePx) else delta.x * .22f
                                }
                            }
                            if (dragging) settle(when {
                                cancelled -> 0
                                canAdvance && drag < -sidePx * .24f -> 1
                                canGoBack && drag > sidePx * .24f -> -1
                                else -> 0
                            })
                            else if (!cancelled) turnPage()
                        }
                    }
                // All cards move by the same distance. Queue acknowledgment only changes
                // the origin: the incoming keyed card is already centered at that point.
                for ((index, slot) in slots) {
                    val track = state.queue.getOrNull(index)
                    key(index, track?.uri) {
                        val current = index == state.index
                        val cover = if (showQueue) rememberTrackArtwork(track, loadArtwork, cachedArtwork).value else artwork
                        val cardShape = SquircleShape(12.dp)
                        Box(Modifier.size(side)
                            .absoluteOffset { IntOffset((slot * pitch + (drag / sidePx) * pitch).roundToInt(), 0) }
                            .graphicsLayer {
                                rotationY = if (current) (if (flip <= 90f) flip else flip - 180f) else 0f
                                cameraDistance = 20f * density.density
                                alpha = if (!current && lyricsVisible) 0f else 1f
                                shadowElevation = 10.dp.toPx()
                                ambientShadowColor = InkMuted.copy(alpha = .36f)
                                spotShadowColor = InkAccent.copy(alpha = .26f)
                                shape = cardShape
                                clip = true
                            }.background(GlassSurfaceColor).testTag("album-card-${track?.uri.orEmpty()}")) {
                            Box(Modifier.fillMaxSize().then(if (current) Modifier.testTag("album-front").semantics {
                                contentDescription = "当前专辑卡片"
                                if (canTurn || lyricsVisible) onClick(label = if (lyricsVisible) "返回封面" else "查看歌词") {
                                    onLyrics()
                                    true
                                }
                                stateDescription = if (lyricsVisible) "歌词" else "封面"
                            } else Modifier)) {
                                if (current && backFacing) {
                                    AlbumLyricsBack(lyrics, state, side < 176.dp, onLyrics, onSeek, onImportLyrics)
                                } else {
                                    val description = if (current) "${track?.album ?: "本地音乐"}专辑封面" else null
                                    if (showQueue && cover == null && track != null) MissingDeckCover(track, description)
                                    else AlbumArtwork(cover, description, Modifier.fillMaxSize())
                                }
                            }
                        }
                    }
                }
                if (!backFacing) Box(Modifier.size(side).zIndex(6f).then(input))

            }
            if (showQueue) Text(remainingLabel, Modifier.padding(top = 6.dp).testTag("album-remaining"), color = InkMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun MissingDeckCover(track: Track, description: String?) {
    Column(Modifier.fillMaxSize().background(GlassSurfaceColor).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Image(painterResource(R.drawable.stillune_foreground), description,
            Modifier.fillMaxWidth(.68f).fillMaxHeight(.57f))
        Text(track.title, color = InkAccent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
internal fun rememberTrackArtwork(
    track: Track?, loader: suspend (Track) -> Bitmap?, cached: (Track) -> Bitmap? = { null },
): State<Bitmap?> = key(track?.uri, track?.modifiedAt, track?.sizeBytes) {
    produceState(track?.let(cached)) {
        if (track != null && value == null) value = loader(track)
    }
}

@Composable
internal fun AlbumArtwork(bitmap: Bitmap?, description: String?, modifier: Modifier = Modifier) {
    if (bitmap != null) Image(bitmap.asImageBitmap(), description, modifier, contentScale = ContentScale.Crop)
    else Box(modifier.background(Brush.linearGradient(listOf(GlassSurfaceColor, GlassGround))), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.stillune_foreground), description, Modifier.fillMaxSize())
    }
}

@Composable
private fun AlbumLyricsBack(lyrics: Lyrics, state: PlaybackState, compact: Boolean, onClose: () -> Unit,
                            onSeek: (Long) -> Unit, onImport: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color.White, Color(0xFFDDECF5))))
        .clickable(onClickLabel = "返回封面", onClick = onClose)
        .padding(if (compact) 6.dp else 14.dp)) {
        Column(Modifier.fillMaxWidth().padding(start = 6.dp, top = 4.dp, bottom = 4.dp)) {
            Text(state.track?.title ?: "未播放", fontSize = if (compact) 13.sp else 17.sp,
                fontWeight = FontWeight.SemiBold, color = InkAccent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            state.track?.let {
                Text(it.artist, fontSize = if (compact) 10.sp else 12.sp, color = InkMuted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (!compact) state.track?.let { AudioFormatBadge(it.format, Modifier.padding(start = 6.dp, bottom = 4.dp)) }
        LyricsPane(lyrics, state.positionMs, state.track != null, onSeek, onImport,
            Modifier.weight(1f).fillMaxWidth(), onReturnToCover = onClose, compact = compact, cardBack = true)
    }
}
