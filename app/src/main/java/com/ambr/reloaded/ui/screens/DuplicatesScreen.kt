package com.ambr.reloaded.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ambr.reloaded.ui.AmbrViewModel
import com.ambr.reloaded.ui.components.ThumbTile
import com.ambr.reloaded.util.formatBytes
import java.text.DateFormat
import java.util.Date

@Composable
fun DuplicatesScreen(vm: AmbrViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val state by vm.duplicates.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { if (state.groups == null && !state.running) vm.findDuplicates() }

    // Paths ticked for deletion. Default: every copy except the oldest in each set.
    var selected by remember(state.groups) {
        mutableStateOf(state.groups.orEmpty().flatMap { g -> g.drop(1).map { it.path } }.toSet())
    }
    var confirm by remember { mutableStateOf(false) }
    val all = state.groups.orEmpty().flatten()
    val chosen = all.filter { it.path in selected }
    val dateFmt = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Duplicates") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = {
                    if (!state.running) IconButton(onClick = vm::findDuplicates) { Icon(Icons.Rounded.Refresh, "Scan again") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            if (chosen.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    Button(
                        onClick = { confirm = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(16.dp)
                            .height(ButtonDefaults.MediumContainerHeight),
                    ) {
                        Icon(Icons.Rounded.DeleteSweep, null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Delete ${chosen.size} ${if (chosen.size == 1) "copy" else "copies"} · free ${formatBytes(chosen.sumOf { it.sizeBytes })}",
                            style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight),
                        )
                    }
                }
            }
        },
    ) { padding ->
        when {
            state.running -> Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                LoadingIndicator(Modifier.size(72.dp))
                Spacer(Modifier.height(20.dp))
                Text("Comparing videos…", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                LinearWavyProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth(0.7f))
                Spacer(Modifier.height(12.dp))
                Text(
                    "Matching sizes first, then fingerprints, so nothing is flagged unless it's byte-for-byte identical.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            state.groups?.isEmpty() == true -> Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Rounded.CheckCircle, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text("No duplicates", style = MaterialTheme.typography.headlineMedium)
                Text("Your brain is tidy.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val groups = state.groups.orEmpty()
                item {
                    Text(
                        "${groups.size} ${if (groups.size == 1) "set" else "sets"} of identical videos. " +
                            "The oldest copy of each is kept unless you change the ticks.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
                itemsIndexed(groups, key = { _, g -> g.first().path }) { _, group ->
                    Card(
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        Column(Modifier.padding(vertical = 10.dp)) {
                            Text(
                                "${group.size} copies · ${formatBytes(group.first().sizeBytes)} each",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                            )
                            group.forEach { v ->
                                val checked = v.path in selected
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                ) {
                                    Checkbox(checked = checked, onCheckedChange = {
                                        selected = if (it) selected + v.path else selected - v.path
                                    })
                                    Box { ThumbTile(v, Modifier.size(width = 44.dp, height = 56.dp), corner = 10.dp) }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(v.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            "${v.file.parentFile?.name.orEmpty()} · ${dateFmt.format(Date(v.lastModified))}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (!checked) {
                                        Text("Keep", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                                        Spacer(Modifier.width(8.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirm) {
        val keepsNone = state.groups.orEmpty().any { g -> g.all { it.path in selected } }
        AlertDialog(
            onDismissRequest = { confirm = false },
            icon = { Icon(Icons.Rounded.DeleteSweep, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete ${chosen.size} ${if (chosen.size == 1) "copy" else "copies"}?") },
            text = {
                Text(
                    if (keepsNone) "Careful: at least one set has every copy ticked, so that video will be gone completely."
                    else "One copy of every video stays. This frees ${formatBytes(chosen.sumOf { it.sizeBytes })}.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { confirm = false; vm.deleteVideos(chosen) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}
