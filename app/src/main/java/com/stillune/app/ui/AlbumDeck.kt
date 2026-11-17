package com.stillune.app.ui

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current
    val photos = remember(context) { AlbumCardPhotos(context.applicationContext) }
    var photoTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var photoRevision by remember { mutableIntStateOf(0) }
    var importingPhoto by remember { mutableStateOf(false) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { result ->
        val target = photoTarget
        photoTarget = null
        if (target != null && result != null) {
            importingPhoto = true
            scope.launch {
                try {
                    if (photos.add(target, result)) photoRevision++
                    else Toast.makeText(context, "无法添加这张图片", Toast.LENGTH_SHORT).show()
                } finally {
                    importingPhoto = false
                }
            }
        }
    }
    val trackKey = state.track?.identity
    val addedPhotos = remember(trackKey, photoRevision) { trackKey?.let(photos::photos).orEmpty() }
    val preferred = remember(trackKey, photoRevision) { trackKey?.let(photos::preferred) }
    var browsed by remember(trackKey, state.index) { mutableStateOf<String?>(null) }
    var menuPhoto by remember(trackKey, state.index, lyricsVisible) { mutableStateOf<String?>(null) }
    val coverPhoto = ""
    val currentCover = if (showQueue && addedPhotos.isNotEmpty()) {
        rememberTrackArtwork(state.track, loadArtwork, cachedArtwork).value
    } else artwork
    val pages = remember(currentCover != null, addedPhotos) {
        if (currentCover != null) listOf(coverPhoto) + addedPhotos else addedPhotos
    }
    val selectedPhoto = browsed?.takeIf { it in pages } ?: preferred?.takeIf { it in pages } ?: pages.firstOrNull()
    val manageablePhoto by rememberUpdatedState(selectedPhoto)
    val openPhotoMenu by rememberUpdatedState<() -> Unit> { menuPhoto = selectedPhoto }
    val nextPhoto by rememberUpdatedState<() -> Unit> {
        if (pages.size > 1) browsed = pages[(pages.indexOf(selectedPhoto) + 1) % pages.size]
    }
    val addPhoto by rememberUpdatedState<() -> Unit> {
        if (trackKey != null && photoTarget == null && !importingPhoto) {
            photoTarget = trackKey
            photoPicker.launch(arrayOf("image/*"))
        }
    }
    val advance by rememberUpdatedState(onAdvance)
    val goBack by rememberUpdatedState(onPrevious)
    val turnPage by rememberUpdatedState(onLyrics)
    val density = LocalDensity.current
    DisposableEffect(uri, state.index) { onDispose { motion?.cancel() } }

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth * (if (showQueue) .70f else .96f),
            if (showQueue) (maxHeight - 25.dp) / 1.23f else maxHeight - 4.dp, 360.dp).coerceAtLeast(48.dp)
        val reflection = if (showQueue) side * .20f else 0.dp
        val stageHeight = side + reflection + if (showQueue) 3.dp else 2.dp
        val sidePx = with(density) { side.toPx() }
        val showPreview = showQueue && state.queue.size > 2
        val focus by remember(uri, state.index, sidePx) { derivedStateOf { (-drag / sidePx).roundToInt() } }
        val slots = remember(state.index, state.queue.size, nextIndex, previousIndex, state.shuffle, showQueue, showPreview, direction) {
            buildMap {
                put(state.index, 0)
                if (showQueue) {
                    nextIndex?.let { put(it, 1) }
                    previousIndex?.let { previous ->
                        if (direction < 0 || previous !in this) put(previous, -1)
                    }
                    if (showPreview && !state.shuffle) {
                        for (depth in 2..3) {
                            nextIndex?.plus(depth - 1)?.takeIf { it in state.queue.indices && it !in this }?.let { put(it, depth) }
                            previousIndex?.minus(depth - 1)?.takeIf { it in state.queue.indices && it !in this }?.let { put(it, -depth) }
                        }
                    }
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
            Box(Modifier.fillMaxWidth().height(stageHeight).clipToBounds().testTag("album-deck")
                .semantics {
                    contentDescription = "专辑卡片组"
                    stateDescription = remainingLabel
                    customActions = buildList {
                        if (canAdvance) add(CustomAccessibilityAction("下一首") { settle(1); true })
                        if (canGoBack) add(CustomAccessibilityAction("上一首") { settle(-1); true })
                    }
                }, contentAlignment = Alignment.TopCenter) {
                val input = if (backFacing) Modifier else Modifier
                    .pointerInput(uri, state.index, nextIndex, previousIndex, canTurn, showQueue, sidePx, lyricsVisible) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (settling || !canTurn || flip > 0f) return@awaitEachGesture
                            var dragging = false
                            var vertical = false
                            var verticalDistance = 0f
                            var cancelled = false
                            val longPressAt = down.uptimeMillis + viewConfiguration.longPressTimeoutMillis
                            var eventTime = down.uptimeMillis
                            while (true) {
                                val event = if (!dragging && !vertical && manageablePhoto != null) {
                                    withTimeoutOrNull((longPressAt - eventTime).coerceAtLeast(1L)) { awaitPointerEvent() }
                                } else awaitPointerEvent()
                                if (event == null) {
                                    openPhotoMenu()
                                    return@awaitEachGesture
                                }
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null) { cancelled = true; break }
                                eventTime = change.uptimeMillis
                                if (!change.pressed) {
                                    if (dragging || vertical) change.consume()
                                    else if (change.isConsumed) cancelled = true
                                    else if (manageablePhoto != null && eventTime >= longPressAt) {
                                        change.consume()
                                        openPhotoMenu()
                                        return@awaitEachGesture
                                    }
                                    break
                                }
                                val delta = change.position - down.position
                                if (!dragging && !vertical) {
                                    if (change.isConsumed) {
                                        cancelled = true
                                        break
                                    }
                                    if (abs(delta.y) > viewConfiguration.touchSlop && abs(delta.y) > abs(delta.x)) {
                                        vertical = true
                                    } else if (abs(delta.x) > viewConfiguration.touchSlop) {
                                        if (!showQueue) { cancelled = true; break }
                                        dragging = true
                                    }
                                }
                                if (vertical) {
                                    change.consume()
                                    verticalDistance = delta.y
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
                            else if (!cancelled && vertical) {
                                val threshold = minOf(40.dp.toPx(), sidePx * .22f)
                                if (verticalDistance <= -threshold) nextPhoto()
                                if (verticalDistance >= threshold) addPhoto()
                            } else if (!cancelled) turnPage()
                        }
                    }
                // The signed distance from the focus drives every card's perspective.
                // At a committed step the incoming card already has its final pose.
                for ((index, slot) in slots) {
                    val track = state.queue.getOrNull(index)
                    key(index, track?.uri) {
                        val current = index == state.index
                        val cover = key("embedded-cover") {
                            when {
                                current && addedPhotos.isNotEmpty() -> currentCover
                                showQueue -> rememberTrackArtwork(track, loadArtwork, cachedArtwork).value
                                else -> artwork
                            }
                        }
                        val cardPhotos = remember(track?.identity, photoRevision) {
                            track?.identity?.let(photos::photos).orEmpty()
                        }
                        val cardPreferred = remember(track?.identity, photoRevision) {
                            track?.identity?.let(photos::preferred)
                        }
                        val cardPhoto = if (current) selectedPhoto else cardPreferred?.takeIf { it in cardPhotos }
                            ?: cardPhotos.firstOrNull().takeIf { cover == null }
                        val displayedPhoto = if (cardPhotos.isEmpty()) null else key("card-photo-loader") {
                            // Keep the loaded bitmap attached to this card as it moves between slots.
                            produceState<Bitmap?>(null, cardPhoto) {
                                value = cardPhoto?.takeIf { it != coverPhoto }?.let { photos.load(it) }
                            }.value
                        }
                        val cardShape = SquircleShape(12.dp)
                        val face = rememberGraphicsLayer()
                        Box(Modifier.size(side, stageHeight)
                            .zIndex(4f - abs(slot - focus))
                            .absoluteOffset {
                                val position = slot + drag / sidePx
                                val distance = abs(position)
                                val spread = minOf(distance, 1f) * .62f + (distance - 1f).coerceAtLeast(0f) * .21f
                                IntOffset((spread * position.sign * sidePx).roundToInt(), 0)
                            }.graphicsLayer {
                                val position = slot + drag / sidePx
                                val distance = abs(position)
                                val turn = distance.coerceAtMost(1f)
                                val scale = 1f - .12f * turn
                                scaleX = scale
                                scaleY = scale
                                translationY = sidePx * (1f - scale) / 2f
                                val lyricTurn = if (current) (if (flip <= 90f) flip else flip - 180f) else 0f
                                rotationY = -position.sign * 56f * turn + lyricTurn
                                cameraDistance = 14f * density.density
                                transformOrigin = TransformOrigin(.5f, .5f * side.value / stageHeight.value)
                                alpha = when {
                                    current -> 1f
                                    lyricsVisible -> 0f
                                    !showPreview -> (abs(drag) / sidePx * 3f).coerceIn(0f, 1f)
                                    else -> (1f - (distance - 1.25f).coerceAtLeast(0f) * .65f).coerceIn(0f, 1f)
                                }
                            }) {
                            Box(Modifier.size(side).shadow(9.dp, cardShape).clip(cardShape)
                                .background(GlassSurfaceColor)
                                .then(if (showQueue) Modifier.drawWithContent {
                                    face.record { this@drawWithContent.drawContent() }
                                    drawLayer(face)
                                } else Modifier)
                                .testTag("album-card-${track?.uri.orEmpty()}")) {
                                Box(Modifier.fillMaxSize().then(if (current) Modifier.testTag("album-front").semantics {
                                    contentDescription = "当前专辑卡片"
                                    if (canTurn || lyricsVisible) onClick(label = if (lyricsVisible) "返回封面" else "查看歌词") {
                                        onLyrics()
                                        true
                                    }
                                    if (!lyricsVisible && selectedPhoto != null) onLongClick(label = "管理卡片图片") {
                                        openPhotoMenu()
                                        true
                                    }
                                    stateDescription = if (lyricsVisible) "歌词" else "封面"
                                } else Modifier)) {
                                    if (current && backFacing) {
                                        AlbumLyricsBack(lyrics, state, side < 176.dp, onLyrics, onSeek, onImportLyrics)
                                    } else {
                                        val description = if (current) "${track?.album ?: "本地音乐"}专辑封面" else null
                                        if (cardPhotos.isNotEmpty()) {
                                            val bitmap = if (cardPhoto.isNullOrEmpty()) cover else displayedPhoto ?: cover
                                            AnimatedContent(bitmap, transitionSpec = {
                                                (slideInVertically(tween(220)) { it / 4 } + fadeIn(tween(220))) togetherWith
                                                    (slideOutVertically(tween(220)) { -it / 4 } + fadeOut(tween(180)))
                                            }, label = "album-card-photo") { image ->
                                                if (image == null && showQueue && track != null) MissingDeckCover(track, description)
                                                else AlbumArtwork(image, if (image === cover) description else "自定义卡片图片", Modifier.fillMaxSize())
                                            }
                                        } else if (showQueue && cover == null && track != null) MissingDeckCover(track, description)
                                        else AlbumArtwork(cover, description, Modifier.fillMaxSize())
                                    }
                                }
                            }
                            if (showQueue && track != null && !lyricsVisible) {
                                Box(Modifier.offset(y = side + 2.dp).size(side, reflection)
                                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                    .drawWithCache {
                                        val fade = Brush.verticalGradient(listOf(Color.White.copy(alpha = .24f), Color.Transparent))
                                        onDrawBehind {
                                            withTransform({
                                                translate(top = sidePx)
                                                scale(1f, -1f, Offset.Zero)
                                            }) { drawLayer(face) }
                                            drawRect(fade, blendMode = BlendMode.DstIn)
                                        }
                                    })
                            }
                        }
                    }
                }
                if (!backFacing) Box(Modifier.size(side).zIndex(6f).then(input).semantics {
                    customActions = buildList {
                        if (canTurn) add(CustomAccessibilityAction("添加卡片图片") { addPhoto(); true })
                        if (pages.size > 1) add(CustomAccessibilityAction("下一张卡片图片") { nextPhoto(); true })
                    }
                })
                val photo = menuPhoto
                if (trackKey != null && photo != null && photo in pages && !backFacing) {
                    Box(Modifier.align(Alignment.Center)) {
                        DropdownMenu(expanded = true, onDismissRequest = { menuPhoto = null }) {
                            DropdownMenuItem(text = { Text("设为首选图") }, onClick = {
                                photos.prefer(trackKey, photo.takeUnless { it == coverPhoto })
                                photoRevision++
                                menuPhoto = null
                            })
                            DropdownMenuItem(text = { Text("删除这张图片") }, enabled = photo != coverPhoto, onClick = {
                                menuPhoto = null
                                scope.launch {
                                    photos.remove(trackKey, photo)
                                    photoRevision++
                                }
                            })
                        }
                    }
                }

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
