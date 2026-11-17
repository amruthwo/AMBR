package com.ambr.reloaded.ui.screens

import android.os.Environment
import android.os.StatFs
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ambr.reloaded.data.VideoFolder
import com.ambr.reloaded.ui.AmbrViewModel
import com.ambr.reloaded.ui.components.ThumbTile
import com.ambr.reloaded.util.formatBytes
import com.ambr.reloaded.util.pluralVideos

/** "Brain scan": how much brain rot you've hoarded, as numbers and pictures. */
@Composable
fun InfoScreen(vm: AmbrViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val folders by vm.folders.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val unlocked by vm.unlocked.collectAsStateWithLifecycle()
    LaunchedEffect(folders.size, unlocked) { vm.computeStats() }

    val real = folders.filter { !it.isVirtual }
    val videos = real.flatMap { it.videos }
    val totalBytes = videos.sumOf { it.sizeBytes }
    val totalMs = videos.sumOf { stats.durations[it.path] ?: 0L }
    val storage = remember {
        val s = StatFs(Environment.getExternalStorageDirectory().path)
        s.totalBytes to s.availableBytes
    }

    // Grow-in animation for every chart on the page.
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Brain scan") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            // Hero numbers.
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Videos", "${videos.size}", "in ${real.size} folders", Modifier.weight(1f))
                    StatTile("Space used", formatBytes(totalBytes), "on this phone", Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        "Watch time",
                        if (stats.done) formatHours(totalMs) else "…",
                        if (stats.done) "to watch everything once" else "measuring",
                        Modifier.weight(1f),
                        progress = if (stats.done) null else stats.progress,
                    )
                    StatTile(
                        "Average",
                        if (videos.isEmpty()) "–" else formatBytes(totalBytes / videos.size),
                        if (stats.done && videos.isNotEmpty()) "${formatClock(totalMs / videos.size)} per video" else "per video",
                        Modifier.weight(1f),
                    )
                }
            }
            if (!unlocked) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                        Icon(Icons.Rounded.Lock, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Hidden folders aren't counted while locked.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Space by folder: sorted bars, one hue, values labelled directly.
            item { SectionCard("Space by folder") { FolderBars(real, grow.value) } }

            // Phone storage: one segmented bar.
            item {
                SectionCard("Phone storage") {
                    StorageBar(totalBytes, storage.first, storage.second, grow.value)
                }
            }

            // Biggest videos.
            val biggest = videos.sortedByDescending { it.sizeBytes }.take(5)
            if (biggest.isNotEmpty()) {
                item {
                    SectionCard("Biggest videos") {
                        biggest.forEach { v ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                                ThumbTile(v, Modifier.size(width = 40.dp, height = 52.dp), corner = 10.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(v.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        v.file.parentFile?.name.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(formatBytes(v.sizeBytes), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, sub: String, modifier: Modifier = Modifier, progress: Float? = null) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = modifier,
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            if (progress != null) {
                LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
            } else {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

/** Top 6 folders by size + "Other", longest first. Bars share one scale (the biggest folder). */
@Composable
private fun FolderBars(folders: List<VideoFolder>, grow: Float) {
    val sorted = folders.filter { it.totalBytes > 0 }.sortedByDescending { it.totalBytes }
    if (sorted.isEmpty()) {
        Text("Nothing here yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val rows = if (sorted.size > 7) {
        val rest = sorted.drop(6)
        sorted.take(6).map { Triple(it.name, it.totalBytes, it.count) } +
            Triple("Other (${rest.size} folders)", rest.sumOf { it.totalBytes }, rest.sumOf { it.count })
    } else sorted.map { Triple(it.name, it.totalBytes, it.count) }
    val max = rows.maxOf { it.second }.toFloat()
    val bar = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { (name, bytes, count) ->
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(
                        "${formatBytes(bytes)} · ${pluralVideos(count)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Canvas(Modifier.fillMaxWidth().height(10.dp)) {
                    val r = CornerRadius(size.height / 2)
                    drawRoundRect(track, cornerRadius = r)
                    val w = (size.width * (bytes / max) * grow).coerceAtLeast(size.height)
                    drawRoundRect(bar, size = Size(w, size.height), cornerRadius = r)
                }
            }
        }
    }
}

/** AMBR | other stuff | free, with 2dp gaps between segments and a legend underneath. */
@Composable
private fun StorageBar(ambr: Long, total: Long, free: Long, grow: Float) {
    val other = (total - free - ambr).coerceAtLeast(0)
    val ambrColor = MaterialTheme.colorScheme.primary
    val otherColor = Color(0xFF8E7396)
    val freeColor = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(Modifier.fillMaxWidth().height(28.dp)) {
        val gap = 2.dp.toPx()
        val r = CornerRadius(8.dp.toPx())
        val parts = listOf(ambr to ambrColor, other to otherColor, free to freeColor)
        val usable = size.width - gap * 2
        var x = 0f
        parts.forEachIndexed { i, (bytes, color) ->
            val target = usable * bytes / total.toFloat()
            val w = if (i == 2) size.width - x else (target * grow).coerceAtLeast(if (bytes > 0) 4.dp.toPx() else 0f)
            if (w > 0f) drawRoundRect(color, topLeft = Offset(x, 0f), size = Size(w, size.height), cornerRadius = r)
            x += w + gap
        }
    }
    Spacer(Modifier.height(12.dp))
    Legend(ambrColor, "AMBR", formatBytes(ambr), "%.1f%%".format(ambr * 100.0 / total))
    Legend(otherColor, "Everything else", formatBytes(other), null)
    Legend(freeColor, "Free", formatBytes(free), null)
}

@Composable
private fun Legend(color: Color, label: String, value: String, extra: String?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
        Box(Modifier.size(12.dp).background(color, RoundedCornerShape(4.dp)))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (extra != null) {
            Text(extra, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
        }
        Text(value, style = MaterialTheme.typography.labelLarge)
    }
}

private fun formatHours(ms: Long): String {
    val min = ms / 60_000
    return when {
        min < 1 -> "${ms / 1000}s"
        min < 60 -> "${min}m"
        else -> "${min / 60}h ${min % 60}m"
    }
}

private fun formatClock(ms: Long): String {
    val s = ms / 1000
    return if (s >= 60) "${s / 60}m ${s % 60}s" else "${s}s"
}

