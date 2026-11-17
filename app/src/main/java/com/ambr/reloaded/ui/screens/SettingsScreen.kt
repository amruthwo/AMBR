package com.ambr.reloaded.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.ambr.reloaded.share.Site
import com.ambr.reloaded.share.SiteCookies
import com.ambr.reloaded.share.SiteLoginActivity
import com.ambr.reloaded.util.Biometrics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ambr.reloaded.BuildConfigInfo
import com.ambr.reloaded.R
import com.ambr.reloaded.ui.AmbrViewModel
import com.ambr.reloaded.ui.components.PinDialog
import com.ambr.reloaded.ui.components.PinMode
import kotlinx.coroutines.launch

private data class Credit(val name: String, val what: String, val license: String, val url: String)

private val credits = listOf(
    Credit("Seal", "The share-to-download flow is modelled on Seal by JunkFood02", "GPL-3.0", "https://github.com/JunkFood02/Seal"),
    Credit("youtubedl-android", "Runs yt-dlp and FFmpeg on Android", "GPL-3.0", "https://github.com/JunkFood02/youtubedl-android"),
    Credit("yt-dlp", "The downloader engine", "Unlicense", "https://github.com/yt-dlp/yt-dlp"),
    Credit("FFmpeg", "Merges video and audio streams", "LGPL / GPL", "https://ffmpeg.org"),
    Credit("AndroidX Media3", "Video playback", "Apache-2.0", "https://developer.android.com/media/media3"),
    Credit("Coil", "Thumbnails", "Apache-2.0", "https://coil-kt.github.io/coil/"),
    Credit("Bricolage Grotesque", "Display typeface", "OFL-1.1", "https://fonts.google.com/specimen/Bricolage+Grotesque"),
)

@Composable
fun SettingsScreen(vm: AmbrViewModel, snackbar: SnackbarHostState, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val holdSpeed by vm.holdSpeed.collectAsStateWithLifecycle()
    val seekStep by vm.seekStep.collectAsStateWithLifecycle()
    val height by vm.downloadHeight.collectAsStateWithLifecycle()
    val autoLock by vm.autoLock.collectAsStateWithLifecycle()
    val ytVersion by vm.ytdlpVersion.collectAsStateWithLifecycle()
    val updating by vm.ytdlpUpdating.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadYtDlpVersion() }

    var changePinStep by remember { mutableStateOf(0) }  // 0 none, 1 verify old, 2 create new, 3 verify for fingerprint
    val biometric by vm.biometric.collectAsStateWithLifecycle()
    val canBiometric = remember { Biometrics.available(context) }
    // Re-read sign-in state whenever we come back from the sign-in browser.
    var signInTick by remember { mutableStateOf(0) }
    LifecycleResumeEffect(Unit) { signInTick++; onPauseOrDispose { } }
    var erase by remember { mutableStateOf(false) }

    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Section("Library") {
                    val hide by vm.hideFromGalleries.collectAsStateWithLifecycle()
                    ListItem(
                        leadingContent = { Icon(Icons.Rounded.VisibilityOff, null) },
                        headlineContent = { Text("Hide AMBR from galleries") },
                        supportingContent = { Text("Keeps your videos out of Photos and other apps. Long-press a folder to make an exception.") },
                        trailingContent = { Switch(checked = hide, onCheckedChange = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.toggleable(hide, role = Role.Switch) { vm.setHideFromGalleries(it) },
                    )
                }
            }
            item {
                Section("Playback") {
                    Choice("Hold to speed up", listOf(1.5f to "1.5×", 2f to "2×", 3f to "3×"), holdSpeed, vm::setHoldSpeed)
                    Choice("Double-tap seeks", listOf(5 to "5s", 10 to "10s", 15 to "15s"), seekStep, vm::setSeekStep)
                }
            }
            item {
                Section("Hidden folders") {
                    Choice(
                        "Relock after leaving the app",
                        listOf(0 to "At once", 30 to "30s", 300 to "5 min"),
                        autoLock, vm::setAutoLock,
                    )
                    if (vm.pinIsSet && canBiometric) {
                        ListItem(
                            leadingContent = { Icon(Icons.Rounded.Fingerprint, null) },
                            headlineContent = { Text("Unlock with fingerprint") },
                            supportingContent = { Text("Your PIN still works as a backup") },
                            trailingContent = { Switch(checked = biometric, onCheckedChange = null) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.toggleable(biometric, role = Role.Switch) { on ->
                                if (on) changePinStep = 3 else vm.setBiometric(false)
                            },
                        )
                    }
                    if (vm.pinIsSet) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            FilledTonalButton(onClick = { changePinStep = 1 }) {
                                Icon(Icons.Rounded.Password, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Change PIN")
                            }
                            TextButton(
                                onClick = { erase = true },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            ) { Text("Forgot PIN…") }
                        }
                    } else {
                        Text(
                            "No PIN yet. You'll set one the first time you hide a folder.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                Section("Downloads") {
                    Choice(
                        "Quality",
                        listOf(480 to "480p", 720 to "720p", 1080 to "1080p", 0 to "Best"),
                        height, vm::setDownloadHeight,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("yt-dlp", style = MaterialTheme.typography.titleSmall)
                            Text(
                                ytVersion ?: "checking…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (updating) LoadingIndicator(Modifier.size(40.dp))
                        else FilledTonalButton(onClick = { vm.updateYtDlp() }) {
                            Icon(Icons.Rounded.SystemUpdateAlt, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Update")
                        }
                    }
                    Text(
                        "Sites change often. If a download fails, update yt-dlp and try again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            item {
                Section("Site sign-ins") {
                    Text(
                        "Some sites (Reddit, Instagram, X) only share videos with signed-in users. Sign in once here " +
                            "and downloads use that login. It stays on this phone, in AMBR's private storage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Site.entries.forEach { site ->
                        val signedIn = remember(signInTick) { SiteCookies.isSignedIn(context, site) }
                        ListItem(
                            headlineContent = { Text(site.label) },
                            supportingContent = { Text(if (signedIn) "Signed in" else "Not signed in") },
                            trailingContent = {
                                if (signedIn) TextButton(onClick = { SiteCookies.signOut(context, site); signInTick++ }) { Text("Sign out") }
                                else FilledTonalButton(onClick = { context.startActivity(SiteLoginActivity.intent(context, site)) }) { Text("Sign in") }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }
            item {
                Section("About") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.ambr_logo), null, Modifier.size(56.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("AMBR · Airplane Mode Brain Rot", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Version ${BuildConfigInfo.versionName(context)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Built with", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    credits.forEach { c ->
                        ListItem(
                            headlineContent = { Text(c.name) },
                            supportingContent = { Text("${c.what} · ${c.license}") },
                            trailingContent = {
                                IconButton(onClick = { open(c.url) }) { Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Open ${c.name}") }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }
        }
    }

    when (changePinStep) {
        1 -> PinDialog(
            mode = PinMode.Verify,
            onDismiss = { changePinStep = 0 },
            check = { vm.checkPin(it) },
            onSuccess = { changePinStep = 2 },
        )
        3 -> PinDialog(
            mode = PinMode.Verify,
            title = "Confirm your PIN to turn on fingerprint",
            onDismiss = { changePinStep = 0 },
            check = { vm.checkPin(it) },
            onSuccess = { changePinStep = 0; vm.setBiometric(true); vm.say("Fingerprint unlock on") },
        )
        2 -> PinDialog(
            mode = PinMode.Create,
            title = null,
            onDismiss = { changePinStep = 0 },
            onSuccess = { code ->
                changePinStep = 0
                scope.launch { vm.createPin(code); vm.say("PIN changed") }
            },
        )
    }
    if (erase) {
        AlertDialog(
            onDismissRequest = { erase = false },
            icon = { Icon(Icons.Rounded.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Erase hidden folders?") },
            text = {
                Text(
                    "A forgotten PIN can't be recovered. The only way back is to permanently delete every hidden folder " +
                        "and its videos, then set a new PIN.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { erase = false; vm.eraseVault() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Erase everything hidden") }
            },
            dismissButton = { TextButton(onClick = { erase = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun <T> Choice(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (value, text) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(text) }
        }
    }
}
