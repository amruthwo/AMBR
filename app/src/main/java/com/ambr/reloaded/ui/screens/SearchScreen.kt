package com.ambr.reloaded.ui.screens

import com.ambr.reloaded.util.str

import com.ambr.reloaded.R

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ambr.reloaded.data.NaturalOrder
import com.ambr.reloaded.data.Video
import com.ambr.reloaded.ui.AmbrViewModel
import com.ambr.reloaded.ui.components.ThumbTile
import com.ambr.reloaded.util.formatBytes
import com.ambr.reloaded.util.plural

private enum class Kind { All, BrainRot, BigBrain }
private enum class SortBy { Name, Size, Folder }

private data class Hit(val video: Video, val folderName: String, val bigBrain: Boolean)

/** Search every video by title or folder; filter Brain Rot / Big Brain; sort by name, size or folder. */
@Composable
fun SearchScreen(vm: AmbrViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val all by vm.allFolders.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(Kind.All) }
    var sort by rememberSaveable { mutableStateOf(SortBy.Name) }
    var descending by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val hits = remember(all, query, kind, sort, descending) {
        val q = query.trim().lowercase()
        val list = all.flatMap { f ->
            val big = vm.bigBrainRootOf(f.path) != null
            f.videos.map { Hit(it, f.name, big) }
        }.filter { h ->
            (kind == Kind.All || (kind == Kind.BigBrain) == h.bigBrain) &&
                (q.isEmpty() || q in h.video.title.lowercase() || q in h.folderName.lowercase())
        }
        val sorted = when (sort) {
            SortBy.Name -> list.sortedWith(compareBy(NaturalOrder) { it.video.title })
            SortBy.Size -> list.sortedBy { it.video.sizeBytes }
            SortBy.Folder -> list.sortedWith(compareBy<Hit, String>(NaturalOrder) { it.folderName }.thenBy(NaturalOrder) { it.video.title })
        }
        if (descending) sorted.reversed() else sorted
    }

    androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Row(Modifier.padding(start = 4.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, str(R.string.cram_back)) }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(str(R.string.search_search_videos_and_folders)) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, str(R.string.mind_meld_clear)) } },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
        }
        // Filters and sorting.
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(kind == Kind.All, { kind = Kind.All }, label = { Text(str(R.string.cram_all)) })
            }
            item {
                FilterChip(kind == Kind.BrainRot, { kind = Kind.BrainRot }, label = { Text(str(R.string.search_brain_rot)) },
                    leadingIcon = { Icon(Icons.Rounded.Shuffle, null, Modifier.size(18.dp)) })
            }
            item {
                FilterChip(kind == Kind.BigBrain, { kind = Kind.BigBrain }, label = { Text(str(R.string.search_big_brain)) },
                    leadingIcon = { Icon(Icons.Rounded.Psychology, null, Modifier.size(18.dp)) })
            }
        }
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(str(R.string.search_sort), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            SortBy.entries.forEach { s ->
                FilterChip(
                    sort == s,
                    { if (sort == s) descending = !descending else { sort = s; descending = false } },
                    label = { Text(when (s) { SortBy.Name -> str(R.string.search_name); SortBy.Size -> str(R.string.search_size); SortBy.Folder -> str(R.string.search_folder) }) },
                    trailingIcon = if (sort == s) {
                        { Icon(if (descending) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward, if (descending) str(R.string.search_descending) else str(R.string.search_ascending), Modifier.size(16.dp)) }
                    } else null,
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
        }
        Text(
            plural(R.plurals.results, hits.size),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )
        LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(hits, key = { it.video.path }) { h ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { vm.openVideo(h.video) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ThumbTile(h.video, Modifier.size(width = 44.dp, height = 58.dp), corner = 10.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(h.video.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${h.folderName} · ${formatBytes(h.video.sizeBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(
                        if (h.bigBrain) Icons.Rounded.Psychology else Icons.Rounded.Shuffle,
                        if (h.bigBrain) str(R.string.search_big_brain) else str(R.string.search_brain_rot),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
    }
}

