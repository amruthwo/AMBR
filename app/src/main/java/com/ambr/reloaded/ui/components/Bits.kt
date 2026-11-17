package com.ambr.reloaded.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.video.videoFrameMillis
import androidx.compose.ui.platform.LocalContext
import com.ambr.reloaded.data.Video
import com.ambr.reloaded.ui.theme.Ambr

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Hides the system bars and keeps the screen on while in composition. */
@Composable
fun ImmersiveMode() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window ?: return@DisposableEffect onDispose {}
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

/** A frame grabbed from about one second into the video. */
@Composable
fun VideoThumb(
    video: Video?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    if (video == null) {
        Box(modifier)
        return
    }
    val request = remember(video.path, video.lastModified) {
        ImageRequest.Builder(context)
            .data(video.file)
            .videoFrameMillis(1000)
            .memoryCacheKey("thumb:${video.path}:${video.lastModified}")
            .build()
    }
    AsyncImage(model = request, contentDescription = null, contentScale = contentScale, modifier = modifier)
}

/**
 * Play/pause that morphs shape: a circle while paused, a squircle while playing,
 * and squishes when pressed (M3 Expressive shape morphing).
 */
@Composable
fun MorphPlayButton(
    playing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    container: Color = MaterialTheme.colorScheme.primary,
    content: Color = MaterialTheme.colorScheme.onPrimary,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateFloatAsState(
        targetValue = when { pressed -> 22f; playing -> 32f; else -> 50f },
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 500f), label = "corner",
    )
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, spring(0.4f, 700f), label = "scale")
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(corner.toInt()),
        color = container,
        contentColor = content,
        interactionSource = interaction,
        modifier = modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale },
    ) {
        Box(contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = playing,
                transitionSpec = {
                    (scaleIn(spring(0.5f, 600f)) + fadeIn()) togetherWith (scaleOut() + fadeOut())
                },
                label = "icon",
            ) { isPlaying ->
                Icon(
                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(size * 0.55f),
                )
            }
        }
    }
}

/** Rounded thumbnail tile with a plum placeholder underneath while the frame loads. */
@Composable
fun ThumbTile(video: Video?, modifier: Modifier = Modifier, corner: Dp = 18.dp) {
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(Ambr.Plum.copy(alpha = 0.55f)),
    ) {
        VideoThumb(video, Modifier.matchParentSize())
    }
}
