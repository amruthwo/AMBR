package com.ambr.reloaded.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ambr.reloaded.ui.theme.Ambr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

data class HeartBurstSpec(val id: Long, val at: Offset, val tilt: Float)

/** Instagram-style heart that pops where you double-tapped, with a spray of logo pixels. */
@Composable
fun BoxScope.HeartBurst(spec: HeartBurstSpec, onDone: () -> Unit) {
    val scale = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }
    val rise = remember { Animatable(0f) }
    val spray = remember { Animatable(0f) }
    LaunchedEffect(spec.id) {
        launch { scale.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = 420f)) }
        launch { spray.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
        delay(520)
        launch { rise.animateTo(-140f, tween(420)) }
        alpha.animateTo(0f, tween(420))
        onDone()
    }
    val heart = 104.dp
    val px = with(LocalDensity.current) { heart.toPx() }
    val particles = remember(spec.id) {
        val r = Random(spec.id)
        List(14) {
            Triple(r.nextFloat() * 2f * PI.toFloat(), 0.6f + r.nextFloat() * 0.8f, Ambr.Pixels[r.nextInt(Ambr.Pixels.size)])
        }
    }
    Canvas(Modifier.matchParentSize()) {
        val p = spray.value
        if (p >= 1f) return@Canvas
        val d = 1.dp.toPx()
        for ((angle, dist, color) in particles) {
            val reach = px * 1.1f * dist * p
            val s = (7f * (1f - p) + 2f) * d
            drawRect(
                color.copy(alpha = (1f - p)),
                topLeft = Offset(spec.at.x + cos(angle) * reach - s / 2, spec.at.y + sin(angle) * reach - s / 2),
                size = Size(s, s),
            )
        }
    }
    Box(
        Modifier
            .offset { IntOffset((spec.at.x - px / 2).roundToInt(), (spec.at.y - px / 2 + rise.value).roundToInt()) }
            .size(heart)
            .graphicsLayer {
                scaleX = scale.value; scaleY = scale.value
                rotationZ = spec.tilt
                this.alpha = alpha.value
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.linearGradient(listOf(Ambr.Glow, Ambr.Magenta, Ambr.Neon)),
                    blendMode = BlendMode.SrcAtop,
                )
            },
    ) {
        Icon(Icons.Rounded.Favorite, null, Modifier.matchParentSize())
    }
}

/**
 * Slowly drifting blobs of colour pulled from the user's video thumbnails, over AMBR night purple.
 * Falls back to the logo palette until thumbnails have been sampled.
 */
@Composable
fun AuroraBackground(colors: List<Int>, modifier: Modifier = Modifier) {
    val brand = listOf(Ambr.Magenta, Ambr.Grape, Ambr.Neon, Ambr.Berry)
    val picked = (colors.map { Color(it) } + brand).take(4)
    // Pull each sampled colour toward AMBR purple and darken it so text always reads.
    val tones: List<State<Color>> = picked.mapIndexed { i, c ->
        val toned = lerp(lerp(c, Ambr.Plum, 0.25f), Color.Black, 0.18f)
        animateColorAsState(toned, tween(1800), label = "aurora$i")
    }
    var nanos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { nanos = it - start }
    }
    Canvas(modifier.fillMaxSize()) {
        drawRect(Ambr.Night)
        val t = nanos / 1_000_000_000f
        val big = max(size.width, size.height)
        val paths = listOf(
            Triple(0.21f, 0.13f, 0f), Triple(0.17f, 0.23f, 1.7f),
            Triple(0.13f, 0.19f, 3.1f), Triple(0.25f, 0.11f, 4.4f),
        )
        tones.forEachIndexed { i, tone ->
            val (fx, fy, ph) = paths[i]
            val cx = size.width * (0.5f + 0.42f * sin(t * fx + ph))
            val cy = size.height * (0.32f + 0.3f * cos(t * fy + ph * 0.7f)) + i * size.height * 0.09f
            val r = big * (0.55f + 0.1f * sin(t * 0.3f + i))
            drawCircle(
                Brush.radialGradient(
                    listOf(tone.value.copy(alpha = 0.62f), tone.value.copy(alpha = 0.18f), Color.Transparent),
                    center = Offset(cx, cy), radius = r,
                ),
                radius = r, center = Offset(cx, cy),
            )
        }
        // Darken toward the bottom so the folder list stays legible.
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Ambr.Night.copy(alpha = 0.55f), Ambr.Night.copy(alpha = 0.9f))))
    }
}
