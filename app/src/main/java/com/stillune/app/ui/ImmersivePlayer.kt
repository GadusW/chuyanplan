package com.stillune.app.ui

import android.Manifest
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stillune.app.R
import com.stillune.app.immersion.*
import com.stillune.app.playback.PlaybackState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import kotlin.math.*
import kotlin.random.Random

@Composable
internal fun ImmersivePlayer(state: PlaybackState, artwork: Bitmap?, style: ImmersionStyle,
    onStyle: (ImmersionStyle) -> Unit, onToggle: () -> Unit, onPrevious: () -> Unit,
    onNext: () -> Unit, onSeek: (Long) -> Unit, onExit: () -> Unit) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    LaunchedEffect(style) {
        if (style != ImmersionStyle.PARALLAX && !allowed && !asked) {
            asked = true
            permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    val reducedMotion = !ValueAnimator.areAnimatorsEnabled()
    val motionFlow = remember(context, reducedMotion) { if (reducedMotion) flowOf(Tilt()) else motionSensor(context) }
    val tilt by motionFlow.collectAsStateWithLifecycle(Tilt())
    val needsAudio = allowed && state.playing && style != ImmersionStyle.PARALLAX
    val audioFlow = remember(state.audioSessionId, needsAudio) {
        if (needsAudio) liveAudioEnergy(state.audioSessionId)
        else flowOf(AudioEnergy())
    }
    val energy by audioFlow.collectAsStateWithLifecycle(AudioEnergy())
    ImmersiveScene(state, artwork, style, tilt, energy, reducedMotion, allowed,
        { permission.launch(Manifest.permission.RECORD_AUDIO) }, onStyle, onToggle, onPrevious, onNext, onSeek, onExit)
}

private data class SceneFrame(val time: Float = 0f, val energy: Float = 0f, val pulse: Float = 0f)

@Composable
internal fun ImmersiveScene(state: PlaybackState, artwork: Bitmap?, style: ImmersionStyle,
    tilt: Tilt, energy: AudioEnergy, reducedMotion: Boolean = false, audioAllowed: Boolean = false,
    onAudioPermission: () -> Unit = {}, onStyle: (ImmersionStyle) -> Unit,
    onToggle: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit,
    onSeek: (Long) -> Unit, onExit: () -> Unit) {
    var controls by rememberSaveable { mutableStateOf(false) }
    var interaction by remember { mutableLongStateOf(0L) }
    val taps = remember { TripleTapGate() }
    val currentEnergy by rememberUpdatedState(energy.rms)
    val currentPlaying by rememberUpdatedState(state.playing)
    val currentTilt by rememberUpdatedState(tilt)
    val frame = remember { mutableStateOf(SceneFrame()) }
    val palette = remember(artwork) { artworkColors(artwork) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, reducedMotion, style) {
        if (reducedMotion || style == ImmersionStyle.PARALLAX) { frame.value = SceneFrame(); return@LaunchedEffect }
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var previous = 0L
            var time = frame.value.time
            val attack = LowPass(.07f)
            val release = LowPass(.28f)
            while (isActive) {
                withFrameNanos { now ->
                    val dt = if (previous == 0L) 0f else ((now - previous) / 1e9f).coerceIn(0f, .08f)
                    previous = now
                    val level = if (currentPlaying && currentEnergy > .000001f)
                        ((20f * log10(currentEnergy) + 80f) / 65f).coerceIn(0f, 1f) else 0f
                    val fast = attack.update(level, dt)
                    val slow = release.update(level, dt)
                    if (currentPlaying || currentTilt.motion > .005f) time += dt * (.3f + fast * .8f + currentTilt.motion * 1.6f)
                    frame.value = SceneFrame(time, fast, (fast - slow).coerceAtLeast(0f))
                }
                delay(if (!currentPlaying && currentTilt.motion < .005f && frame.value.energy < .001f) 200 else 24)
            }
        }
    }
    LaunchedEffect(controls, interaction) { if (controls) { delay(10_000); controls = false } }
    fun activity() { interaction = SystemClock.uptimeMillis() }
    val exit by rememberUpdatedState(onExit)
    BoxWithConstraints(Modifier.fillMaxSize()
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.any { it.pressed }) activity()
                }
            }
        }
        .semantics { contentDescription = "体感沉浸画面"; customActions = listOf(
            CustomAccessibilityAction("显示播放控制") { controls = true; activity(); true },
            CustomAccessibilityAction("退出体感沉浸") { exit(); true }) }) {
        MoonlitBackdrop(artwork, Modifier.matchParentSize())
        val side by animateDpAsState(if (style == ImmersionStyle.PARALLAX)
            minOf(maxWidth * .86f, maxHeight * .61f, 430.dp)
            else minOf(maxWidth * .60f, maxHeight * .40f, 300.dp), tween(if (reducedMotion) 0 else 280), label = "immersive-cover-size")
        val controlMaxHeight = (maxHeight - 24.dp).coerceAtLeast(160.dp)
        Box(Modifier.matchParentSize().pointerInput(Unit) {
            detectTapGestures(onTap = {
                if (taps.tap(SystemClock.uptimeMillis())) exit()
                else { controls = true; activity() }
            })
        }) {
            if (style != ImmersionStyle.PARALLAX) ImmersionCanvas(style, frame, tilt, reducedMotion, palette, Modifier.fillMaxSize())
            Box(Modifier.align(Alignment.Center).offset(y = (-24).dp).size(side)
                .graphicsLayer {
                    val movement = if (reducedMotion) Tilt() else tilt
                    translationX = (movement.x * 38f + movement.shakeX * 24f).dp.toPx()
                    translationY = (movement.y * 32f + movement.shakeY * 22f).dp.toPx()
                    rotationY = movement.x * 16f + movement.shakeX * 4f
                    rotationX = -movement.y * 13f - movement.shakeY * 4f
                    scaleX = 1f + movement.motion * .035f
                    scaleY = scaleX
                    cameraDistance = 24 * density
                    shadowElevation = 16.dp.toPx()
                    shape = RoundedCornerShape(12.dp)
                    clip = true
                }.background(Panel)) {
                if (artwork != null) Image(artwork.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Image(painterResource(R.drawable.stillune_foreground), null,
                    Modifier.fillMaxSize().graphicsLayer(scaleX = 1.6f, scaleY = 1.6f))
            }
        }
        if (!controls) Text(state.track?.title ?: "让声音停泊", Modifier.align(Alignment.BottomCenter)
            .safeDrawingPadding().padding(horizontal = 28.dp, vertical = 36.dp),
            color = Moon, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        AnimatedVisibility(controls, Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
            Column(Modifier.fillMaxWidth().heightIn(max = controlMaxHeight).safeDrawingPadding().padding(20.dp)
                .clip(RoundedCornerShape(20.dp)).background(Night.copy(alpha = .94f))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(state.track?.title ?: "让声音停泊", Modifier.weight(1f), color = Moon,
                        fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = onExit) { Icon(Icons.Rounded.Close, "退出体感沉浸", tint = Quiet) }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ImmersionStyle.entries.forEach { choice ->
                        FilterChip(style == choice, { onStyle(choice); activity() }, label = { Text(choice.title, fontSize = 12.sp) })
                    }
                }
                if (style != ImmersionStyle.PARALLAX && !audioAllowed) {
                    Text("节奏响应需播放波形权限，不采集麦克风。", color = Quiet, fontSize = 12.sp)
                    TextButton(onClick = onAudioPermission) { Text("启用节奏响应") }
                } else if (style != ImmersionStyle.PARALLAX && energy.message != null) Text(energy.message, color = Quiet, fontSize = 12.sp)
                if (!tilt.available && !reducedMotion) Text("未检测到方向传感器，封面保持静止", color = Quiet, fontSize = 12.sp)
                if (reducedMotion) Text("已遵循系统设置，减少动态效果", color = Quiet, fontSize = 12.sp)
                ImmersionControls(state, onToggle, onPrevious, onNext, onSeek, ::activity)
                Text("闲置 10 秒隐藏控制 · 连续轻点画面三下退出", color = Quiet, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun ImmersionCanvas(style: ImmersionStyle, frame: State<SceneFrame>, tilt: Tilt, reduced: Boolean, palette: ArtworkColors, modifier: Modifier) {
    val particles = remember {
        val random = Random(814)
        List(108) { floatArrayOf(random.nextFloat(), random.nextFloat(), random.nextFloat(), random.nextFloat()) }
    }
    Canvas(modifier) {
        if (reduced) return@Canvas
        val f = frame.value
        drawRect(palette.ground)
        val strength = (f.energy * .85f + f.pulse * 2.2f + tilt.motion * 1.3f).coerceIn(0f, 1f)
        if (strength < .003f) return@Canvas
        val center = Offset(size.width * (.5f + tilt.x * .10f + tilt.shakeX * .05f), size.height * (.46f + tilt.y * .08f + tilt.shakeY * .05f))
        drawCircle(Brush.radialGradient(listOf(palette.primary.copy(alpha = strength * .24f), Color.Transparent),
            center = center, radius = size.minDimension * .9f), size.minDimension * .9f, center)
        if (style == ImmersionStyle.RIPPLES) {
            for (i in 0 until 9) {
                val phase = ((f.time * .30f + i / 9f) % 1f)
                val radius = size.minDimension * (.12f + phase * .95f)
                val ink = if (i % 3 == 0) palette.secondary else palette.primary
                drawCircle(ink.copy(alpha = strength * (1f-phase) * .12f), radius, center,
                    style = Stroke((6f + tilt.motion * 8f).dp.toPx()))
                drawCircle(ink.copy(alpha = strength * (1f-phase) * .75f), radius, center,
                    style = Stroke((1f + f.pulse * 3f + tilt.motion * 1.2f).dp.toPx()))
            }
        } else {
            particles.forEachIndexed { index, seed ->
                val t = f.time * (.18f + seed[2] * .3f)
                val x = ((seed[0] + sin(t + seed[3]*6.28f) * .075f + tilt.x * (.06f+seed[2]*.11f) + tilt.shakeX*.08f) % 1f + 1f) % 1f
                val y = ((seed[1] - t * (.05f+seed[2]*.055f) + tilt.y*.08f + tilt.shakeY*.08f) % 1f + 1f) % 1f
                val opacity = ((.4f + .45f * sin(t * 2f + index).absoluteValue) * strength).coerceIn(0f,1f)
                val ink = if (index % 4 == 0) palette.secondary else palette.primary
                val position = Offset(x*size.width, y*size.height)
                drawLine(ink.copy(alpha = opacity * .4f), position,
                    position + Offset(-tilt.shakeX*60f, (8f + tilt.motion*42f) * (1f+seed[2])), 1.dp.toPx())
                drawCircle(ink.copy(alpha = opacity * .13f), (5f+seed[2]*6f).dp.toPx(), position)
                drawCircle(ink.copy(alpha = opacity), (1f + seed[2] * 2.1f + f.pulse * 3f).dp.toPx(), position)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ImmersionControls(state: PlaybackState, onToggle: () -> Unit, onPrevious: () -> Unit,
    onNext: () -> Unit, onSeek: (Long) -> Unit, onActivity: () -> Unit) {
    var drag by remember(state.track?.uri) { mutableStateOf<Float?>(null) }
    val duration = state.durationMs.coerceAtLeast(1).toFloat()
    Slider(drag ?: state.positionMs.toFloat().coerceIn(0f, duration),
        { drag = it; onActivity() }, onValueChangeFinished = { drag?.let { onSeek(it.toLong()) }; drag = null; onActivity() },
        enabled = state.durationMs > 0, valueRange = 0f..duration,
        modifier = Modifier.semantics { contentDescription = "沉浸播放进度" },
        thumb = { Box(Modifier.size(10.dp).background(Moon, androidx.compose.foundation.shape.CircleShape)) },
        track = { slider -> SliderDefaults.Track(slider, Modifier.height(3.dp),
            colors = SliderDefaults.colors(activeTrackColor = Moon, inactiveTrackColor = Quiet.copy(alpha = .22f)),
            thumbTrackGapSize = 0.dp, drawStopIndicator = null) })
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(timeLabel(state.positionMs), color = Quiet, fontSize = 11.sp)
        Text(timeLabel(state.durationMs), color = Quiet, fontSize = 11.sp)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onPrevious(); onActivity() }, enabled = state.track != null) { Icon(Icons.Rounded.SkipPrevious, "上一首") }
        FilledIconButton(onClick = { onToggle(); onActivity() }, enabled = state.track != null,
            modifier = Modifier.size(56.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Moon, contentColor = Night)) {
            Icon(if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (state.playing) "暂停" else "播放")
        }
        IconButton(onClick = { onNext(); onActivity() }, enabled = state.track != null) { Icon(Icons.Rounded.SkipNext, "下一首") }
    }
}
