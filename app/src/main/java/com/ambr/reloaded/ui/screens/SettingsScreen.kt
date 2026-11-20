package com.ambr.reloaded.ui.screens

import com.ambr.reloaded.util.str

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Code
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
import com.ambr.reloaded.share.YtDlp
import com.ambr.reloaded.ui.AmbrViewModel
import com.ambr.reloaded.ui.components.icon
import com.ambr.reloaded.ui.components.label
import com.ambr.reloaded.ui.components.PinDialog
import com.ambr.reloaded.ui.components.PinMode
import kotlinx.coroutines.launch

private data class Credit(val name: String, val what: String, val license: String, val url: String)

private val credits = listOf(
    Credit(str(R.string.settings_seal), str(R.string.settings_the_share_to_download_flow), str(R.string.settings_gpl_3_0), "https://github.com/JunkFood02/Seal"),
    Credit("youtubedl-android", str(R.string.settings_runs_yt_dlp_and_ffmpeg), str(R.string.settings_gpl_3_0), "https://github.com/JunkFood02/youtubedl-android"),
    Credit("yt-dlp", str(R.string.settings_the_downloader_engine), str(R.string.settings_unlicense), "https://github.com/yt-dlp/yt-dlp"),
    Credit(str(R.string.settings_ffmpeg), str(R.string.settings_merges_video_and_audio_streams), str(R.string.settings_lgpl_gpl), "https://ffmpeg.org"),
    Credit(str(R.string.settings_androidx_media3), str(R.string.settings_video_playback), str(R.string.settings_apache_2_0), "https://developer.android.com/media/media3"),
    Credit(str(R.string.settings_coil), str(R.string.settings_thumbnails), str(R.string.settings_apache_2_0), "https://coil-kt.github.io/coil/"),
    Credit(str(R.string.settings_bricolage_grotesque), str(R.string.settings_display_typeface), str(R.string.settings_ofl_1_1), "https://fonts.google.com/specimen/Bricolage+Grotesque"),
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
                title = { Text(str(R.string.home_settings)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, str(R.string.cram_back)) } },
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
                Section(str(R.string.settings_library)) {
                    val hide by vm.hideFromGalleries.collectAsStateWithLifecycle()
                    ListItem(
                        leadingContent = { Icon(Icons.Rounded.VisibilityOff, null) },
                        headlineContent = { Text(str(R.string.settings_hide_ambr_from_galleries)) },
                        supportingContent = { Text(str(R.string.settings_keeps_your_videos_out_of)) },
                        trailingContent = { Switch(checked = hide, onCheckedChange = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.toggleable(hide, role = Role.Switch) { vm.setHideFromGalleries(it) },
                    )
                }
            }
            item {
                Section(str(R.string.settings_playback)) {
                    val sr by vm.scaleBrainRot.collectAsStateWithLifecycle()
                    val sb by vm.scaleBigBrain.collectAsStateWithLifecycle()
                    ScaleChoice(str(R.string.settings_brain_rot_video_size), sr, vm::setScaleBrainRot)
                    ScaleChoice(str(R.string.settings_big_brain_video_size), sb, vm::setScaleBigBrain)
                    Choice(str(R.string.settings_hold_to_speed_up), listOf(1.5f to "1.5×", 2f to "2×", 3f to "3×"), holdSpeed, vm::setHoldSpeed)
                    Choice(str(R.string.settings_double_tap_seeks), listOf(5 to "5s", 10 to "10s", 15 to "15s"), seekStep, vm::setSeekStep)
                }
            }
            item {
                Section(str(R.string.settings_hidden_folders)) {
                    Choice(
                        str(R.string.settings_relock_after_leaving_the_app),
                        listOf(0 to str(R.string.settings_at_once), 30 to "30s", 300 to str(R.string.settings_5_min)),
                        autoLock, vm::setAutoLock,
                    )
                    if (vm.pinIsSet && canBiometric) {
                        ListItem(
                            leadingContent = { Icon(Icons.Rounded.Fingerprint, null) },
                            headlineContent = { Text(str(R.string.settings_unlock_with_fingerprint)) },
                            supportingContent = { Text(str(R.string.settings_your_pin_still_works_as)) },
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
                                Icon(Icons.Rounded.Password, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(str(R.string.settings_change_pin))
                            }
                            TextButton(
                                onClick = { erase = true },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            ) { Text(str(R.string.settings_forgot_pin)) }
                        }
                    } else {
                        Text(
                            str(R.string.settings_no_pin_yet_you_ll),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (YtDlp.available) item {
                Section(str(R.string.settings_downloads)) {
                    Choice(
                        str(R.string.settings_quality),
                        listOf(480 to "480p", 720 to "720p", 1080 to "1080p", 0 to str(R.string.settings_best)),
                        height, vm::setDownloadHeight,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("yt-dlp", style = MaterialTheme.typography.titleSmall)
                            Text(
                                ytVersion ?: str(R.string.settings_checking),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (updating) LoadingIndicator(Modifier.size(40.dp))
                        else FilledTonalButton(onClick = { vm.updateYtDlp() }) {
                            Icon(Icons.Rounded.SystemUpdateAlt, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(str(R.string.settings_update))
                        }
                    }
                    Text(
                        str(R.string.settings_sites_change_often_if_a),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            if (YtDlp.available) item {
                Section(str(R.string.settings_site_sign_ins)) {
                    Text(
                        str(R.string.settings_some_sites_reddit_instagram_x) +
                            str(R.string.settings_and_downloads_use_that_login),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Site.entries.forEach { site ->
                        val signedIn = remember(signInTick) { SiteCookies.isSignedIn(context, site) }
                        ListItem(
                            headlineContent = { Text(site.label) },
                            supportingContent = { Text(if (signedIn) str(R.string.settings_signed_in) else str(R.string.settings_not_signed_in)) },
                            trailingContent = {
                                if (signedIn) TextButton(onClick = { SiteCookies.signOut(context, site); signInTick++ }) { Text(str(R.string.settings_sign_out)) }
                                else FilledTonalButton(onClick = { context.startActivity(SiteLoginActivity.intent(context, site)) }) { Text(str(R.string.settings_sign_in)) }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }
            item {
                Section(str(R.string.settings_about)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.ambr_logo), null, Modifier.size(56.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(str(R.string.settings_ambr_airplane_mode_brain_rot), style = MaterialTheme.typography.titleMedium)
                            Text(
                                str(R.string.settings_version_x, BuildConfigInfo.versionName(context)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    ListItem(
                        leadingContent = { Icon(Icons.Rounded.Code, null) },
                        headlineContent = { Text(str(R.string.settings_source_code_updates)) },
                        supportingContent = { Text(str(R.string.settings_github_com_amruthwo_ambr_learn)) },
                        trailingContent = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { open("https://github.com/amruthwo/AMBR") },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(str(R.string.settings_built_with), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    credits.forEach { c ->
                        ListItem(
                            headlineContent = { Text(c.name) },
                            supportingContent = { Text("${c.what} · ${c.license}") },
                            trailingContent = {
                                IconButton(onClick = { open(c.url) }) { Icon(Icons.AutoMirrored.Rounded.OpenInNew, str(R.string.settings_open_x, c.name)) }
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
            title = str(R.string.settings_confirm_your_pin_to_turn),
            onDismiss = { changePinStep = 0 },
            check = { vm.checkPin(it) },
            onSuccess = { changePinStep = 0; vm.setBiometric(true); vm.say(str(R.string.settings_fingerprint_unlock_on)) },
        )
        2 -> PinDialog(
            mode = PinMode.Create,
            title = null,
            onDismiss = { changePinStep = 0 },
            onSuccess = { code ->
                changePinStep = 0
                scope.launch { vm.createPin(code); vm.say(str(R.string.settings_pin_changed)) }
            },
        )
    }
    if (erase) {
        AlertDialog(
            onDismissRequest = { erase = false },
            icon = { Icon(Icons.Rounded.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(str(R.string.settings_erase_hidden_folders)) },
            text = {
                Text(
                    str(R.string.settings_a_forgotten_pin_can_t) +
                        str(R.string.settings_and_its_videos_then_set),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { erase = false; vm.eraseVault() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(str(R.string.settings_erase_everything_hidden)) }
            },
            dismissButton = { TextButton(onClick = { erase = false }) { Text(str(R.string.pin_pad_cancel)) } },
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

@Composable
private fun ScaleChoice(label: String, selected: com.ambr.reloaded.data.VideoScale, onSelect: (com.ambr.reloaded.data.VideoScale) -> Unit) {
    Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
        items(com.ambr.reloaded.data.VideoScale.entries.size) { i ->
            val s = com.ambr.reloaded.data.VideoScale.entries[i]
            androidx.compose.material3.FilterChip(
                selected = s == selected,
                onClick = { onSelect(s) },
                label = { Text(s.label) },
                leadingIcon = { Icon(s.icon, null, Modifier.size(18.dp)) },
            )
        }
    }
}
