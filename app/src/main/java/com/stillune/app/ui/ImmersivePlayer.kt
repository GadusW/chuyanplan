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

@Composable
internal fun ImmersivePlayer(state: PlaybackState, artwork: Bitmap?, style: ImmersionStyle,
    onStyle: (ImmersionStyle) -> Unit, onToggle: () -> Unit, onPrevious: () -> Unit,
    onNext: () -> Unit, onSeek: (Long) -> Unit, onExit: () -> Unit) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val reducedMotion = !ValueAnimator.areAnimatorsEnabled()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    LaunchedEffect(style, reducedMotion) {
        if (!reducedMotion && style != ImmersionStyle.PARALLAX && !allowed && !asked) {
            asked = true
            permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    val motionFlow = remember(context, reducedMotion) { if (reducedMotion) flowOf(Tilt()) else motionSensor(context) }
    val tilt = motionFlow.collectAsStateWithLifecycle(Tilt())
    val needsAudio = !reducedMotion && allowed && state.playing && style != ImmersionStyle.PARALLAX
    val audioFlow = remember(state.audioSessionId, needsAudio) {
        if (needsAudio) liveAudioEnergy(state.audioSessionId)
        else flowOf(AudioEnergy())
    }
    val energy = audioFlow.collectAsStateWithLifecycle(AudioEnergy())
    ImmersiveScene(state, artwork, style, Tilt(), AudioEnergy(), reducedMotion, allowed,
        { permission.launch(Manifest.permission.RECORD_AUDIO) }, onStyle, onToggle, onPrevious, onNext, onSeek, onExit,
        tiltState = tilt, energyState = energy)
}

private data class SceneFrame(val time: Float = 0f, val energy: Float = 0f,
    val pulse: Float = 0f, val tilt: Tilt = Tilt(), val fullDetail: Boolean = true)

@Composable
internal fun ImmersiveScene(state: PlaybackState, artwork: Bitmap?, style: ImmersionStyle,
    tilt: Tilt, energy: AudioEnergy, reducedMotion: Boolean = false, audioAllowed: Boolean = false,
    onAudioPermission: () -> Unit = {}, onStyle: (ImmersionStyle) -> Unit,
    onToggle: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit,
    onSeek: (Long) -> Unit, onExit: () -> Unit,
    tiltState: State<Tilt>? = null, energyState: State<AudioEnergy>? = null) {
    var controls by rememberSaveable { mutableStateOf(false) }
    var interaction by remember { mutableLongStateOf(0L) }
    val taps = remember { TripleTapGate() }
    val currentEnergy by rememberUpdatedState(energy)
    val currentPlaying by rememberUpdatedState(state.playing)
    val currentTilt by rememberUpdatedState(tilt)
    val frame = remember { mutableStateOf(SceneFrame()) }
    val dynamics = remember(style, state.track?.uri) { ImmersionDynamics() }
    val palette = remember(artwork) { artworkColors(artwork) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, reducedMotion, dynamics) {
        if (reducedMotion) { frame.value = SceneFrame(); return@LaunchedEffect }
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var previous = 0L
            var time = 0f
            var beatId = (energyState?.value ?: currentEnergy).beatId
            var smooth = Tilt()
            var level = 0f
            var interval = .016f
            var fullDetail = true
            while (isActive) {
                withFrameNanos { now ->
                    val dt = if (previous == 0L) .016f else ((now - previous) / 1e9f).coerceIn(0f, .05f)
                    previous = now
                    interval += (dt - interval) * .08f
                    if (interval > .028f) fullDetail = false
                    else if (interval < .019f) fullDetail = true
                    val target = tiltState?.value ?: currentTilt
                    val alpha = 1f - exp(-dt / .032f)
                    fun follow(a: Float, b: Float) = a + (b - a) * alpha
                    smooth = Tilt(follow(smooth.x, target.x), follow(smooth.y, target.y),
                        follow(smooth.motion, target.motion), target.available,
                        follow(smooth.shakeX, target.shakeX), follow(smooth.shakeY, target.shakeY))
                    val audio = energyState?.value ?: currentEnergy
                    val fresh = audio.available && (audio.capturedNanos == 0L || System.nanoTime() - audio.capturedNanos < 350_000_000L)
                    val desired = if (currentPlaying && fresh) audio.level else 0f
                    level += (1f - exp(-dt / if (desired > level) .025f else .14f)) * (desired - level)
                    val impulse = if (currentPlaying && fresh && audio.beatId != beatId) audio.onset else 0f
                    beatId = audio.beatId
                    time += dt
                    if (style != ImmersionStyle.PARALLAX) dynamics.advance(dt, smooth, level, impulse)
                    frame.value = SceneFrame(time, level, dynamics.pulse, smooth, fullDetail)
                }
                // Active motion is display-synchronised, without an extra 24 ms throttle.
                val latestTilt = tiltState?.value ?: currentTilt
                if (!currentPlaying && latestTilt.motion < .005f &&
                    abs(latestTilt.x - frame.value.tilt.x) + abs(latestTilt.y - frame.value.tilt.y) < .001f &&
                    frame.value.energy < .001f && dynamics.ripples.isEmpty() &&
                    dynamics.motes.none { it.age < it.life }) delay(80)
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
        if (style == ImmersionStyle.PARALLAX || reducedMotion) MoonlitBackdrop(artwork, Modifier.matchParentSize())
        val side by animateDpAsState(if (style == ImmersionStyle.PARALLAX)
            minOf(maxWidth * .86f, maxHeight * .61f, 430.dp)
            else minOf(maxWidth * .52f, maxHeight * .36f, 270.dp), tween(if (reducedMotion) 0 else 280), label = "immersive-cover-size")
        val controlMaxHeight = (maxHeight - 24.dp).coerceAtLeast(160.dp)
        Box(Modifier.matchParentSize().pointerInput(Unit) {
            detectTapGestures(onTap = {
                if (taps.tap(SystemClock.uptimeMillis())) exit()
                else { controls = true; activity() }
            })
        }) {
            if (style != ImmersionStyle.PARALLAX) ImmersionCanvas(style, frame, dynamics, reducedMotion, palette, Modifier.fillMaxSize())
            Box(Modifier.align(Alignment.Center).offset(y = (-24).dp).size(side)
                .graphicsLayer {
                    val movement = frame.value.tilt
                    translationX = (movement.x * 38f + movement.shakeX * 24f).dp.toPx()
                    translationY = (movement.y * 32f + movement.shakeY * 22f).dp.toPx()
                    rotationY = movement.x * 16f + movement.shakeX * 4f
                    rotationX = -movement.y * 13f - movement.shakeY * 4f
                    scaleX = 1f + movement.motion * .035f + frame.value.pulse * .018f
                    scaleY = scaleX
                    cameraDistance = 24 * density
                    shadowElevation = 16.dp.toPx()
                    shape = SquircleShape(12.dp)
                    clip = true
                }.background(GlassSurfaceColor)) {
                if (artwork != null) Image(artwork.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Image(painterResource(R.drawable.stillune_foreground), null,
                    Modifier.fillMaxSize().graphicsLayer(scaleX = 1.6f, scaleY = 1.6f))
            }
        }
        if (!controls) Text(state.track?.title ?: "让声音停泊", Modifier.align(Alignment.BottomCenter)
            .safeDrawingPadding().padding(horizontal = 28.dp, vertical = 36.dp),
            color = if (style == ImmersionStyle.PARALLAX) InkAccent else Color.White, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        AnimatedVisibility(controls, Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
            Column(Modifier.fillMaxWidth().heightIn(max = controlMaxHeight).safeDrawingPadding().padding(20.dp)
                .clip(SquircleShape(20.dp)).background(GlassGround.copy(alpha = .94f))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(state.track?.title ?: "让声音停泊", Modifier.weight(1f), color = InkAccent,
                        fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = onExit) { Icon(Icons.Rounded.Close, "退出体感沉浸", tint = InkMuted) }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ImmersionStyle.entries.forEach { choice ->
                        FilterChip(style == choice, { onStyle(choice); activity() }, label = { Text(choice.title, fontSize = 12.sp) })
                    }
                }
                val signal = energyState?.value ?: energy
                val motion = tiltState?.value ?: tilt
                if (style != ImmersionStyle.PARALLAX && !audioAllowed) {
                    Text("节奏响应需播放波形权限，不采集麦克风。", color = InkMuted, fontSize = 12.sp)
                    TextButton(onClick = onAudioPermission) { Text("启用节奏响应") }
                } else if (style != ImmersionStyle.PARALLAX && signal.message != null) Text(signal.message, color = InkMuted, fontSize = 12.sp)
                if (!motion.available && !reducedMotion) Text("未检测到方向传感器，封面保持静止", color = InkMuted, fontSize = 12.sp)
                if (reducedMotion) Text("已遵循系统设置，减少动态效果", color = InkMuted, fontSize = 12.sp)
                ImmersionControls(state, onToggle, onPrevious, onNext, onSeek, ::activity)
                Text("闲置 10 秒隐藏控制 · 连续轻点画面三下退出", color = InkMuted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun ImmersionCanvas(style: ImmersionStyle, frame: State<SceneFrame>, dynamics: ImmersionDynamics,
    reduced: Boolean, palette: ArtworkColors, modifier: Modifier) {
    val path = remember { Path() }
    val points = remember { FloatArray(48 * 2) }
    Canvas(modifier) {
        if (reduced) return@Canvas
        val f = frame.value // Only redraw/layer updates: no per-frame layout or recomposition.
        val tilt = f.tilt
        val unit = size.minDimension
        val center = Offset(size.width * .5f, size.height * .46f)
        drawRect(palette.ground)
        if (style == ImmersionStyle.RIPPLES) {
            fun water(radius: Float, age: Float, seed: Float, strength: Float, origin: Offset) {
                path.reset()
                val segments = if (f.fullDetail) 48 else 24
                for (step in 0 until segments) {
                    val angle = step.toFloat() / segments * 2f * PI.toFloat()
                    val bend = ImmersionDynamics.contour(angle, age, seed, strength, tilt)
                    val r = (radius + bend).coerceAtLeast(.02f)
                    val point = origin + Offset(cos(angle) * r * unit,
                        sin(angle) * r * unit * (1f + tilt.y * .10f))
                    points[step * 2] = point.x
                    points[step * 2 + 1] = point.y
                }
                val last = (segments - 1) * 2
                path.moveTo((points[last] + points[0]) * .5f, (points[last + 1] + points[1]) * .5f)
                for (step in 0 until segments) {
                    val next = (step + 1) % segments
                    path.quadraticBezierTo(points[step * 2], points[step * 2 + 1],
                        (points[step * 2] + points[next * 2]) * .5f,
                        (points[step * 2 + 1] + points[next * 2 + 1]) * .5f)
                }
                path.close()
            }
            // Low-amplitude surface currents connect the hits without a fake beat clock.
            if (f.energy + tilt.motion > .01f) repeat(if (f.fullDetail) 3 else 1) { index ->
                water(.30f + index * .095f, f.time, index * 1.7f, f.energy * .3f,
                    center + Offset(tilt.x * unit * .12f, tilt.y * unit * .10f))
                drawPath(path, palette.primary.copy(alpha = (.05f + f.energy * .10f + tilt.motion * .07f).coerceAtMost(.23f)),
                    style = Stroke(.7.dp.toPx()))
            }
            // Keep overdraw bounded even when dense music fills all eight simulation slots.
            for (index in maxOf(0, dynamics.ripples.size - if (f.fullDetail) 5 else 2) until dynamics.ripples.size) {
                val ripple = dynamics.ripples[index]
                val progress = ripple.age / ripple.life
                val fade = (1f - progress).pow(1.5f) * (1f - exp(-ripple.age * 32f))
                val origin = center + Offset((ripple.x + tilt.x * ripple.age * .055f) * unit,
                    (ripple.y + tilt.y * ripple.age * .05f) * unit)
                val ink = lerp(if (ripple.seed > 3.14f) palette.primary else palette.secondary, InkAccent, .38f)
                repeat(if (f.fullDetail && ripple.age < .5f) 2 else 1) { crest ->
                    val radius = .24f + ripple.age * (.35f + ripple.strength * .16f) - crest * .038f
                    water(radius, ripple.age, ripple.seed, ripple.strength, origin)
                    val alpha = (fade * (.38f + ripple.strength * .5f) * if (crest == 0) 1f else .4f).coerceIn(0f, 1f)
                    drawPath(path, ink.copy(alpha = alpha), style = Stroke((.9f + fade * .8f).dp.toPx()))
                }
            }
        } else {
            for (index in dynamics.motes.indices step if (f.fullDetail) 1 else 2) {
                val mote = dynamics.motes[index]
                if (mote.age >= mote.life) continue
                val progress = mote.age / mote.life
                val opacity = ((1f - progress).pow(1.5f) * (1f - exp(-mote.age * 40f))).coerceIn(0f, 1f)
                val ink = if (mote.color == 0) palette.secondary else palette.primary
                val position = center + Offset(mote.x * unit, mote.y * unit)
                // Velocity-aligned streaks stretch on attacks, curl in the hand's current.
                val tail = position - Offset(mote.vx * unit, mote.vy * unit) * (.025f + f.pulse * .055f)
                drawLine(ink.copy(alpha = opacity * .52f), tail, position,
                    (mote.size * .65f).dp.toPx(), cap = StrokeCap.Round)
                if (f.fullDetail) drawCircle(ink.copy(alpha = opacity * .1f), (mote.size * 3.2f).dp.toPx(), position)
                drawCircle(lerp(ink, InkAccent, .65f).copy(alpha = opacity), mote.size.dp.toPx(), position)
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
    PlaybackSeekBar(drag ?: state.positionMs.toFloat().coerceIn(0f, duration), state.durationMs, "沉浸播放进度",
        { drag = it; onActivity() }, { drag?.let { onSeek(it.toLong()) }; drag = null; onActivity() })
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(timeLabel(state.positionMs), color = InkMuted, fontSize = 11.sp)
        Text(timeLabel(state.durationMs), color = InkMuted, fontSize = 11.sp)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onPrevious(); onActivity() }, enabled = state.track != null) { Icon(Icons.Rounded.SkipPrevious, "上一首") }
        FilledIconButton(onClick = { onToggle(); onActivity() }, enabled = state.track != null,
            modifier = Modifier.size(56.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = InkAccent, contentColor = GlassGround)) {
            Icon(if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (state.playing) "暂停" else "播放")
        }
        IconButton(onClick = { onNext(); onActivity() }, enabled = state.track != null) { Icon(Icons.Rounded.SkipNext, "下一首") }
    }
}
