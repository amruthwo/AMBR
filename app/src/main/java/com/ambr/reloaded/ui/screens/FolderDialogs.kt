package com.ambr.reloaded.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ambr.reloaded.data.VideoFolder
import com.ambr.reloaded.ui.Busy
import com.ambr.reloaded.ui.components.ThumbTile
import com.ambr.reloaded.util.formatBytes
import com.ambr.reloaded.util.pluralVideos

@Composable
fun CreateFolderDialog(onDismiss: () -> Unit, onCreate: (name: String, hidden: Boolean, noMedia: Boolean) -> Unit) {
    var name by remember { mutableStateOf("") }
    var hidden by remember { mutableStateOf(false) }
    var noMedia by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val valid = name.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.CreateNewFolder, null) },
        title = { Text("New folder") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Folder name") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (valid) onCreate(name, hidden, noMedia || hidden) }),
                    modifier = Modifier.focusRequester(focus),
                )
                Spacer(Modifier.height(12.dp))
                SwitchRow(
                    Icons.Rounded.Lock, "Hidden folder",
                    "Only visible after entering your PIN",
                    hidden,
                ) { hidden = it }
                SwitchRow(
                    Icons.Rounded.VisibilityOff, "Hide from galleries",
                    if (hidden) "Hidden folders are always invisible to other apps" else "Adds a .nomedia file",
                    noMedia || hidden, enabled = !hidden,
                ) { noMedia = it }
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name, hidden, noMedia || hidden) }, enabled = valid) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    ListItem(
        leadingContent = { Icon(icon, null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        // The whole row flips the switch, not just the little thumb.
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
    )
}

/** Long-press a folder: gallery visibility, hidden (vault) toggle, rename, delete. */
@Composable
fun FolderOptionsSheet(
    folder: VideoFolder,
    onDismiss: () -> Unit,
    onNoMedia: (Boolean) -> Unit,
    onHidden: (Boolean) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onCram: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    ThumbTile(folder.cover, Modifier.size(56.dp), corner = 16.dp)
                    if (folder.cover == null) Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(folder.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${pluralVideos(folder.count)} · ${formatBytes(folder.totalBytes)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            SwitchRow(
                Icons.Rounded.VisibilityOff, "Hide from galleries",
                when {
                    folder.isHidden -> "Hidden folders are always invisible to other apps"
                    else -> "Overrides the default in Settings for this folder"
                },
                folder.hiddenFromGalleries,
                enabled = !folder.isHidden,
                onChange = onNoMedia,
            )
            if (!folder.isRoot) {
                SwitchRow(
                    Icons.Rounded.Lock, "Hidden folder",
                    if (folder.isHidden) "Turn off to move it back to Internal storage › AMBR"
                    else "Moves it into AMBR's private, PIN-locked space",
                    folder.isHidden,
                    onChange = onHidden,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    val big = 28.dp
                    val small = 8.dp
                    Tile(
                        "Rename", Icons.Rounded.DriveFileRenameOutline, onRename,
                        RoundedCornerShape(topStart = big, bottomStart = big, topEnd = small, bottomEnd = small),
                        Modifier.weight(1f),
                        MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Tile(
                        "Cram", Icons.Rounded.Compress, onCram,
                        RoundedCornerShape(small),
                        Modifier.weight(1f),
                        MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Tile(
                        "Delete", Icons.Rounded.DeleteOutline, onDelete,
                        RoundedCornerShape(topStart = small, bottomStart = small, topEnd = big, bottomEnd = big),
                        Modifier.weight(1f),
                        MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun Tile(
    label: String, icon: ImageVector, onClick: () -> Unit, shape: RoundedCornerShape,
    modifier: Modifier, container: Color, content: Color,
) {
    Surface(onClick = onClick, shape = shape, color = container, contentColor = content, modifier = modifier.height(72.dp)) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null)
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun RenameFolderDialog(folder: VideoFolder, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val start = folder.name.substringAfterLast('/')
    var value by remember { mutableStateOf(TextFieldValue(start, TextRange(0, start.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.DriveFileRenameOutline, null) },
        title = { Text("Rename folder") },
        text = {
            OutlinedTextField(
                value = value, onValueChange = { value = it }, singleLine = true,
                label = { Text("Folder name") }, shape = RoundedCornerShape(16.dp),
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(value.text) }, enabled = value.text.isNotBlank()) { Text("Rename") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun DeleteFolderDialog(folder: VideoFolder, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Delete “${folder.name}”?") },
        text = {
            Text(
                if (folder.count == 0) "The empty folder will be removed."
                else "This permanently deletes the folder and all ${pluralVideos(folder.count)} in it (${formatBytes(folder.totalBytes)}).",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text("Delete")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Shown before the first hide, because the vault lives and dies with the app. */
@Composable
fun HideWarningDialog(folder: VideoFolder, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.tertiary) },
        title = { Text("Hide “${folder.name}”?") },
        text = {
            Text(
                "Its videos move into AMBR's private storage, where no other app, gallery or file manager can see them, " +
                    "and only your PIN reveals them.\n\nHeads up: uninstalling AMBR or clearing its storage deletes hidden videos. " +
                    "Unhide anything you want to keep first.",
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Hide it") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Non-dismissable progress for long file moves. */
@Composable
fun BusyDialog(busy: Busy) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Surface(shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                LoadingIndicator(Modifier.size(64.dp))
                Spacer(Modifier.height(16.dp))
                Text(busy.label, style = MaterialTheme.typography.titleMedium)
                busy.progress?.let { p ->
                    Spacer(Modifier.height(16.dp))
                    LinearWavyProgressIndicator(progress = { p }, modifier = Modifier.width(220.dp))
                }
            }
        }
    }
}
