package com.ambr.reloaded.ui.screens

import com.ambr.reloaded.util.str

import com.ambr.reloaded.R

import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ambr.reloaded.data.VideoFormats
import com.ambr.reloaded.ui.AmbrViewModel
import com.ambr.reloaded.util.pluralVideos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private data class Volume(val label: String, val root: File, val removable: Boolean)

private data class Entry(val dir: File, val videos: Int)

/** Internal storage plus any SD cards / USB drives that are mounted. */
private fun volumes(context: Context): List<Volume> {
    val sm = context.getSystemService(StorageManager::class.java)
    val primary = Environment.getExternalStorageDirectory()
    val out = mutableListOf(Volume(str(R.string.link_folder_internal_storage), primary, false))
    context.getExternalFilesDirs(null).drop(1).filterNotNull().forEach { appDir ->
        val root = File(appDir.absolutePath.substringBefore("/Android/data"))
        val label = runCatching { sm.getStorageVolume(root)?.getDescription(context) }.getOrNull() ?: str(R.string.link_folder_sd_card)
        if (out.none { it.root == root }) out += Volume(label, root, true)
    }
    return out
}

/**
 * "Add a folder from your phone": a small folder browser. The chosen folder is linked, not
 * copied: AMBR plays its videos where they are.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkFolderScreen(vm: AmbrViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val vols = remember { volumes(context) }
    var dir by remember { mutableStateOf(vols.first().root) }
    var bigBrain by remember { mutableStateOf(true) }
    var library by remember { mutableStateOf(false) }
    val atVolumeRoot = vols.any { it.root == dir }
    // Show "SD card/TV" rather than "/storage/1A2B-3C4D/TV".
    val vol = vols.firstOrNull { dir.absolutePath == it.root.absolutePath || dir.absolutePath.startsWith(it.root.absolutePath + File.separator) }
    val pretty = if (vol == null) dir.absolutePath else vol.label + dir.absolutePath.removePrefix(vol.root.absolutePath)
    val shortName = if (atVolumeRoot) vol?.label ?: dir.name else dir.name

    val entries by produceState<List<Entry>?>(null, dir) {
        value = withContext(Dispatchers.IO) {
            dir.listFiles()
                ?.filter { it.isDirectory && !it.name.startsWith(".") && !(atVolumeRoot && it.name == "Android") }
                ?.sortedBy { it.name.lowercase() }
                ?.map { d -> Entry(d, d.listFiles()?.count(VideoFormats::isVideo) ?: 0) }
                .orEmpty()
        }
    }
    val problem by produceState<String?>(null, dir) {
        value = withContext(Dispatchers.IO) { if (atVolumeRoot) str(R.string.link_folder_open_a_folder_first) else vm.linkProblem(dir) }
    }
    val here = remember(dir) { dir.listFiles()?.count(VideoFormats::isVideo) ?: 0 }

    BackHandler { if (atVolumeRoot) onBack() else dir = dir.parentFile ?: dir }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(str(R.string.link_folder_add_a_folder))
                            Text(
                                pretty,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.StartEllipsis,
                            )
                        }
                    },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, str(R.string.cram_back)) } },
                )
            },
            bottomBar = {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
                    Column(Modifier.navigationBarsPadding().padding(16.dp)) {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = bigBrain, onClick = { bigBrain = true },
                                shape = SegmentedButtonDefaults.itemShape(0, 2),
                                icon = { Icon(Icons.Rounded.Psychology, null) },
                            ) { Text(str(R.string.search_big_brain)) }
                            SegmentedButton(
                                selected = !bigBrain, onClick = { bigBrain = false },
                                shape = SegmentedButtonDefaults.itemShape(1, 2),
                                icon = { Icon(Icons.Rounded.Whatshot, null) },
                            ) { Text(str(R.string.search_brain_rot)) }
                        }
                        if (bigBrain) Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .toggleable(library, role = Role.Checkbox) { library = it }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = library, onCheckedChange = null, modifier = Modifier.padding(horizontal = 12.dp))
                            Column {
                                Text(str(R.string.link_folder_each_folder_inside_is_its), style = MaterialTheme.typography.bodyLarge)
                                Text(str(R.string.link_folder_for_a_tv_or_channels), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(
                            problem ?: if (bigBrain && library) str(R.string.link_folder_every_folder_inside_becomes_a)
                            else if (bigBrain) str(R.string.link_folder_in_order_with_full_controls)
                            else str(R.string.link_folder_a_shuffled_short_video_feed),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (problem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 10.dp),
                        )
                        Button(
                            onClick = { vm.linkFolder(dir, bigBrain, library); onBack() },
                            enabled = problem == null,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            contentPadding = ButtonDefaults.ContentPadding,
                        ) { Text(str(R.string.link_folder_add_x, shortName), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        Text(
                            str(R.string.link_folder_videos_stay_where_they_are),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            },
        ) { padding ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 8.dp)) {
                if (vols.size > 1) item {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        vols.forEach { v ->
                            FilterChip(
                                selected = dir.absolutePath.startsWith(v.root.absolutePath),
                                onClick = { dir = v.root },
                                label = { Text(v.label) },
                                leadingIcon = { Icon(if (v.removable) Icons.Rounded.SdCard else Icons.Rounded.Smartphone, null) },
                            )
                        }
                    }
                }
                if (!atVolumeRoot) item {
                    ListItem(
                        leadingContent = { Icon(Icons.Rounded.ArrowUpward, null) },
                        headlineContent = { Text(str(R.string.link_folder_up_to_x, dir.parentFile?.let { p -> vols.firstOrNull { it.root == p }?.label ?: p.name })) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { dir = dir.parentFile ?: dir },
                    )
                }
                if (here > 0) item {
                    Text(
                        str(R.string.link_folder_x_in_this_folder, pluralVideos(here)),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
                val list = entries
                if (list != null && list.isEmpty()) item {
                    Text(
                        str(R.string.link_folder_no_folders_in_here),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
                items(list.orEmpty(), key = { it.dir.absolutePath }) { e ->
                    ListItem(
                        leadingContent = { Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary) },
                        headlineContent = { Text(e.dir.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = if (e.videos > 0) ({ Text(pluralVideos(e.videos)) }) else null,
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { dir = e.dir },
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}
