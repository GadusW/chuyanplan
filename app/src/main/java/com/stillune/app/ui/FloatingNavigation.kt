package com.stillune.app.ui

import android.animation.ValueAnimator
import android.graphics.PathMeasure
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalDragOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

private val destinations = listOf("乐库", "此刻", "护耳")
private const val lastDestination = 2
private val railInset = 5.dp
private val railHeight = 62.dp

@Composable
internal fun FloatingNavigation(
    playerOpen: Boolean,
    onLibrary: () -> Unit,
    onPlayer: () -> Unit,
    healthOpen: Boolean = false,
    onHealth: () -> Unit = {},
    onOrigin: (Offset) -> Unit = {},
) {
    val selected = if (healthOpen) 2 else if (playerOpen) 1 else 0
    val scope = rememberCoroutineScope()
    val slider = remember(scope) { NavigationSlider(scope, selected) }
    val currentSelection by rememberUpdatedState(selected)
    val openPage by rememberUpdatedState<(Int, Offset) -> Unit> { destination, origin ->
        onOrigin(origin)
        when (destination) {
            0 -> onLibrary()
            1 -> onPlayer()
            2 -> onHealth()
        }
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val pressure = animateGlassPressure(slider.pressed)
    var origin by remember { mutableStateOf(Offset.Zero) }

    // The existing navigation can also change through Back, notifications or the mini player.
    LaunchedEffect(selected) {
        if (slider.target != selected) slider.settle(selected)
    }

    BoxWithConstraints(Modifier.padding(horizontal = 16.dp, vertical = railInset)
        .widthIn(max = 306.dp).fillMaxWidth().height(railHeight)) {
        val railWidth = maxWidth
        val cellWidth = (railWidth - railInset * 2) / destinations.size
        val density = LocalDensity.current
        val labelSize = with(density) {
            minOf(14f, ((cellWidth - 12.dp) / 2).coerceAtLeast(10.dp).toSp().value).sp
        }
        Box(Modifier.fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
            .selectableGroup().semantics { contentDescription = "悬浮导航" }
            .drawNavigation(slider, rtl) { pressure.value }
            .pointerInput(slider, rtl, cellWidth) {
                val step = cellWidth.toPx()
                val direction = if (rtl) -1f else 1f
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    slider.pressed = true
                    slider.beginDrag()
                    var dragging = false
                    try {
                        if (step <= 0f) return@awaitEachGesture
                        val velocity = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
                        val threshold = 4.dp.toPx()
                        var start = down
                        while (!dragging) {
                            val event = awaitPointerEvent()
                            start = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                            if (!start.pressed || start.isConsumed) return@awaitEachGesture
                            val distance = start.position - down.position
                            if (abs(distance.y) >= threshold && abs(distance.y) > abs(distance.x)) {
                                return@awaitEachGesture
                            }
                            if (abs(distance.x) >= threshold) {
                                dragging = true
                                start.consume()
                                slider.dragBy(distance.x * direction / step)
                            }
                        }
                        velocity.addPosition(start.uptimeMillis, start.position)
                        var lastMove = start.uptimeMillis
                        var end = awaitHorizontalDragOrCancellation(start.id)
                        while (end != null && !end.changedToUpIgnoreConsumed()) {
                            slider.dragBy(end.positionChange().x * direction / step)
                            velocity.addPosition(end.uptimeMillis, end.position)
                            lastMove = end.uptimeMillis
                            end.consume()
                            end = awaitHorizontalDragOrCancellation(start.id)
                        }
                        if (end == null) return@awaitEachGesture
                        val speed = if (end.uptimeMillis - lastMove < 70L) {
                            (velocity.calculateVelocity().x * direction / step).coerceIn(-5f, 5f)
                        } else 0f
                        val previous = currentSelection
                        val destination = (slider.position + speed * .06f).roundToInt().coerceIn(0, lastDestination)
                        slider.settle(destination, speed) {
                            if (currentSelection == previous && destination != previous) {
                                openPage(destination, origin + Offset(
                                    cellCenter(destination.toFloat(), size.width.toFloat(), railInset.toPx(), rtl),
                                    size.height / 2f,
                                ))
                            }
                        }
                        dragging = false
                    } finally {
                        slider.pressed = false
                        if (slider.target == null) slider.settle(currentSelection)
                    }
                }
            }) {
            Row(Modifier.fillMaxSize().padding(horizontal = railInset, vertical = railInset)) {
                destinations.forEachIndexed { index, name ->
                    Box(Modifier.weight(1f).fillMaxHeight().clip(SquircleShape(23.dp))
                        .semantics { contentDescription = name }
                        .selectable(selected == index, role = Role.Tab, interactionSource = null, indication = ripple()) {
                            val destination = if (index == selected) (selected + 1) % destinations.size else index
                            slider.settle(destination)
                            if (destination != selected) {
                                val tapCenter = with(density) {
                                    Offset(cellCenter(destination.toFloat(), railWidth.toPx(), railInset.toPx(), rtl), railHeight.toPx() / 2)
                                }
                                openPage(destination, origin + tapCenter)
                            }
                        }) {
                        Text(name, Modifier.align(Alignment.Center)
                            .graphicsLayer {
                                alpha = smooth(abs(index - slider.position) / .55f)
                            }
                            .then(if (selected == index) Modifier.clearAndSetSemantics {} else Modifier),
                            color = InkAccent, fontSize = labelSize, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

private class NavigationSlider(private val scope: CoroutineScope, selected: Int) {
    var position by mutableFloatStateOf(selected.toFloat())
        private set
    var pressed by mutableStateOf(false)
    var target: Int? = selected
        private set
    private var rawPosition = position
    private var settling: Job? = null

    fun beginDrag() {
        settling?.cancel()
        target = null
        rawPosition = position
    }

    fun dragBy(delta: Float) {
        rawPosition += delta
        val limit = rawPosition.coerceIn(0f, lastDestination.toFloat())
        val excess = rawPosition - limit
        position = limit + excess * .18f / (1f + abs(excess) * 2.35f)
    }

    fun settle(destination: Int, speed: Float = 0f, onArrived: () -> Unit = {}) {
        settling?.cancel()
        target = destination
        val end = destination.toFloat()
        if (!ValueAnimator.areAnimatorsEnabled() || position == end) {
            position = end
            onArrived()
            return
        }
        val distance = end - position
        val velocity = (speed * sign(distance)).coerceIn(0f, abs(distance) * 22f) * sign(distance)
        settling = scope.launch {
            AnimationState(initialValue = position, initialVelocity = velocity)
                .animateTo(end, spring(dampingRatio = 1f, stiffness = 100f, visibilityThreshold = .007f)) {
                    position = value
                }
            position = end
            onArrived()
        }
    }
}

private fun cellCenter(position: Float, width: Float, inset: Float, rtl: Boolean): Float {
    val step = (width - inset * 2) / destinations.size
    val center = inset + (position + .5f) * step
    return if (rtl) width - center else center
}

private fun smooth(amount: Float): Float {
    val t = amount.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private val blueLight = Color(0xFF71A8E6)
private val whiteLight = Color(0xFFF5F4EE)
private val goldLight = Color(0xFFEEB16A)

private fun Modifier.drawNavigation(slider: NavigationSlider, rtl: Boolean, pressure: () -> Float) = drawWithCache {
    val inset = railInset.toPx()
    val width = (size.width - inset * 2) / destinations.size
    val height = size.height - inset * 2
    if (width <= 0f || height <= 0f) return@drawWithCache onDrawWithContent { drawContent() }

    val trackRadius = 29.dp.toPx().coerceAtMost(size.width / 2)
    val corner = minOf(23.dp.toPx(), width / 2, height / 2)
    val track = Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(trackRadius))) }
    val knobBounds = Size(width, height)
    val contour = roundedContour(knobBounds, corner)
    val orbit = PathMeasure(contour.asAndroidPath(), true)
    val light = FloatArray(2)
    val railTone = Brush.verticalGradient(0f to Color(0x41658DA3), .32f to Color(0x36798FA3),
        .77f to Color(0x75BAD2E1), 1f to Color(0x9CEDF6FC), endY = size.height)
    val railDepth = Brush.verticalGradient(0f to Color(0x28315875), .17f to Color.Transparent,
        .85f to Color.Transparent, 1f to Color(0x80FFFFFF), endY = size.height)
    val face = Brush.linearGradient(0f to Color(0xE4FFFFFF), .3f to Color(0x9AF4FBFF),
        .73f to Color(0x84DFEEF8), 1f to Color(0xD6FAFDFF), end = Offset(width * .58f, height))
    val dotPitch = height * .6f / (14f * 5.6f + 4.3f) * 5.6f
    val outline = .85.dp.toPx()

    onDrawWithContent {
        val position = slider.position
        val progress = position.coerceIn(0f, lastDestination.toFloat())
        val center = cellCenter(position, size.width, inset, rtl)
        val left = center - width / 2f
        val tint = if (progress <= 1f) lerp(blueLight, whiteLight, progress) else lerp(whiteLight, goldLight, progress - 1f)
        orbit.getPosTan(orbit.length * ((.625f + smooth(progress / 2f) * .505f) % 1f), light, null)
        val glint = Offset(light[0], light[1])

        drawRoundRect(Color(0x12375E72), topLeft = Offset(0f, 1.dp.toPx()), cornerRadius = CornerRadius(trackRadius))
        clipPath(track) {
            drawRect(railTone)
            drawRect(railDepth)
            drawRect(Brush.radialGradient(listOf(tint.copy(alpha = .42f), Color.Transparent),
                center = Offset(left + glint.x, inset + glint.y), radius = width * .84f))
        }


        translate(left, inset) {
            val press = pressure()
            scale(1f - .027f * press, 1f - .05f * press, pivot = Offset(width / 2, height / 2)) {
                drawRoundRect(Color(0x22325475), topLeft = Offset(0f, 2.dp.toPx()), size = knobBounds, cornerRadius = CornerRadius(corner))
                clipPath(contour) {
                    drawRect(face, size = knobBounds)
                    drawRect(Brush.radialGradient(listOf(tint.copy(alpha = .36f), Color.Transparent),
                        center = glint, radius = width * .72f), size = knobBounds)
                    val edge = Brush.radialGradient(0f to Color.White.copy(alpha = .93f),
                        .33f to lerp(Color.White, tint, .35f).copy(alpha = .55f), .85f to Color.Transparent,
                        center = glint, radius = width * .66f)
                    drawPath(contour, edge, style = Stroke(outline * 2))
                }
                drawMorphingGlyph(progress, Offset(width / 2, height / 2), dotPitch, ValueAnimator.areAnimatorsEnabled())
            }
        }
        drawContent()
    }
}

// Path length starts at the top centre and runs clockwise, as in the approved preview.
private fun roundedContour(size: Size, radius: Float): Path = Path().apply {
    val (w, h) = size
    val diameter = radius * 2
    moveTo(w / 2, 0f)
    lineTo(w - radius, 0f)
    arcTo(Rect(w - diameter, 0f, w, diameter), -90f, 90f, false)
    lineTo(w, h - radius)
    arcTo(Rect(w - diameter, h - diameter, w, h), 0f, 90f, false)
    lineTo(radius, h)
    arcTo(Rect(0f, h - diameter, diameter, h), 90f, 90f, false)
    lineTo(0f, radius)
    arcTo(Rect(0f, 0f, diameter, diameter), 180f, 90f, false)
    close()
}

// These are the same three hand-placed 15 × 15 pixel masks used in the HTML preview.
private val glyphs = listOf(
    glyph(
        intArrayOf(), intArrayOf(), intArrayOf(6, 7, 8, 9, 10, 11, 12), intArrayOf(5, 6, 12, 13),
        intArrayOf(3, 5, 8, 9, 10, 13), intArrayOf(3, 5, 7, 11, 13), intArrayOf(3, 5, 7, 9, 11, 13),
        intArrayOf(3, 5, 7, 11, 13), intArrayOf(3, 5, 8, 9, 10, 13), intArrayOf(3, 5, 6, 12, 13),
        intArrayOf(3, 6, 7, 8, 9, 10, 11, 12), intArrayOf(3, 4), intArrayOf(4, 5, 6, 7, 8, 9, 10, 11),
        intArrayOf(), intArrayOf(),
    ),
    glyph(
        intArrayOf(), intArrayOf(), intArrayOf(5, 6, 7, 8, 9), intArrayOf(3, 4, 10, 11, 12), intArrayOf(3, 12, 13),
        intArrayOf(2, 6, 13), intArrayOf(2, 6, 7, 13), intArrayOf(2, 6, 7, 8, 9), intArrayOf(2, 6, 7, 8, 9, 10, 13),
        intArrayOf(2, 6, 7, 8, 13), intArrayOf(3, 6, 7, 13), intArrayOf(3, 4, 11, 12), intArrayOf(5, 6, 7, 8, 9, 10),
        intArrayOf(), intArrayOf(),
    ),
    glyph(
        intArrayOf(), intArrayOf(7, 8, 9, 10), intArrayOf(5, 6, 10, 11), intArrayOf(4, 5, 11, 12), intArrayOf(4, 7, 8, 9, 12),
        intArrayOf(3, 4, 6, 7, 10, 12), intArrayOf(3, 6, 10, 12), intArrayOf(3, 4, 6, 7, 11, 12), intArrayOf(4, 7, 8, 11),
        intArrayOf(4, 5, 11), intArrayOf(5, 6, 10, 11), intArrayOf(6, 10), intArrayOf(6, 7, 9, 10), intArrayOf(7, 8, 9), intArrayOf(),
    ),
)

private fun glyph(vararg rows: IntArray): List<Offset> {
    val cells = rows.flatMapIndexed { row, columns -> columns.map { Offset(it.toFloat(), row.toFloat()) } }
    val center = Offset((cells.minOf { it.x } + cells.maxOf { it.x }) / 2, (cells.minOf { it.y } + cells.maxOf { it.y }) / 2)
    return cells.map { it - center }.sortedWith(compareBy({ atan2(it.y, it.x) }, { it.getDistanceSquared() }))
}

private data class ParticleEnd(val position: Offset, val alpha: Float)
private data class Particle(val from: ParticleEnd, val to: ParticleEnd, val delay: Float, val spin: Float, val drift: Offset)

private val morphs = glyphs.zipWithNext { from, to ->
    val random = Random(from.size * 193 + to.size * 37)
    val count = maxOf(from.size, to.size)
    fun endpoint(glyph: List<Offset>, index: Int): ParticleEnd {
        val point = index * glyph.size / count
        val repeated = index > 0 && point == (index - 1) * glyph.size / count
        return ParticleEnd(glyph[point], if (repeated) 0f else 1f)
    }
    List(count) { index ->
        Particle(endpoint(from, index), endpoint(to, index), random.nextFloat() * .25f,
            1.1f * (.6f + random.nextFloat() * .8f),
            Offset((random.nextFloat() * 2f - 1f) * 2.2f, (random.nextFloat() * 2f - 1f) * 2.2f))
    }
}

private fun DrawScope.drawNavigationGlyph(index: Int, center: Offset, pitch: Float, color: Color) {
    glyphs[index].forEach { point -> dot(center + point * pitch, (4.3f / 5.6f) * pitch, color) }
}

private fun DrawScope.drawMorphingGlyph(position: Float, center: Offset, pitch: Float, animate: Boolean) {
    val nearest = position.roundToInt()
    if (position == nearest.toFloat()) {
        drawNavigationGlyph(nearest, center, pitch, InkAccent)
        return
    }
    val index = position.toInt()
    val progress = position - index
    val wave = if (animate) sin(Math.PI.toFloat() * progress) else 0f
    val diameter = (4.3f / 5.6f) * pitch * (1f - .15f * wave)
    morphs[index].forEach { particle ->
        val t = if (animate) smooth((progress - particle.delay) / (1f - particle.delay)) else progress
        val from = particle.from.position
        val offset = from + (particle.to.position - from) * t
        val angle = wave * particle.spin
        val rotated = Offset(offset.x * cos(angle) - offset.y * sin(angle), offset.x * sin(angle) + offset.y * cos(angle))
        val alpha = particle.from.alpha + (particle.to.alpha - particle.from.alpha) * t
        if (alpha > 0f) dot(center + (rotated + particle.drift * wave) * pitch, diameter, InkAccent.copy(alpha = alpha))
    }
}

private fun DrawScope.dot(center: Offset, diameter: Float, color: Color) {
    drawRect(color, topLeft = center - Offset(diameter / 2, diameter / 2), size = Size(diameter, diameter))
}
