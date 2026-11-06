package com.stillune.app.ui

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stillune.app.R
import com.stillune.app.data.Lyrics
import com.stillune.app.data.Track
import com.stillune.app.playback.PlaybackState
import com.stillune.app.playback.RepeatMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
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
    val coverIndexes = remember(state.queue, state.index, nextIndex, state.shuffle) {
        if (nextIndex == null) emptyList()
        else if (state.shuffle) listOf(nextIndex)
        else (nextIndex until state.queue.size).take(3).takeWhile { it != state.index }
    }
    val flip by animateFloatAsState(
        if (lyricsVisible) 180f else 0f, tween(300), label = "album-flip")
    var swipeArrival by remember { mutableStateOf<Pair<String?, String>?>(null) }
    var drag by remember(uri) { mutableFloatStateOf(0f) }
    val backFacing by remember { derivedStateOf { flip >= 90f } }
    var settling by remember(uri) { mutableStateOf(false) }
    var motion by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val advance by rememberUpdatedState(onAdvance)
    val goBack by rememberUpdatedState(onPrevious)
    val turnPage by rememberUpdatedState(onLyrics)
    val density = LocalDensity.current
    DisposableEffect(uri) { onDispose { motion?.cancel() } }

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth * (if (showQueue) .86f else .96f), maxHeight - (if (showQueue) 28.dp else 4.dp), 380.dp).coerceAtLeast(48.dp)
        val sidePx = with(density) { side.toPx() }
        fun forwardProgress() = (-drag / sidePx).coerceIn(0f, 1f)
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
                        animate(drag, finish, animationSpec = tween((distance * 230f).toInt())) { value, _ -> drag = value }
                        swipeArrival = uri to targetUri
                        if (step > 0) advance() else goBack()
                        // Restore the card if the service has not acknowledged the step.
                        delay(350)
                        swipeArrival = null
                    }
                    animate(drag, 0f, animationSpec = spring(dampingRatio = .82f, stiffness = 430f)) { value, _ -> drag = value }
                } finally {
                    settling = false
                }
            }
        }
        val requestStep by rememberUpdatedState<(Int) -> Unit>(::settle)
        LaunchedEffect(stepRequests, showQueue) {
            if (showQueue) stepRequests.collect { requestStep(it) }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().height(side + 2.dp).testTag("album-deck")
                .semantics {
                    contentDescription = "专辑卡片组"
                    stateDescription = remainingLabel
                    customActions = buildList {
                        if (canAdvance) add(CustomAccessibilityAction("下一首") { requestStep(1); true })
                        if (canGoBack) add(CustomAccessibilityAction("上一首") { requestStep(-1); true })
                    }
                }, contentAlignment = Alignment.Center) {
                if (showQueue && !lyricsVisible) {
                    // Backmost first. Only a bounded set of upcoming covers is ever decoded.
                    for (slot in coverIndexes.indices.reversed()) {
                        val depth = slot + 1
                        val track = state.queue[coverIndexes[slot]]
                        key(track.uri) {
                            val cover by rememberTrackArtwork(track, loadArtwork, cachedArtwork)
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
                        val cover by rememberTrackArtwork(track, loadArtwork, cachedArtwork)
                        AlbumArtwork(cover, null, Modifier.size(side).graphicsLayer {
                            val back = (drag / sidePx).coerceIn(0f, 1f)
                            translationX = -sidePx * .62f * (1f - back)
                            scaleX = .82f + .18f * back; scaleY = scaleX; rotationY = 58f * (1f - back)
                            cameraDistance = 16f * density.density
                            alpha = (.56f + .44f * back) * (1f - forwardProgress())
                        }.clip(SquircleShape(12.dp)))
                    }
                }
                val input = if (backFacing) Modifier else Modifier
                    .pointerInput(uri, nextIndex, previousIndex, canTurn, showQueue, sidePx) {
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
                            if (dragging) requestStep(when {
                                cancelled -> 0
                                canAdvance && drag < -sidePx * .24f -> 1
                                canGoBack && drag > sidePx * .24f -> -1
                                else -> 0
                            })
                            else if (!cancelled) turnPage()
                        }
                    }
                Box(Modifier.size(side).then(input).graphicsLayer {
                    translationX = drag
                    rotationZ = if (showQueue) (drag / sidePx) * 7f else 0f
                    rotationY = if (flip <= 90f) flip else flip - 180f
                    cameraDistance = 20f * density.density
                }.shadow(18.dp, SquircleShape(12.dp), ambientColor = InkMuted.copy(alpha = .36f), spotColor = InkAccent.copy(alpha = .26f))
                    .clip(SquircleShape(12.dp)).background(GlassSurfaceColor).testTag("album-front")
                    .semantics {
                        contentDescription = "当前专辑卡片"
                        if (canTurn || lyricsVisible) onClick(label = if (lyricsVisible) "返回封面" else "查看歌词") { onLyrics(); true }
                        stateDescription = if (lyricsVisible) "歌词" else "封面"
                    }) {
                    if (backFacing) {
                        AlbumLyricsBack(lyrics, state, side < 176.dp, onLyrics, onSeek, onImportLyrics)
                    } else {
                        AnimatedContent(uri, Modifier.fillMaxSize(), transitionSpec = {
                            if (swipeArrival == (initialState to targetState)) {
                                (EnterTransition.None togetherWith ExitTransition.None).using(null)
                            } else {
                                val direction = if (showQueue) state.trackDirection else 0
                                val enter = if (direction == 0) fadeIn(tween(180)) else slideInHorizontally(tween(220)) { it * direction }
                                val exit = if (direction == 0) fadeOut(tween(180)) else slideOutHorizontally(tween(220)) { -it * direction }
                                (enter togetherWith exit).using(null)
                            }
                        }, label = "cover-slide") { cardUri ->
                            var retained by remember { mutableStateOf(artwork) }
                            DisposableEffect(cardUri) {
                                onDispose {
                                    if (swipeArrival?.first == cardUri) swipeArrival = null
                                }
                            }
                            if (cardUri == uri) SideEffect { retained = artwork }
                            AlbumArtwork(if (cardUri == uri) artwork else retained,
                                if (cardUri == uri) "${state.track?.album ?: "本地音乐"}专辑封面" else null,
                                Modifier.fillMaxSize())
                        }
                    }
                }
            }
            if (showQueue) Text(remainingLabel, Modifier.padding(top = 6.dp).testTag("album-remaining"), color = InkMuted, fontSize = 11.sp)
        }
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
