package com.ambr.reloaded.ui.components

import com.ambr.reloaded.util.str

import com.ambr.reloaded.R

import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.CropOriginal
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import com.ambr.reloaded.data.VideoScale

/**
 * Size for the video surface inside a [BoxWithConstraintsScope] that fills the screen.
 * [aspect] is width/height of the video (0 = unknown yet), [videoWidthPx] its pixel width.
 */
@Composable
fun BoxWithConstraintsScope.videoSize(scale: VideoScale, aspect: Float, videoWidthPx: Int): Modifier {
    if (aspect <= 0f) return Modifier.fillMaxSize()
    val boxAspect = if (maxHeight.value > 0f) maxWidth.value / maxHeight.value else 1f
    val unbounded = Modifier.fillMaxSize().wrapContentSize(unbounded = true)
    return when (scale) {
        // Full width always; near-screen-shaped videos fill the screen (a sliver cropped top/bottom).
        VideoScale.Auto -> if (aspect < boxAspect && aspect / boxAspect >= 0.8f) unbounded.size(maxWidth, maxWidth / aspect)
        else Modifier.aspectRatio(aspect)
        VideoScale.Fit -> Modifier.aspectRatio(aspect)
        VideoScale.Fill -> if (aspect > boxAspect) unbounded.size(maxHeight * aspect, maxHeight)
        else unbounded.size(maxWidth, maxWidth / aspect)
        VideoScale.Stretch -> Modifier.fillMaxSize()
        VideoScale.Original -> {
            val w = with(LocalDensity.current) { videoWidthPx.toDp() }
            unbounded.size(w, w / aspect)
        }
    }
}

fun VideoScale.next(): VideoScale = VideoScale.entries[(ordinal + 1) % VideoScale.entries.size]

val VideoScale.icon: ImageVector
    get() = when (this) {
        VideoScale.Auto -> Icons.Rounded.AutoAwesome
        VideoScale.Fit -> Icons.Rounded.FitScreen
        VideoScale.Fill -> Icons.Rounded.Crop
        VideoScale.Stretch -> Icons.Rounded.OpenInFull
        VideoScale.Original -> Icons.Rounded.CropOriginal
    }

val VideoScale.label: String
    get() = when (this) {
        VideoScale.Auto -> str(R.string.video_scaling_auto)
        VideoScale.Fit -> str(R.string.video_scaling_fit)
        VideoScale.Fill -> str(R.string.video_scaling_fill_crop)
        VideoScale.Stretch -> str(R.string.video_scaling_stretch)
        VideoScale.Original -> str(R.string.video_scaling_original_size)
    }
