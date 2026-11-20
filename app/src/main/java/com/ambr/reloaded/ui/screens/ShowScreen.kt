package com.ambr.reloaded.ui.screens

import com.ambr.reloaded.util.str

import com.ambr.reloaded.R

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ambr.reloaded.data.NaturalOrder
import com.ambr.reloaded.data.Video
import com.ambr.reloaded.ui.AmbrViewModel
import com.ambr.reloaded.ui.components.ThumbTile
import com.ambr.reloaded.ui.theme.Ambr
import com.ambr.reloaded.util.formatDuration
import com.ambr.reloaded.util.pluralVideos
import java.io.File

enum class EpisodeSort { Name, DateAdded, Length, Size }

private val EpisodeSort.label: String
    get() = when (this) {
        EpisodeSort.Name -> str(R.string.search_name)
        EpisodeSort.DateAdded -> str(R.string.show_date_added)
        EpisodeSort.Length -> str(R.string.show_length)
        EpisodeSort.Size -> str(R.string.search_size)
    }

/** A Big Brain folder: continue button, seasons as tabs, episodes in order with resume progress. */
@Composable
fun ShowScreen(vm: AmbrViewModel, path: String, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val all by vm.allFolders.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val show = remember(all, path) { vm.show(path) }
    if (show == null) { onBack(); return }
    val durations by produceState(emptyMap<String, Long>(), show.playlist) { value = vm.durationsOf(show.playlist) }

    // Episode order (remembered per show). Seasons keep their order; episodes sort within each.
    val saved = remember(path) { vm.showSort(path)?.split(':') }
    var sort by remember(path) { mutableStateOf(saved?.getOrNull(0)?.let { runCatching { EpisodeSort.valueOf(it) }.getOrNull() } ?: EpisodeSort.Name) }
    var descending by remember(path) { mutableStateOf(saved?.getOrNull(1) == "desc") }
    fun sorted(list: List<Video>): List<Video> {
        val asc = when (sort) {
            EpisodeSort.Name -> list.sortedWith(compareBy(NaturalOrder) { it.name })
            EpisodeSort.DateAdded -> list.sortedBy { it.lastModified }
            EpisodeSort.Length -> list.sortedBy { durations[it.path] ?: 0L }
            EpisodeSort.Size -> list.sortedBy { it.sizeBytes }
        }
        return if (descending) asc.reversed() else asc
    }

    // Tabs: loose videos in the folder itself (if any), then each season sub-folder.
    val tabs = buildList {
        if (show.episodes.isNotEmpty()) add(str(R.string.show_episodes) to sorted(show.episodes))
        show.seasons.forEach { s ->
            add(s.path.removePrefix(show.folder.path + File.separator) to sorted(s.videos))
        }
    }
    // What plays, and in what order: every tab, top to bottom, in the chosen sort.
    val playlist = tabs.flatMap { it.second }
    var tab by remember(path) { mutableIntStateOf(0) }
    var newSeason by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf<Video?>(null) }
    // Up next: the first half-watched video, else the first unwatched one.
    val next = playlist.firstOrNull { progress[it.path]?.let { p -> !p.watched && p.positionMs > 5_000 } == true }
        ?: playlist.firstOrNull { progress[it.path]?.watched != true } ?: playlist.firstOrNull()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(show.folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            str(R.string.show_big_brain_x_videos, pluralVideos(playlist.size)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, str(R.string.cram_back)) } },
                actions = {
                    IconButton(onClick = { newSeason = true }) { Icon(Icons.Rounded.CreateNewFolder, str(R.string.show_new_season)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            if (next != null) {
                val p = progress[next.path]
                Button(
                    onClick = { vm.markLastFolder(path); vm.playLong(show.folder.name, playlist, playlist.indexOf(next)) },
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth()
                        .height(68.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Rounded.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    // Two lines, so a title can't be misread as part of the label.
                    Column {
                        Text(
                            if (p != null && !p.watched && p.positionMs > 5_000) str(R.string.show_continue) else str(R.string.show_play),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(next.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (tabs.size > 1) {
                PrimaryScrollableTabRow(selectedTabIndex = tab.coerceAtMost(tabs.lastIndex), containerColor = Color.Transparent, edgePadding = 16.dp) {
                    tabs.forEachIndexed { i, (name, _) -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(name, maxLines = 1) }) }
                }
            }
            // Sort chips: tap another to switch, tap the active one to reverse.
            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(EpisodeSort.entries.size) { i ->
                    val s = EpisodeSort.entries[i]
                    androidx.compose.material3.FilterChip(
                        selected = sort == s,
                        onClick = {
                            if (sort == s) descending = !descending
                            else { sort = s; descending = s == EpisodeSort.DateAdded }   // newest first by default
                            vm.setShowSort(path, "${sort.name}:${if (descending) "desc" else "asc"}")
                        },
                        label = { Text(s.label) },
                        trailingIcon = if (sort == s) {
                            {
                                Icon(
                                    if (descending) androidx.compose.material.icons.Icons.Rounded.ArrowDownward else androidx.compose.material.icons.Icons.Rounded.ArrowUpward,
                                    null, Modifier.size(16.dp),
                                )
                            }
                        } else null,
                    )
                }
            }
            val episodes = tabs.getOrNull(tab)?.second.orEmpty()
            if (episodes.isEmpty()) {
                Text(
                    str(R.string.show_this_season_is_empty_move),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(episodes, key = { _, v -> v.path }) { i, v ->
                    EpisodeRow(
                        number = i + 1,
                        video = v,
                        durationMs = durations[v.path] ?: progress[v.path]?.durationMs ?: 0L,
                        fraction = progress[v.path]?.fraction ?: 0f,
                        watched = progress[v.path]?.watched == true,
                        onPlay = { vm.markLastFolder(path); vm.playLong(show.folder.name, playlist, playlist.indexOf(v)) },
                        onWatched = { vm.setWatched(v, it) },
                        onMove = { moving = v },
                    )
                }
            }
        }
    }

    if (newSeason) {
        var name by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { newSeason = false },
            icon = { Icon(Icons.Rounded.CreateNewFolder, null) },
            title = { Text(str(R.string.show_new_season)) },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    name, { name = it }, singleLine = true,
                    label = { Text(str(R.string.show_name_e_g_season_3)) },
                    shape = RoundedCornerShape(16.dp),
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(enabled = name.isNotBlank(), onClick = {
                    newSeason = false; vm.createSubfolder(show.folder.path, name)
                }) { Text(str(R.string.folder_create)) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { newSeason = false }) { Text(str(R.string.pin_pad_cancel)) } },
        )
    }
    moving?.let { v ->
        MoveSheet(
            video = v,
            folders = (listOf(show.folder) + show.seasons).filter { it.path != v.folderPath },
            onDismiss = { moving = null },
            onMove = { f -> moving = null; vm.move(v, f) },
        )
    }
}

@Composable
private fun EpisodeRow(
    number: Int,
    video: Video,
    durationMs: Long,
    fraction: Float,
    watched: Boolean,
    onPlay: () -> Unit,
    onWatched: (Boolean) -> Unit,
    onMove: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onPlay)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(150.dp).aspectRatio(16f / 9f)) {
            ThumbTile(video, Modifier.fillMaxSize(), corner = 14.dp)
            if (watched) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.CheckCircle, str(R.string.show_watched), tint = Color.White)
                }
            } else if (fraction > 0.01f) {
                // Resume bar along the bottom of the thumbnail.
                Box(Modifier.align(Alignment.BottomStart).padding(6.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.35f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(Ambr.Pink))
                }
            }
            if (durationMs > 0) {
                Text(
                    formatDuration(durationMs),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("$number", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(video.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, str(R.string.home_more)) }
            DropdownMenu(menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(20.dp)) {
                DropdownMenuItem(
                    text = { Text(if (watched) str(R.string.show_mark_as_unwatched) else str(R.string.show_mark_as_watched)) },
                    leadingIcon = { Icon(if (watched) Icons.Rounded.Psychology else Icons.Rounded.CheckCircle, null) },
                    onClick = { menu = false; onWatched(!watched) },
                )
                DropdownMenuItem(
                    text = { Text(str(R.string.video_move_to)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.DriveFileMove, null) },
                    onClick = { menu = false; onMove() },
                )
            }
        }
    }
}

