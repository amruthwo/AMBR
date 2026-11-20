package com.ambr.reloaded.ui.screens

import com.ambr.reloaded.util.str

import com.ambr.reloaded.R

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.ambr.reloaded.data.VideoScale
import com.ambr.reloaded.ui.AmbrViewModel
import com.ambr.reloaded.ui.components.ImmersiveMode
import com.ambr.reloaded.ui.components.MorphPlayButton
import com.ambr.reloaded.ui.components.SquigglySeekBar
import com.ambr.reloaded.ui.components.detectFeedGestures
import com.ambr.reloaded.ui.components.detectZoomPan
import com.ambr.reloaded.ui.components.findActivity
import com.ambr.reloaded.ui.components.icon
import com.ambr.reloaded.ui.components.label
import com.ambr.reloaded.ui.components.next
import com.ambr.reloaded.ui.components.videoSize
import com.ambr.reloaded.util.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SPEEDS = listOf(1f, 1.25f, 1.5f, 2f, 0.75f)

/**
 * Big Brain player: in-order, long-form playback with full controls. No looping; plays on
 * through the playlist and remembers where you stopped in every video.
 */
@Composable
fun LongPlayerScreen(vm: AmbrViewModel, state: AmbrViewModel.LongState, scale: VideoScale, holdSpeed: Float, seekStepSeconds: Int) {
    ImmersiveMode()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val playlist = state.playlist

    val player = remember(state.key) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            val start = playlist[state.startIndex]
            val resume = vm.progressOf(start.path)?.takeIf { !it.watched }?.positionMs ?: 0L
            setMediaItems(playlist.map { MediaItem.fromUri(it.uri) }, state.startIndex, resume)
            prepare()
            playWhenReady = true
        }
    }

    var index by remember { mutableIntStateOf(state.startIndex) }
    var isPlaying by remember { mutableStateOf(false) }
    var ended by remember { mutableStateOf(false) }
    var duration by remember { mutableLongStateOf(0L) }
    var position by remember { mutableLongStateOf(0L) }
    var aspect by remember { mutableFloatStateOf(0f) }
    var videoWidthPx by remember { mutableIntStateOf(0) }
    var controls by remember { mutableStateOf(true) }
    var muted by remember { mutableStateOf(false) }
    var speedIndex by remember { mutableIntStateOf(0) }
    var fast by remember { mutableStateOf(false) }
    var landscape by remember { mutableStateOf(false) }
    var flash by remember { mutableStateOf<String?>(null) }
    var flashKey by remember { mutableIntStateOf(0) }
    val video = playlist.getOrNull(index)

    fun save() {
        val v = playlist.getOrNull(player.currentMediaItemIndex) ?: return
        vm.saveProgress(v.path, player.currentPosition, player.duration)
    }
    fun close() { save(); vm.closeLong() }
    BackHandler { close() }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(p: Boolean) { isPlaying = p }
            override fun onVideoSizeChanged(s: VideoSize) {
                if (s.width > 0 && s.height > 0) {
                    aspect = s.width * s.pixelWidthHeightRatio / s.height
                    videoWidthPx = (s.width * s.pixelWidthHeightRatio).toInt()
                }
            }
            override fun onPlaybackStateChanged(st: Int) {
                if (st == Player.STATE_READY) duration = player.duration.coerceAtLeast(0)
                if (st == Player.STATE_ENDED) {
                    playlist.getOrNull(player.currentMediaItemIndex)?.let { vm.saveProgress(it.path, duration, duration) }
                    ended = true; controls = true
                }
            }
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                // The video we just left: finished if it auto-advanced, else remember the spot.
                playlist.getOrNull(index)?.let { prev ->
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) vm.saveProgress(prev.path, duration, duration)
                }
                index = player.currentMediaItemIndex
                ended = false
                duration = 0L
                // Resume a half-watched next episode where you left it.
                playlist.getOrNull(index)?.let { v ->
                    vm.progressOf(v.path)?.takeIf { !it.watched && it.positionMs > 5_000 }?.let { player.seekTo(it.positionMs) }
                }
            }
        }
        player.addListener(listener)
        onDispose {
            save()
            player.removeListener(listener)
            player.release()
        }
    }

    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    LaunchedEffect(lifecycle) { if (!lifecycle.isAtLeast(Lifecycle.State.RESUMED)) { player.pause(); save() } }
    LaunchedEffect(muted) { player.volume = if (muted) 0f else 1f }
    LaunchedEffect(fast, speedIndex) { player.setPlaybackSpeed(if (fast) holdSpeed else SPEEDS[speedIndex]) }
    LaunchedEffect(Unit) { while (true) { withFrameMillis { position = player.currentPosition } } }
    LaunchedEffect(Unit) { while (true) { delay(5_000); if (player.isPlaying) save() } }
    // Controls fade away while playing.
    LaunchedEffect(controls, isPlaying, flashKey) {
        if (controls && isPlaying) { delay(3_500); controls = false }
    }
    LaunchedEffect(flashKey) { if (flashKey > 0) { delay(900); flash = null } }

    // Rotate button: lock landscape for wide videos, back to normal when leaving.
    val activity = remember { context.findActivity() }
    LaunchedEffect(landscape) {
        activity?.requestedOrientation =
            if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    DisposableEffect(Unit) { onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED } }

    // Pinch zoom (same as the Brain Rot feed).
    val zoom = remember { Animatable(1f) }
    val panX = remember { Animatable(0f) }
    val panY = remember { Animatable(0f) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val stepMs by rememberUpdatedState(seekStepSeconds * 1000L)

    fun seekBy(ms: Long) {
        val target = (player.currentPosition + ms).coerceIn(0, player.duration.coerceAtLeast(0))
        player.seekTo(target); position = target
    }
    fun showFlash(text: String) { flash = text; flashKey++ }

    Box(Modifier.fillMaxSize().background(Color.Black).clipToBounds()) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .onSizeChanged { boxSize = it }
                .graphicsLayer { scaleX = zoom.value; scaleY = zoom.value; translationX = panX.value; translationY = panY.value },
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { TextureView(it).also(player::setVideoTextureView) },
                onRelease = { player.clearVideoTextureView(it) },
                modifier = videoSize(scale, aspect, videoWidthPx),
            )
        }

        // Gestures: tap = controls, double-tap sides = seek, middle = play/pause, hold = fast, pinch = zoom.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectZoomPan(
                        isZoomed = { zoom.value > 1.01f },
                        onTransform = { pan, z ->
                            scope.launch {
                                val nz = (zoom.value * z).coerceIn(0.85f, 6f)
                                zoom.snapTo(nz)
                                val mx = (boxSize.width * (nz - 1f) / 2f).coerceAtLeast(0f)
                                val my = (boxSize.height * (nz - 1f) / 2f).coerceAtLeast(0f)
                                panX.snapTo((panX.value + pan.x).coerceIn(-mx, mx))
                                panY.snapTo((panY.value + pan.y).coerceIn(-my, my))
                            }
                        },
                        onEnd = {
                            if (zoom.value < 1.05f) scope.launch {
                                launch { zoom.animateTo(1f, spring(0.7f, 400f)) }
                                launch { panX.animateTo(0f) }
                                panY.animateTo(0f)
                            }
                        },
                    )
                }
                .pointerInput(player) {
                    detectFeedGestures(
                        onTap = { controls = !controls },
                        onSeek = { forward ->
                            seekBy(if (forward) stepMs else -stepMs)
                            showFlash(if (forward) "+${stepMs / 1000}s" else "−${stepMs / 1000}s")
                        },
                        onLike = { if (player.isPlaying) player.pause() else player.play() },
                        onHoldStart = { fast = true; haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                        onHoldEnd = { fast = false },
                    )
                },
        )

        // Quick feedback bubble (seek amount, speed, scale).
        val f = flash ?: if (fast) "${holdSpeed}× ▶▶" else null
        AnimatedVisibility(f != null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter).padding(top = 72.dp)) {
            Surface(shape = RoundedCornerShape(50), color = Color.Black.copy(alpha = 0.6f), contentColor = Color.White) {
                Text(f.orEmpty(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }

        AnimatedVisibility(controls, enter = fadeIn(tween(150)), exit = fadeOut(tween(300)), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                // Top bar.
                Row(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)))
                        .displayCutoutPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { close() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, str(R.string.cram_back), tint = Color.White) }
                    Column(Modifier.weight(1f)) {
                        Text(video?.title.orEmpty(), color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(str(R.string.long_player_x_x_of_x, state.title, index + 1, playlist.size), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                    TextButton(onClick = {
                        speedIndex = (speedIndex + 1) % SPEEDS.size
                        showFlash(str(R.string.long_player_speed_x, SPEEDS[speedIndex]))
                    }) { Text("${SPEEDS[speedIndex]}×", color = Color.White) }
                    IconButton(onClick = {
                        val n = scale.next(); vm.setScaleBigBrain(n); showFlash(str(R.string.long_player_video_size_x, n.label))
                    }) { Icon(scale.icon, str(R.string.long_player_video_size_x, scale.label), tint = Color.White) }
                    IconButton(onClick = { landscape = !landscape }) { Icon(Icons.Rounded.ScreenRotation, str(R.string.long_player_rotate), tint = Color.White) }
                    IconButton(onClick = { muted = !muted }) {
                        Icon(if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, if (muted) str(R.string.video_unmute) else str(R.string.video_mute), tint = Color.White)
                    }
                }

                // Transport controls: above the seek bar in portrait, sharing its row in landscape.
                val wide = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
                val tonal = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.Black.copy(alpha = 0.45f), contentColor = Color.White)
                val transport: @Composable (Boolean) -> Unit = { compact ->
                    val btn = Modifier.size(if (compact) 40.dp else 48.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalIconButton(onClick = { if (player.currentPosition > 5_000 || !player.hasPreviousMediaItem()) player.seekTo(0) else player.seekToPreviousMediaItem() }, colors = tonal, modifier = btn) {
                            Icon(Icons.Rounded.SkipPrevious, str(R.string.long_player_previous))
                        }
                        FilledTonalIconButton(onClick = { seekBy(-stepMs) }, colors = tonal, modifier = btn) { Icon(Icons.Rounded.Replay10, str(R.string.long_player_back_x_s, seekStepSeconds)) }
                        MorphPlayButton(playing = isPlaying, onClick = {
                            if (ended) { player.seekTo(0); player.play() } else if (player.isPlaying) player.pause() else player.play()
                        }, size = if (compact) 48.dp else 64.dp)
                        FilledTonalIconButton(onClick = { seekBy(stepMs) }, colors = tonal, modifier = btn) { Icon(Icons.Rounded.Forward10, str(R.string.long_player_forward_x_s, seekStepSeconds)) }
                        FilledTonalIconButton(onClick = { if (player.hasNextMediaItem()) player.seekToNextMediaItem() }, colors = tonal, modifier = btn, enabled = player.hasNextMediaItem() || index < playlist.lastIndex) {
                            Icon(Icons.Rounded.SkipNext, str(R.string.long_player_next))
                        }
                    }
                }
                val seekBar: @Composable (Modifier) -> Unit = { m ->
                    SquigglySeekBar(
                        position = { position },
                        duration = duration,
                        playing = isPlaying,
                        onSeek = { player.seekTo(it); position = it },
                        modifier = m,
                    )
                }
                val elapsed = formatDuration(position)
                val remaining = "−" + formatDuration((duration - position).coerceAtLeast(0))

                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                        .displayCutoutPadding()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 32.dp),
                ) {
                    if (ended && index >= playlist.lastIndex) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(str(R.string.long_player_that_s_everything_in_x, state.title), color = Color.White, modifier = Modifier.weight(1f))
                            OutlinedButton(onClick = { close() }) { Text(str(R.string.cram_done), color = Color.White) }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    if (wide) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            transport(true)
                            Spacer(Modifier.width(12.dp))
                            Text(elapsed, color = Color.White, style = MaterialTheme.typography.labelMedium)
                            seekBar(Modifier.weight(1f).padding(horizontal = 10.dp))
                            Text(remaining, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                        }
                    } else {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { transport(false) }
                        Spacer(Modifier.height(10.dp))
                        seekBar(Modifier.fillMaxWidth())
                        Row {
                            Text(elapsed, color = Color.White, style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.weight(1f))
                            Text(remaining, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

