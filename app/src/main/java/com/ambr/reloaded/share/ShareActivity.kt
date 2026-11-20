package com.ambr.reloaded.share

import com.ambr.reloaded.util.str

import android.Manifest
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ambr.reloaded.MainActivity
import com.ambr.reloaded.R
import com.ambr.reloaded.data.PinLock
import com.ambr.reloaded.data.Prefs
import com.ambr.reloaded.data.StorageAccess
import com.ambr.reloaded.data.VideoFolder
import com.ambr.reloaded.data.VideoRepository
import com.ambr.reloaded.ui.components.PinDialog
import com.ambr.reloaded.ui.components.PinMode
import com.ambr.reloaded.ui.theme.AmbrTheme
import com.ambr.reloaded.util.Biometrics
import com.ambr.reloaded.util.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** What was shared to us. */
sealed interface Incoming {
    data class Link(val url: String?) : Incoming
    data class Files(val uris: List<Uri>) : Incoming
}

class ShareActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val incoming = parse(intent)
        setContent {
            AmbrTheme {
                ShareSheet(incoming, onClose = { finish() })
            }
        }
    }

    private fun parse(intent: Intent): Incoming {
        if (intent.action == ACTION_PASTE_LINK) {
            val clip = getSystemService(ClipboardManager::class.java)?.primaryClip?.getItemAt(0)?.text?.toString()
            return Incoming.Link(clip?.let(::firstUrl))
        }
        @Suppress("DEPRECATION")
        val streams: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND_MULTIPLE ->
                if (Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
                else intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
            Intent.ACTION_SEND -> listOfNotNull(
                if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else intent.getParcelableExtra(Intent.EXTRA_STREAM),
            )
            else -> emptyList()
        }
        val isVideo = intent.type?.startsWith("video/") == true
        if (streams.isNotEmpty() && isVideo) return Incoming.Files(streams)
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)
        return Incoming.Link(text?.let(::firstUrl))
    }

    companion object {
        const val ACTION_PASTE_LINK = "com.ambr.reloaded.PASTE_LINK"
        private val URL = Regex("""https?://[^\s<>"']+""")
        fun firstUrl(text: String) = URL.find(text)?.value?.trimEnd('.', ',', ')', ']')
    }
}

private sealed interface Preview {
    data object Idle : Preview
    data object Loading : Preview
    data class Ready(val info: YtDlp.Preview) : Preview
    data class Failed(val reason: String) : Preview
}

@Composable
private fun ShareSheet(incoming: Incoming, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { Prefs(context) }
    val repo = remember { VideoRepository(context) }
    val pin = remember { PinLock(prefs) }
    val hasAccess = remember { StorageAccess.has(context) }

    var url by remember { mutableStateOf((incoming as? Incoming.Link)?.url.orEmpty()) }
    var preview by remember { mutableStateOf<Preview>(Preview.Idle) }
    var height by remember { mutableStateOf(prefs.downloadHeight) }
    var unlocked by remember { mutableStateOf(false) }
    var folders by remember { mutableStateOf<List<VideoFolder>>(emptyList()) }
    var target by remember { mutableStateOf<String?>(null) }
    var askPin by remember { mutableStateOf(false) }
    var newFolder by remember { mutableStateOf(false) }
    var copying by remember { mutableStateOf<Float?>(null) }
    var retry by remember { mutableStateOf(0) }
    val signInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { retry++ }

    suspend fun reload() {
        val scanned = repo.scan(includeVault = unlocked)
        val root = VideoFolder(repo.root.absolutePath, "AMBR (Root)", emptyList(), com.ambr.reloaded.data.FolderKind.Root)
        folders = listOf(root) + scanned.filter { !it.isRoot }
        if (target == null || folders.none { it.path == target }) {
            target = prefs.lastDownloadFolder?.takeIf { p -> folders.any { it.path == p } } ?: root.path
        }
    }
    LaunchedEffect(unlocked) { if (hasAccess) reload() }

    // Fetch title/thumbnail as soon as we have a link.
    LaunchedEffect(url, retry) {
        if (incoming !is Incoming.Link || url.isBlank() || !url.startsWith("http")) { preview = Preview.Idle; return@LaunchedEffect }
        preview = Preview.Loading
        preview = runCatching { YtDlp.info(context, url, height) }.fold(
            { Preview.Ready(it) },
            {
                android.util.Log.e("AMBR", "Preview failed for $url", it)
                Preview.Failed(
                    it.message?.lines()?.lastOrNull { l -> l.isNotBlank() }?.substringAfter("ERROR:")?.trim()?.take(140)
                        ?: it::class.java.simpleName,
                )
            },
        )
    }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    val slide = remember { Animatable(400f) }
    LaunchedEffect(Unit) { slide.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 380f)) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationY = slide.value }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        ) {
            Column(
                Modifier
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 36.dp, height = 4.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp)),
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.ambr_logo), null, Modifier.size(40.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(str(R.string.share_save_to_ambr), style = MaterialTheme.typography.headlineSmall)
                }
                Spacer(Modifier.height(14.dp))

                if (!hasAccess) {
                    NeedsAccess(onOpen = {
                        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        onClose()
                    })
                    return@Column
                }

                when (incoming) {
                    is Incoming.Link -> if (!YtDlp.available) {
                        Text(
                            str(R.string.share_this_version_of_ambr_can) +
                                str(R.string.share_the_full_version_with_link),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/amruthwo/AMBR/releases")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            onClose()
                        }) { Text(str(R.string.share_get_the_full_version)) }
                        return@Column
                    } else {
                        if (incoming.url == null) {
                            OutlinedTextField(
                                value = url, onValueChange = { url = it.trim() },
                                label = { Text(str(R.string.share_paste_a_video_link)) },
                                leadingIcon = { Icon(Icons.Rounded.Link, null) },
                                singleLine = true, shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                        LinkPreview(preview, url, onSignIn = { site -> signInLauncher.launch(SiteLoginActivity.intent(context, site)) })
                        Spacer(Modifier.height(14.dp))
                        Text(str(R.string.settings_quality), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        val options = listOf(480 to "480p", 720 to "720p", 1080 to "1080p", 0 to str(R.string.settings_best))
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            options.forEachIndexed { i, (h, label) ->
                                SegmentedButton(
                                    selected = height == h,
                                    onClick = { height = h },
                                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                                ) { Text(label) }
                            }
                        }
                    }
                    is Incoming.Files -> FilesPreview(incoming.uris)
                }

                Spacer(Modifier.height(14.dp))
                Text(str(R.string.share_save_to), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(folders, key = { it.path }) { f ->
                        FilterChip(
                            selected = target == f.path,
                            onClick = { target = f.path },
                            label = { Text(f.name, maxLines = 1) },
                            leadingIcon = { Icon(if (f.isHidden) Icons.Rounded.Lock else Icons.Rounded.Folder, null, Modifier.size(18.dp)) },
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                    item {
                        AssistChip(
                            onClick = { newFolder = true },
                            label = { Text(str(R.string.share_new)) },
                            leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)) },
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                    if (pin.isSet && !unlocked) item {
                        AssistChip(
                            onClick = {
                                if (prefs.biometric && Biometrics.available(context)) {
                                    Biometrics.prompt(context, str(R.string.home_show_hidden_folders), onSuccess = { unlocked = true }, onFallback = { askPin = true })
                                } else askPin = true
                            },
                            label = { Text(str(R.string.share_hidden)) },
                            leadingIcon = { Icon(Icons.Rounded.Lock, null, Modifier.size(18.dp)) },
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                copying?.let {
                    LinearWavyProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onClose) { Text(str(R.string.pin_pad_cancel)) }
                    Spacer(Modifier.weight(1f))
                    val folder = folders.firstOrNull { it.path == target }
                    val ready = folder != null && copying == null && when (incoming) {
                        is Incoming.Link -> url.startsWith("http")
                        is Incoming.Files -> true
                    }
                    Button(
                        enabled = ready,
                        onClick = {
                            val f = folder ?: return@Button
                            prefs.lastDownloadFolder = f.path
                            when (incoming) {
                                is Incoming.Link -> {
                                    if (Build.VERSION.SDK_INT >= 33 && !Notify.canNotify(context)) {
                                        notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                    prefs.downloadHeight = height
                                    val title = (preview as? Preview.Ready)?.info?.title
                                    DownloadWorker.enqueue(context, url, File(f.path), f.name, title, height, f.isHidden)
                                    Toast.makeText(context, str(R.string.share_downloading_to_x, f.name), Toast.LENGTH_SHORT).show()
                                    onClose()
                                }
                                is Incoming.Files -> scope.launch {
                                    copying = 0f
                                    val n = DirectImport.copy(context, incoming.uris, File(f.path), f.isHidden) { d, t ->
                                        copying = if (t == 0) 1f else d / t.toFloat()
                                    }
                                    Toast.makeText(
                                        context,
                                        if (n == incoming.uris.size) str(R.string.downloader_saved_to_x, f.name) else str(R.string.share_saved_x_of_x, n, incoming.uris.size),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    onClose()
                                }
                            }
                        },
                        modifier = Modifier.height(ButtonDefaults.MediumContainerHeight),
                        contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
                    ) {
                        Icon(if (incoming is Incoming.Link) Icons.Rounded.Download else Icons.Rounded.SaveAlt, null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (incoming is Incoming.Link) str(R.string.share_download) else str(R.string.mind_meld_save),
                            style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight),
                        )
                    }
                }
            }
        }
    }

    if (askPin) {
        PinDialog(
            mode = PinMode.Unlock,
            onDismiss = { askPin = false },
            check = { withContext(Dispatchers.Default) { pin.check(it) } },
            onSuccess = { askPin = false; unlocked = true },
        )
    }
    if (newFolder) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newFolder = false },
            title = { Text(str(R.string.folder_new_folder)) },
            text = {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(str(R.string.folder_folder_name)) }, shape = RoundedCornerShape(16.dp))
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    newFolder = false
                    scope.launch {
                        repo.createFolder(name, hidden = false, noMedia = false).onSuccess { target = it.absolutePath }
                        reload()
                    }
                }) { Text(str(R.string.folder_create)) }
            },
            dismissButton = { TextButton(onClick = { newFolder = false }) { Text(str(R.string.pin_pad_cancel)) } },
        )
    }
}

@Composable
private fun LinkPreview(preview: Preview, url: String, onSignIn: (Site) -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        AnimatedContent(preview, label = "preview", contentKey = { it::class }) { p ->
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                when (p) {
                    Preview.Idle -> {
                        Icon(Icons.Rounded.Link, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (url.isBlank()) str(R.string.share_no_link_found_paste_one) else url,
                            style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Preview.Loading -> {
                        LoadingIndicator(Modifier.size(48.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(str(R.string.share_peeking_at_the_link), style = MaterialTheme.typography.titleSmall)
                            Text(
                                str(R.string.share_the_first_time_takes_a),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    is Preview.Ready -> {
                        AsyncImage(
                            model = p.info.thumbnail, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 112.dp, height = 72.dp)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(14.dp))
                                .graphicsLayer { clip = true; shape = RoundedCornerShape(14.dp) },
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.info.title ?: url, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val meta = listOfNotNull(
                                p.info.uploader,
                                p.info.durationSec.takeIf { it > 0 }?.let { formatDuration(it * 1000L) },
                            ).joinToString(" · ")
                            if (meta.isNotEmpty()) {
                                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    is Preview.Failed -> {
                        Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(12.dp))
                        val site = Site.forUrl(url)
                        if (site != null && SiteCookies.isAuthError(p.reason)) Column {
                            Text(str(R.string.downloader_x_wants_you_to_sign, site.label), style = MaterialTheme.typography.titleSmall)
                            Text(
                                str(R.string.share_sign_in_once_inside_ambr),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { onSignIn(site) }) { Text(str(R.string.share_sign_in_to_x, site.label)) }
                        } else Column {
                            Text(str(R.string.share_couldn_t_preview_this_link), style = MaterialTheme.typography.titleSmall)
                            Text(
                                str(R.string.share_x_nyou_can_still_try, p.reason),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 4, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilesPreview(uris: List<Uri>) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = uris.first(), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 56.dp, height = 72.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(14.dp))
                    .graphicsLayer { clip = true; shape = RoundedCornerShape(14.dp) },
            )
            Spacer(Modifier.width(12.dp))
            Text(
                if (uris.size == 1) str(R.string.share_1_video_ready_to_copy) else str(R.string.share_x_videos_ready_to_copy, uris.size),
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

@Composable
private fun NeedsAccess(onOpen: () -> Unit) {
    Column {
        Text(
            str(R.string.share_ambr_needs_file_access_before),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onOpen) { Text(str(R.string.share_open_ambr)) }
    }
}
