package com.ambr.reloaded.ui.screens

import com.ambr.reloaded.util.str

import com.ambr.reloaded.R

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.ambr.reloaded.data.Cram
import com.ambr.reloaded.data.CramCandidate
import com.ambr.reloaded.ui.AmbrViewModel
import com.ambr.reloaded.ui.components.ThumbTile
import com.ambr.reloaded.util.formatBytes
import com.ambr.reloaded.util.pluralVideos
import kotlinx.coroutines.launch

@Composable
fun CramScreen(vm: AmbrViewModel, snackbar: SnackbarHostState, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val height by vm.cramHeight.collectAsStateWithLifecycle()
    val charging by vm.cramCharging.collectAsStateWithLifecycle()

    // Live status of the background worker + the saved queue.
    val works by remember { WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(Cram.WORK) }
        .collectAsState(initial = emptyList())
    val work = works.firstOrNull()
    val queue by produceState(Cram.readQueue(context), work?.state, work?.progress) { value = Cram.readQueue(context) }
    val running = work?.state == WorkInfo.State.RUNNING
    val waiting = work?.state == WorkInfo.State.ENQUEUED

    var scanning by remember { mutableStateOf<Float?>(null) }
    var candidates by remember { mutableStateOf<List<CramCandidate>?>(null) }
    var picked by remember { mutableStateOf(emptySet<String>()) }

    fun scan() = scope.launch {
        val videos = folders.filter { !it.isVirtual }.flatMap { it.videos }
        scanning = 0f
        val found = vm.cramCandidates(videos) { d, t -> scanning = if (t == 0) 1f else d / t.toFloat() }
        scanning = null
        candidates = found
        picked = found.map { it.video.path }.toSet()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(str(R.string.cram_cram_session)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, str(R.string.cram_back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            val chosen = candidates.orEmpty().filter { it.video.path in picked }
            if (chosen.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    Button(
                        onClick = {
                            vm.startCram(chosen.map { it.video.path })
                            candidates = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(16.dp)
                            .height(ButtonDefaults.MediumContainerHeight),
                    ) {
                        Icon(Icons.Rounded.Compress, null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            str(R.string.cram_cram_x_save_x, pluralVideos(chosen.size), formatBytes(chosen.sumOf { it.savings })),
                            style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight),
                        )
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp, padding.calculateTopPadding() + 4.dp, 16.dp, padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = MaterialShapes.Pill.toShape(), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(44.dp)) {
                                Icon(Icons.Rounded.Compress, null, Modifier.padding(10.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                str(R.string.cram_shrinks_big_videos_using_your),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(str(R.string.cram_shrink_to), style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(6.dp))
                        val opts = listOf(480 to "480p", 720 to "720p", 1080 to "1080p")
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            opts.forEachIndexed { i, (h, label) ->
                                SegmentedButton(
                                    selected = height == h,
                                    onClick = { vm.setCramHeight(h); candidates = null },
                                    shape = SegmentedButtonDefaults.itemShape(i, opts.size),
                                ) { Text(label) }
                            }
                        }
                        ListItem(
                            leadingContent = { Icon(Icons.Rounded.BatteryChargingFull, null) },
                            headlineContent = { Text(str(R.string.cram_only_while_charging)) },
                            supportingContent = { Text(str(R.string.cram_big_batches_can_take_a)) },
                            trailingContent = { Switch(checked = charging, onCheckedChange = null) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.toggleable(charging, role = Role.Switch) { vm.setCramCharging(it) },
                        )
                    }
                }
            }

            // Queue / progress.
            if (queue.pending.isNotEmpty() || running || queue.done + queue.failed + queue.kept > 0) {
                item {
                    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.padding(18.dp)) {
                            val name = work?.progress?.getString("name")
                            val index = work?.progress?.getInt("index", 0) ?: 0
                            val total = work?.progress?.getInt("total", 0) ?: 0
                            val fraction = work?.progress?.getFloat("fraction", 0f) ?: 0f
                            Text(
                                when {
                                    running && total > 0 -> str(R.string.cram_cramming_x_of_x, index, total)
                                    running -> str(R.string.cram_starting)
                                    waiting && charging -> str(R.string.cram_waiting_for_the_charger)
                                    queue.pending.isNotEmpty() -> str(R.string.cram_paused_x_left, pluralVideos(queue.pending.size))
                                    else -> str(R.string.cram_done)
                                },
                                style = MaterialTheme.typography.titleLarge,
                            )
                            if (running && name != null) {
                                Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(10.dp))
                                LinearWavyProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                str(R.string.cram_x_shrunk_saved_x, queue.done, formatBytes(queue.saved)) +
                                    (if (queue.kept > 0) str(R.string.cram_x_already_small_kept, queue.kept) else "") +
                                    if (queue.failed > 0) str(R.string.cram_x_couldn_t_convert_kept, queue.failed) else "",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (running || waiting) {
                                    FilledTonalButton(onClick = { Cram.pause(context) }) {
                                        Icon(Icons.Rounded.Pause, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(str(R.string.bits_pause))
                                    }
                                } else if (queue.pending.isNotEmpty()) {
                                    FilledTonalButton(onClick = { Cram.start(context, charging) }) {
                                        Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(str(R.string.cram_resume))
                                    }
                                }
                                if (!running) TextButton(onClick = { Cram.clear(context); vm.refresh() }) {
                                    Text(if (queue.pending.isEmpty()) str(R.string.cram_dismiss) else str(R.string.cram_clear_queue))
                                }
                            }
                        }
                    }
                }
            }

            // Finding candidates.
            when {
                scanning != null -> item {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        LoadingIndicator(Modifier.size(64.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(str(R.string.cram_measuring_your_videos), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(10.dp))
                        LinearWavyProgressIndicator(progress = { scanning ?: 0f }, modifier = Modifier.fillMaxWidth(0.7f))
                    }
                }
                candidates == null -> item {
                    Button(
                        onClick = { scan() },
                        modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight),
                    ) {
                        Icon(Icons.Rounded.Search, null); Spacer(Modifier.width(8.dp))
                        Text(str(R.string.cram_find_videos_to_cram), style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
                    }
                }
                candidates!!.isEmpty() -> item {
                    Text(
                        str(R.string.cram_everything_is_already_x_p, height),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                else -> {
                    val list = candidates!!
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                str(R.string.cram_x_could_shrink_x, pluralVideos(list.size), formatBytes(list.sumOf { it.savings })),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = {
                                picked = if (picked.size == list.size) emptySet() else list.map { it.video.path }.toSet()
                            }) { Text(if (picked.size == list.size) str(R.string.cram_none) else str(R.string.cram_all)) }
                        }
                    }
                    items(list, key = { it.video.path }) { c ->
                        val on = c.video.path in picked
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .toggleable(on, role = Role.Checkbox) { picked = if (it) picked + c.video.path else picked - c.video.path },
                        ) {
                            Checkbox(checked = on, onCheckedChange = null, modifier = Modifier.padding(end = 8.dp))
                            ThumbTile(c.video, Modifier.size(width = 40.dp, height = 52.dp), corner = 10.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.video.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${c.probe.shortSide}p · ${formatBytes(c.video.sizeBytes)} → ~${formatBytes(c.estimatedBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text("−${formatBytes(c.savings)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
