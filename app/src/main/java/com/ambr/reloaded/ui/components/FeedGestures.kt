package com.ambr.reloaded.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.positionChanged

enum class TapZone { Left, Center, Right }

/**
 * YouTube × Instagram controls for a video page:
 *
 * - tap anywhere → [onTap] (mute), after a short wait to rule out a double-tap
 * - double-tap left/right third → [onSeek]; keep tapping that side to add another step each time
 * - double-tap the middle → [onLike] at the tap position
 * - press and hold → [onHoldStart] … [onHoldEnd] (2× speed); vertical drags still scroll the feed
 */
suspend fun PointerInputScope.detectFeedGestures(
    onTap: () -> Unit,
    onSeek: (forward: Boolean) -> Unit,
    onLike: (Offset) -> Unit,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
) {
    // "Seek mode": after a side double-tap, single taps on that side keep seeking.
    var seekZone: TapZone? = null
    var seekUntil = 0L
    val seekWindow = 900L

    fun zoneOf(x: Float) = when {
        x < size.width * 0.33f -> TapZone.Left
        x > size.width * 0.67f -> TapZone.Right
        else -> TapZone.Center
    }

    awaitEachGesture {
        val down = awaitFirstDown()
        val zone = zoneOf(down.position.x)

        var timedOut = true
        val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            waitForUpOrCancellation().also { timedOut = false }
        }

        if (timedOut) {
            // Long press → fast-forward until every finger lifts. Swallow moves so the pager stays put.
            onHoldStart()
            try {
                do {
                    val event = awaitPointerEvent()
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                } while (event.changes.any { it.pressed })
            } finally {
                onHoldEnd()
            }
            seekZone = null
            return@awaitEachGesture
        }
        if (up == null) return@awaitEachGesture   // became a scroll; the pager has it
        up.consume()

        // Already in seek mode on this side: every single tap is another step, instantly.
        if (zone != TapZone.Center && zone == seekZone && up.uptimeMillis < seekUntil) {
            onSeek(zone == TapZone.Right)
            seekUntil = up.uptimeMillis + seekWindow
            return@awaitEachGesture
        }
        seekZone = null

        val second = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) { awaitFirstDown() }
        if (second == null) {
            onTap()
            return@awaitEachGesture
        }
        val secondUp = waitForUpOrCancellation() ?: return@awaitEachGesture
        secondUp.consume()
        when (zone) {
            TapZone.Center -> onLike(secondUp.position)
            else -> {
                onSeek(zone == TapZone.Right)
                seekZone = zone
                seekUntil = secondUp.uptimeMillis + seekWindow
            }
        }
    }
}

/**
 * Pinch to zoom (two fingers) and, once zoomed, one-finger pan. Runs in the Initial pass so it can
 * claim the gesture before taps or the feed's vertical swipe see it. Plain taps pass straight through.
 */
suspend fun PointerInputScope.detectZoomPan(
    isZoomed: () -> Boolean,
    onTransform: (pan: Offset, zoom: Float) -> Unit,
    onEnd: () -> Unit,
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var transforming = false
        var travel = Offset.Zero
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val fingers = event.changes.count { it.pressed }
            if (fingers >= 2) {
                transforming = true
            } else if (fingers == 1 && isZoomed() && !transforming) {
                travel += event.calculatePan()
                if (travel.getDistance() > viewConfiguration.touchSlop) transforming = true
            }
            if (transforming) {
                onTransform(event.calculatePan(), if (fingers >= 2) event.calculateZoom() else 1f)
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
        } while (event.changes.any { it.pressed })
        if (transforming) onEnd()
    }
}
