package com.ambr.reloaded.ui.screens

import android.os.Build
import android.view.TextureView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.window.layout.FoldingFeature
import com.ambr.reloaded.data.Video
import com.ambr.reloaded.ui.components.HeartBurst
import com.ambr.reloaded.ui.components.HeartBurstSpec
import com.ambr.reloaded.ui.components.MorphPlayButton
import com.ambr.reloaded.ui.components.SquigglySeekBar
import com.ambr.reloaded.ui.components.VideoThumb
import com.ambr.reloaded.ui.components.detectFeedGestures
import com.ambr.reloaded.ui.components.detectZoomPan
import com.ambr.reloaded.ui.theme.Ambr
import com.ambr.reloaded.util.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class VideoAction { Favorite, Share, Rename, Move, Cram, Delete }

/**
 * One full-screen video in the feed. Owns its own player; only plays while [selected].
 *
 * Gestures: tap = mute, double-tap sides = seek (keep tapping to stack), double-tap middle = like,
 * hold = fast playback.
 */
@Composable
fun VideoPage(
    video: Video,
    selected: Boolean,
    suspended: Boolean,
    muted: Boolean,
    favorite: Boolean,
    holdSpeed: Float,
    seekStepSeconds: Int,
    tabletop: FoldingFeature?,
    onToggleMute: () -> Unit,
    onLike: () -> Unit,
    onAction: (VideoAction) -> Unit,
    onMenuOpenChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val player = remember(video.path) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            setMediaItem(MediaItem.fromUri(video.uri))
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }

    var userPaused by remember(video.path) { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var duration by remember { mutableLongStateOf(0L) }
    var position by remember { mutableLongStateOf(0L) }
    var aspect by remember { mutableFloatStateOf(0f) }
    var firstFrame by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableStateOf<Float?>(null) }
    var fast by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    // Pinch-zoom for videos with baked-in black bars.
    val zoom = remember { Animatable(1f) }
    val panX = remember { Animatable(0f) }
    val panY = remember { Animatable(0f) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()
    fun resetZoom() = scope.launch {
        launch { zoom.animateTo(1f, spring(0.7f, 400f)) }
        launch { panX.animateTo(0f, spring(0.7f, 400f)) }
        panY.animateTo(0f, spring(0.7f, 400f))
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    aspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                }
            }
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) duration = player.duration.coerceAtLeast(0)
            }
            override fun onRenderedFirstFrame() { firstFrame = true }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val shouldPlay = selected && (!userPaused || fast) && !suspended && !menuOpen &&
        lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(shouldPlay) { player.playWhenReady = shouldPlay }
    LaunchedEffect(selected) {
        if (!selected) {
            userPaused = false; fast = false; menuOpen = false
            zoom.snapTo(1f); panX.snapTo(0f); panY.snapTo(0f)
        }
    }
    LaunchedEffect(muted) { player.volume = if (muted) 0f else 1f }
    LaunchedEffect(fast, holdSpeed) { player.setPlaybackSpeed(if (fast) holdSpeed else 1f) }
    LaunchedEffect(menuOpen) { onMenuOpenChange(menuOpen) }
    LaunchedEffect(selected) {
        while (selected) {
            withFrameMillis { position = player.currentPosition }
        }
    }

    // Transient feedback overlays.
    var muteFlash by remember { mutableIntStateOf(0) }
    var seekSide by remember { mutableIntStateOf(0) }       // -1 left, +1 right
    var seekTotal by remember { mutableIntStateOf(0) }      // seconds stacked this burst
    var seekFlash by remember { mutableIntStateOf(0) }
    val hearts = remember { mutableStateListOf<HeartBurstSpec>() }

    val currentMute by rememberUpdatedState(onToggleMute)
    val currentLike by rememberUpdatedState(onLike)
    val stepMs by rememberUpdatedState(seekStepSeconds * 1000L)
    val step by rememberUpdatedState(seekStepSeconds)

    val videoLayer: @Composable BoxScope.() -> Unit = {
        // Blurred, dimmed copy of the frame fills any bars around the video.
        VideoThumb(
            video,
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = 0.45f }
                .then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(40.dp) else Modifier),
        )
        BoxWithConstraints(
            Modifier
                .matchParentSize()
                .onSizeChanged { boxSize = it }
                .graphicsLayer {
                    scaleX = zoom.value; scaleY = zoom.value
                    translationX = panX.value; translationY = panY.value
                },
            contentAlignment = Alignment.Center,
        ) {
            // Always show the full width. A video only slightly taller than the screen fills it
            // (losing a sliver top and bottom); anything else is fitted with blurred bars.
            val boxAspect = if (maxHeight.value > 0f) maxWidth.value / maxHeight.value else 1f
            val sized = when {
                aspect <= 0f -> Modifier.fillMaxSize()
                aspect < boxAspect && aspect / boxAspect >= 0.8f -> Modifier
                    .fillMaxSize()
                    .wrapContentSize(unbounded = true)
                    .size(maxWidth, maxWidth / aspect)
                else -> Modifier.aspectRatio(aspect)
            }
            AndroidView(
                factory = { TextureView(it).also(player::setVideoTextureView) },
                onRelease = { player.clearVideoTextureView(it) },
                modifier = sized,
            )
            AnimatedVisibility(!firstFrame, enter = fadeIn(), exit = fadeOut(tween(200)), modifier = sized) {
                VideoThumb(video, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
        }
        // Gesture surface.
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    detectZoomPan(
                        isZoomed = { zoom.value > 1.01f },
                        onTransform = { pan, z ->
                            scope.launch {
                                val newZoom = (zoom.value * z).coerceIn(0.85f, 6f)
                                zoom.snapTo(newZoom)
                                // Keep the zoomed video covering the screen: clamp the pan.
                                val maxX = (boxSize.width * (newZoom - 1f) / 2f).coerceAtLeast(0f)
                                val maxY = (boxSize.height * (newZoom - 1f) / 2f).coerceAtLeast(0f)
                                panX.snapTo((panX.value + pan.x).coerceIn(-maxX, maxX))
                                panY.snapTo((panY.value + pan.y).coerceIn(-maxY, maxY))
                            }
                        },
                        onEnd = { if (zoom.value < 1.05f) resetZoom() },
                    )
                }
                .pointerInput(player) {
                    detectFeedGestures(
                        onTap = {
                            currentMute()
                            muteFlash++
                        },
                        onSeek = { forward ->
                            val target = if (forward) {
                                (player.currentPosition + stepMs).coerceAtMost(player.duration.coerceAtLeast(0))
                            } else {
                                (player.currentPosition - stepMs).coerceAtLeast(0)
                            }
                            player.seekTo(target)
                            position = target
                            val side = if (forward) 1 else -1
                            seekTotal = if (side == seekSide && seekTotal > 0) seekTotal + step else step
                            seekSide = side
                            seekFlash++
                        },
                        onLike = { at ->
                            currentLike()
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            hearts += HeartBurstSpec(System.nanoTime(), at, Random.nextInt(-18, 18).toFloat())
                        },
                        onHoldStart = {
                            fast = true
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onHoldEnd = { fast = false },
                    )
                },
        )
        SeekFlash(seekSide, seekTotal, seekFlash, onFinished = { seekTotal = 0 })
        MuteFlash(muted, muteFlash, Modifier.align(Alignment.Center))
        for (h in hearts.toList()) key(h.id) { HeartBurst(h) { hearts.remove(h) } }
        ZoomChip(zoom.value, onReset = { resetZoom() }, modifier = Modifier.align(Alignment.TopEnd).displayCutoutPadding().padding(top = 64.dp, end = 12.dp))
        FastPill(fast, holdSpeed, Modifier.align(Alignment.TopCenter).displayCutoutPadding().padding(top = 64.dp))
    }

    val controls: @Composable (Modifier) -> Unit = { mod ->
        Controls(
            video = video,
            playing = (isPlaying || (shouldPlay && !firstFrame)) && !userPaused,
            duration = duration,
            position = { position },
            scrubFraction = scrubFraction,
            muted = muted,
            favorite = favorite,
            menuOpen = menuOpen,
            onMenuOpen = { menuOpen = it },
            onScrub = { scrubFraction = it },
            onSeek = { player.seekTo(it); position = it },
            onPlayPause = { userPaused = !userPaused },
            onToggleMute = { onToggleMute(); muteFlash++ },
            onAction = { menuOpen = false; onAction(it) },
            modifier = mod,
        )
    }

    val hingeTop = tabletop?.bounds?.top
    if (hingeTop != null && hingeTop > 0) {
        // Tabletop posture: video above the fold, controls below it.
        val density = LocalDensity.current
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(with(density) { hingeTop.toDp() })
                    .clipToBounds(),
            ) { videoLayer() }
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                contentAlignment = Alignment.Center,
            ) {
                controls(Modifier.padding(16.dp))
            }
        }
    } else {
        Box(Modifier.fillMaxSize().background(Color.Black).clipToBounds()) {
            videoLayer()
            // Bottom scrim so white text always reads.
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(240.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)))),
            )
            controls(
                Modifier
                    .align(Alignment.BottomCenter)
                    .displayCutoutPadding()
                    .padding(start = 16.dp, end = 12.dp, bottom = 20.dp),
            )
        }
    }
}

@Composable
private fun Controls(
    video: Video,
    playing: Boolean,
    duration: Long,
    position: () -> Long,
    scrubFraction: Float?,
    muted: Boolean,
    favorite: Boolean,
    menuOpen: Boolean,
    onMenuOpen: (Boolean) -> Unit,
    onScrub: (Float?) -> Unit,
    onSeek: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onToggleMute: () -> Unit,
    onAction: (VideoAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val seconds by remember { derivedStateOf { position() / 1000 } }
    val shownMs = scrubFraction?.let { (it * duration).toLong() } ?: (seconds * 1000)
    Column(modifier.fillMaxWidth()) {
        // Title row: name (+ heart if favourite) on the left, mute | ▾ split button on the right.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                AnimatedVisibility(favorite, enter = scaleIn(spring(0.4f, 500f)), exit = scaleOut()) {
                    Icon(
                        Icons.Rounded.Favorite, "Favorite",
                        tint = Ambr.Glow,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(20.dp)
                            .clickable { onAction(VideoAction.Favorite) },
                    )
                }
                Text(
                    video.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            VideoSplitButton(muted, favorite, menuOpen, onMenuOpen, onToggleMute, onAction)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            MorphPlayButton(playing = playing, onClick = onPlayPause, size = 48.dp)
            Spacer(Modifier.width(12.dp))
            SquigglySeekBar(
                position = position,
                duration = duration,
                playing = playing,
                onSeek = onSeek,
                onScrub = onScrub,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "${formatDuration(shownMs)} / ${formatDuration(duration)}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = if (scrubFraction != null) 1f else 0.85f),
            )
        }
    }
}

/** M3 Expressive split button: primary half toggles mute, the chevron half opens video actions. */
@Composable
private fun VideoSplitButton(
    muted: Boolean,
    favorite: Boolean,
    menuOpen: Boolean,
    onMenuOpen: (Boolean) -> Unit,
    onToggleMute: () -> Unit,
    onAction: (VideoAction) -> Unit,
) {
    val colors = ButtonDefaults.filledTonalButtonColors(
        containerColor = Color.White.copy(alpha = 0.16f),
        contentColor = Color.White,
    )
    Box {
        SplitButtonLayout(
            leadingButton = {
                SplitButtonDefaults.LeadingButton(onClick = onToggleMute, colors = colors) {
                    Icon(
                        if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = if (muted) "Unmute" else "Mute",
                        modifier = Modifier.size(SplitButtonDefaults.LeadingIconSize),
                    )
                }
            },
            trailingButton = {
                SplitButtonDefaults.TrailingButton(
                    checked = menuOpen,
                    onCheckedChange = onMenuOpen,
                    colors = colors,
                ) {
                    val rotation by animateFloatAsState(if (menuOpen) 180f else 0f, spring(0.5f, 500f), label = "chev")
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Video options",
                        modifier = Modifier
                            .size(SplitButtonDefaults.TrailingIconSize)
                            .graphicsLayer { rotationZ = rotation },
                    )
                }
            },
        )
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { onMenuOpen(false) },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            MenuRow(
                if (favorite) "Unfavorite" else "Favorite",
                if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            ) { onAction(VideoAction.Favorite) }
            MenuRow("Share", Icons.Rounded.Share) { onAction(VideoAction.Share) }
            HorizontalDivider(Modifier.padding(vertical = 4.dp, horizontal = 12.dp))
            MenuRow("Rename", Icons.Rounded.DriveFileRenameOutline) { onAction(VideoAction.Rename) }
            MenuRow("Move to…", Icons.AutoMirrored.Rounded.DriveFileMove) { onAction(VideoAction.Move) }
            MenuRow("Cram (shrink)", Icons.Rounded.Compress) { onAction(VideoAction.Cram) }
            MenuRow("Delete", Icons.Rounded.DeleteOutline, danger = true) { onAction(VideoAction.Delete) }
        }
    }
}

@Composable
private fun MenuRow(label: String, icon: ImageVector, danger: Boolean = false, onClick: () -> Unit) {
    val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    DropdownMenuItem(
        text = { Text(label, color = tint) },
        leadingIcon = { Icon(icon, null, tint = tint) },
        onClick = onClick,
    )
}

/** “2×  ▶▶” pill while holding. The chevrons ripple to show speed. */
@Composable
private fun FastPill(visible: Boolean, speed: Float, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier,
    ) {
        val t by rememberInfiniteTransition(label = "ff").animateFloat(
            0f, 3f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "t",
        )
        Surface(shape = RoundedCornerShape(50), color = Color.Black.copy(alpha = 0.55f), contentColor = Color.White) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                val label = if (speed % 1f == 0f) "${speed.toInt()}×" else "$speed×"
                Text(label, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(6.dp))
                repeat(3) { i ->
                    val a = (1f - ((t - i).let { if (it < 0) it + 3 else it } / 3f)).coerceIn(0.25f, 1f)
                    Icon(
                        Icons.Rounded.FastForward, null,
                        modifier = Modifier
                            .size(16.dp)
                            .graphicsLayer { alpha = a },
                    )
                }
            }
        }
    }
}

/** Shows the zoom level while zoomed; tap to snap back to 1×. */
@Composable
private fun ZoomChip(zoom: Float, onReset: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(zoom > 1.05f, enter = scaleIn(spring(0.5f, 500f)) + fadeIn(), exit = scaleOut() + fadeOut(), modifier = modifier) {
        Surface(
            onClick = onReset,
            shape = RoundedCornerShape(50),
            color = Color.Black.copy(alpha = 0.55f),
            contentColor = Color.White,
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ZoomOutMap, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("%.1f×  ·  1×".format(zoom), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Big speaker bubble that pops in the centre on tap. */
@Composable
private fun MuteFlash(muted: Boolean, trigger: Int, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        visible = true
        delay(650)
        visible = false
    }
    AnimatedVisibility(
        visible,
        enter = scaleIn(spring(0.45f, 500f), initialScale = 0.5f) + fadeIn(),
        exit = scaleOut(targetScale = 1.2f) + fadeOut(),
        modifier = modifier,
    ) {
        Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.5f), contentColor = Color.White) {
            Icon(
                if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                null,
                Modifier
                    .padding(22.dp)
                    .size(40.dp),
            )
        }
    }
}

/** Half-pill ripple on the side you tapped, showing the stacked total (10s, 20s, 30s…). */
@Composable
private fun BoxScope.SeekFlash(side: Int, total: Int, trigger: Int, onFinished: () -> Unit) {
    val alpha = remember { Animatable(0f) }
    val bump = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        alpha.snapTo(1f)
        bump.snapTo(1.25f)
        bump.animateTo(1f, spring(0.4f, 600f))
        delay(700)
        alpha.animateTo(0f, tween(300))
        onFinished()
    }
    if (side == 0) return
    val forward = side > 0
    Box(
        Modifier
            .align(if (forward) Alignment.CenterEnd else Alignment.CenterStart)
            .fillMaxHeight(0.6f)
            .fillMaxWidth(0.38f)
            .graphicsLayer { this.alpha = alpha.value }
            .background(
                Color.White.copy(alpha = 0.14f),
                if (forward) RoundedCornerShape(topStartPercent = 100, bottomStartPercent = 100)
                else RoundedCornerShape(topEndPercent = 100, bottomEndPercent = 100),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.graphicsLayer { scaleX = bump.value; scaleY = bump.value },
        ) {
            Icon(
                if (forward) Icons.Rounded.FastForward else Icons.Rounded.FastRewind,
                null, tint = Color.White, modifier = Modifier.size(36.dp),
            )
            Text(
                "${if (forward) "+" else "−"}${total}s",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}
