package com.ambr.reloaded.ui.screens

import com.ambr.reloaded.util.str

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CallReceived
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.ambr.reloaded.R
import com.ambr.reloaded.data.Video
import com.ambr.reloaded.data.VideoFolder
import com.ambr.reloaded.meld.HostInfo
import com.ambr.reloaded.meld.MeldStage
import com.ambr.reloaded.meld.MeldViewModel
import com.ambr.reloaded.meld.MeldNet
import com.ambr.reloaded.meld.Nearby
import com.ambr.reloaded.meld.QrCode
import com.ambr.reloaded.meld.QrScanner
import com.ambr.reloaded.meld.RemoteFolder
import com.ambr.reloaded.meld.RemoteVideo
import com.ambr.reloaded.meld.TransferState
import com.ambr.reloaded.ui.AmbrViewModel
import com.ambr.reloaded.ui.components.PinDialog
import com.ambr.reloaded.ui.components.PinMode
import com.ambr.reloaded.ui.components.PixelField
import com.ambr.reloaded.ui.components.ThumbTile
import com.ambr.reloaded.ui.components.findActivity
import com.ambr.reloaded.ui.theme.Ambr
import com.ambr.reloaded.util.formatBytes
import com.ambr.reloaded.util.pluralVideos
import kotlin.math.sin

@Composable
fun MindMeldScreen(vm: AmbrViewModel, meld: MeldViewModel, snackbar: SnackbarHostState, onBack: () -> Unit) {
    val context = LocalContext.current
    val stage by meld.stage.collectAsStateWithLifecycle()
    val unlocked by vm.unlocked.collectAsStateWithLifecycle()
    var shareHidden by remember { mutableStateOf(false) }
    LaunchedEffect(unlocked, shareHidden) { meld.setIncludeHidden(unlocked && shareHidden) }
    LaunchedEffect(Unit) { meld.messages.collect { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(it) } }

    fun exit() {
        if (meld.changedLibrary) vm.refresh()
        meld.leave()
        onBack()
    }
    BackHandler {
        if (stage == MeldStage.Idle) exit() else meld.leave()
    }

    // Keep the screen awake while connected or transferring.
    val view = LocalView.current
    DisposableEffect(stage) {
        val w = view.context.findActivity()?.window
        if (stage != MeldStage.Idle) w?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { w?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // Permissions: hotspot needs "nearby devices" (13+) or location (≤12); scanning needs the camera.
    var pendingHotspot by remember { mutableStateOf(false) }
    val hotspotPerm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.NEARBY_WIFI_DEVICES else Manifest.permission.ACCESS_FINE_LOCATION
    val hotspotLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok && pendingHotspot) meld.host(useHotspot = true)
        pendingHotspot = false
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { meld.startJoining() }
    fun has(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(str(R.string.home_mind_meld)) },
                navigationIcon = { IconButton(onClick = { if (stage == MeldStage.Idle) exit() else meld.leave() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, str(R.string.cram_back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            AnimatedContent(stage, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "meld") { s ->
                when (s) {
                    MeldStage.Idle -> Landing(
                        meld = meld,
                        unlocked = unlocked,
                        shareHidden = shareHidden,
                        onShareHidden = { shareHidden = it },
                        onHost = { hotspot ->
                            if (hotspot && !has(hotspotPerm)) { pendingHotspot = true; hotspotLauncher.launch(hotspotPerm) }
                            else meld.host(hotspot)
                        },
                        onJoin = {
                            if (has(Manifest.permission.CAMERA)) meld.startJoining() else cameraLauncher.launch(Manifest.permission.CAMERA)
                        },
                    )
                    MeldStage.Starting, MeldStage.Connecting -> Busy(if (s == MeldStage.Starting) str(R.string.mind_meld_warming_up_the_brain_link) else str(R.string.mind_meld_connecting))
                    MeldStage.Hosting -> HostView(meld, onStop = { meld.leave() })
                    MeldStage.Joining -> JoinView(meld, cameraOk = has(Manifest.permission.CAMERA))
                    MeldStage.Connected -> Brains(meld, unlocked)
                }
            }
        }
    }
}

@Composable
private fun Busy(text: String) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        LoadingIndicator(Modifier.size(80.dp))
        Spacer(Modifier.height(16.dp))
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/** Two brains bobbing toward each other with a bridge of pixels between them. */
@Composable
private fun MeldArt(modifier: Modifier = Modifier) {
    val t by rememberInfiniteTransition(label = "meld").animateFloat(
        0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart), label = "t",
    )
    Box(modifier.height(170.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
        PixelField(Modifier.fillMaxWidth(0.55f).height(70.dp), count = 30, speed = 4f, seed = 3)
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(0.92f)) {
            Image(
                painterResource(R.drawable.ambr_logo), null,
                Modifier.size(130.dp).graphicsLayer { translationX = 14f * sin(t); rotationZ = 6f + 3f * sin(t) },
            )
            Image(
                painterResource(R.drawable.ambr_logo), null,
                Modifier.size(130.dp).graphicsLayer { translationX = -14f * sin(t); rotationZ = -6f - 3f * sin(t); scaleX = -1f },
            )
        }
    }
}

@Composable
private fun Landing(
    meld: MeldViewModel,
    unlocked: Boolean,
    shareHidden: Boolean,
    onShareHidden: (Boolean) -> Unit,
    onHost: (hotspot: Boolean) -> Unit,
    onJoin: () -> Unit,
) {
    val context = LocalContext.current
    var hotspot by remember { mutableStateOf(!MeldNet.onWifi(context)) }
    var name by remember { mutableStateOf(meld.myName) }
    var renaming by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MeldArt()
        Text(
            str(R.string.mind_meld_swap_videos_with_a_friend),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = { renaming = true }) {
            Text(str(R.string.mind_meld_you_appear_as_x, name))
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Rounded.Edit, str(R.string.rename), Modifier.size(16.dp))
        }
        Spacer(Modifier.height(14.dp))

        Card(shape = RoundedCornerShape(32.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(18.dp)) {
                Text(str(R.string.mind_meld_start_a_mind_meld), style = MaterialTheme.typography.titleLarge)
                Text(
                    str(R.string.mind_meld_shows_a_qr_code_for),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(!hotspot, { hotspot = false }, SegmentedButtonDefaults.itemShape(0, 2), icon = {
                        Icon(Icons.Rounded.Wifi, null, Modifier.size(18.dp))
                    }) { Text(str(R.string.mind_meld_same_wi_fi)) }
                    SegmentedButton(hotspot, { hotspot = true }, SegmentedButtonDefaults.itemShape(1, 2), icon = {
                        Icon(Icons.Rounded.WifiTethering, null, Modifier.size(18.dp))
                    }) { Text(str(R.string.mind_meld_make_a_hotspot)) }
                }
                if (unlocked) {
                    ListItem(
                        leadingContent = { Icon(Icons.Rounded.Lock, null) },
                        headlineContent = { Text(str(R.string.mind_meld_include_hidden_folders)) },
                        supportingContent = { Text(str(R.string.mind_meld_off_unless_you_switch_it)) },
                        trailingContent = { Switch(checked = shareHidden, onCheckedChange = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.toggleable(shareHidden, role = Role.Switch, onValueChange = onShareHidden),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { onHost(hotspot) },
                    modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight),
                ) { Text(str(R.string.mind_meld_start), style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight)) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(shape = RoundedCornerShape(32.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(18.dp)) {
                Text(str(R.string.mind_meld_join_a_mind_meld), style = MaterialTheme.typography.titleLarge)
                Text(
                    str(R.string.mind_meld_scan_the_qr_code_on),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = onJoin,
                    modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight),
                ) {
                    Icon(Icons.Rounded.QrCodeScanner, null)
                    Spacer(Modifier.width(8.dp))
                    Text(str(R.string.mind_meld_scan_to_join), style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (renaming) {
        var draft by remember { mutableStateOf(name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(str(R.string.mind_meld_your_mind_meld_name)) },
            text = { OutlinedTextField(draft, { draft = it.take(40) }, singleLine = true, shape = RoundedCornerShape(16.dp)) },
            confirmButton = { TextButton(onClick = { meld.setMyName(draft); name = meld.myName; renaming = false }) { Text(str(R.string.mind_meld_save)) } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text(str(R.string.pin_pad_cancel)) } },
        )
    }
}

@Composable
private fun HostView(meld: MeldViewModel, onStop: () -> Unit) {
    val host by meld.host.collectAsStateWithLifecycle()
    val peers by meld.peers.collectAsStateWithLifecycle()
    val h: HostInfo = host ?: return Busy(str(R.string.cram_starting))
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = RoundedCornerShape(36.dp), color = Color.White, modifier = Modifier.padding(top = 8.dp)) {
            Box(Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
                QrCode(h.invite.toUri(), Modifier.size(240.dp))
                Surface(shape = RoundedCornerShape(16.dp), color = Color.White, modifier = Modifier.size(64.dp)) {
                    Image(painterResource(R.drawable.ambr_logo), null, Modifier.padding(6.dp))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(str(R.string.mind_meld_friend_ambr_mind_meld_scan), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(str(R.string.mind_meld_code), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            h.code.chunked(3).joinToString(" "),
            style = MaterialTheme.typography.displayMedium.copy(letterSpacing = 6.sp),
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(14.dp))

        Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Computer, null)
                    Spacer(Modifier.width(10.dp))
                    Text(str(R.string.mind_meld_on_a_computer), style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(8.dp))
                h.hotspot?.let { hs ->
                    Text(str(R.string.mind_meld_1_join_the_wi_fi), style = MaterialTheme.typography.bodyMedium)
                    Mono("${hs.ssid}  ·  ${hs.pass}")
                    Text(str(R.string.mind_meld_2_open), style = MaterialTheme.typography.bodyMedium)
                } ?: Text(str(R.string.mind_meld_on_the_same_wi_fi), style = MaterialTheme.typography.bodyMedium)
                h.urls.firstOrNull()?.let { Mono(it) }
                Text(str(R.string.mind_meld_and_type_the_code_above), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (peers.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            peers.forEach { p ->
                ListItem(
                    leadingContent = { Icon(if (p.isBrowser) Icons.Rounded.Computer else Icons.Rounded.Folder, null) },
                    headlineContent = { Text(p.name) },
                    supportingContent = { Text(str(R.string.mind_meld_connected)) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        } else {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LoadingIndicator(Modifier.size(36.dp))
                Spacer(Modifier.width(8.dp))
                Text(str(R.string.mind_meld_waiting_for_a_brain_to), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onStop) { Text(str(R.string.mind_meld_stop_mind_meld)) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Mono(text: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.padding(vertical = 6.dp)) {
        Text(text, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
    }
}

@Composable
private fun JoinView(meld: MeldViewModel, cameraOk: Boolean) {
    val nearby by meld.nearby.collectAsStateWithLifecycle()
    var handled by remember { mutableStateOf(false) }
    var codeFor by remember { mutableStateOf<Nearby?>(null) }
    val scan by rememberInfiniteTransition(label = "scan").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse), label = "line",
    )
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(40.dp))
                .background(Ambr.Ink),
            contentAlignment = Alignment.Center,
        ) {
            if (cameraOk) {
                QrScanner(onResult = { text -> if (!handled) { handled = true; meld.join(text) } }, modifier = Modifier.fillMaxSize())
                // Viewfinder frame with a glowing line sweeping up and down.
                BoxWithConstraints(
                    Modifier
                        .fillMaxSize()
                        .padding(36.dp)
                        .border(3.dp, Ambr.Glow.copy(alpha = 0.8f), RoundedCornerShape(28.dp)),
                ) {
                    val travel = maxHeight - 3.dp
                    Box(
                        Modifier
                            .padding(horizontal = 12.dp)
                            .fillMaxWidth()
                            .height(3.dp)
                            .offset(y = travel * scan)
                            .background(Ambr.Glow, RoundedCornerShape(2.dp)),
                    )
                }
            } else {
                Text(
                    str(R.string.mind_meld_camera_permission_is_needed_to),
                    color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(str(R.string.mind_meld_point_at_the_qr_code), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        Text(str(R.string.mind_meld_nearby_on_this_wi_fi), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
        if (nearby.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                LoadingIndicator(Modifier.size(32.dp))
                Spacer(Modifier.width(8.dp))
                Text(str(R.string.mind_meld_looking), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        nearby.forEach { n ->
            ListItem(
                leadingContent = { Image(painterResource(R.drawable.ambr_logo), null, Modifier.size(40.dp)) },
                headlineContent = { Text(n.name) },
                supportingContent = { Text(str(R.string.mind_meld_tap_and_enter_the_code)) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { codeFor = n },
            )
        }
        Spacer(Modifier.height(24.dp))
    }
    codeFor?.let { n ->
        PinDialog(
            mode = PinMode.Verify,
            title = str(R.string.mind_meld_code_on_x, n.name),
            length = 6,
            onDismiss = { codeFor = null },
            check = { meld.joinWithCode(n, it) },
            onSuccess = { codeFor = null },
        )
    }
}

/** Connected: browse both libraries side by side and move videos either way. */
@Composable
private fun Brains(meld: MeldViewModel, unlocked: Boolean) {
    val remote by meld.remote.collectAsStateWithLifecycle()
    val remoteName by meld.remoteName.collectAsStateWithLifecycle()
    val theirs by meld.remoteFolders.collectAsStateWithLifecycle()
    val theirVideos by meld.remoteVideos.collectAsStateWithLifecycle()
    val mine by meld.mine.collectAsStateWithLifecycle()
    val transfers by meld.transfers.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(0) }

    var pickedFolders by remember { mutableStateOf(emptySet<String>()) }           // their folder ids
    var pickedVideos by remember { mutableStateOf(emptyMap<String, Set<String>>()) } // their folder id -> names
    var myPicked by remember { mutableStateOf(emptySet<String>()) }               // my video paths
    var open by remember { mutableStateOf(emptySet<String>()) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(str(R.string.mind_meld_you_x, remoteName), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            TextButton(onClick = { meld.leave() }) { Text(str(R.string.mind_meld_leave)) }
        }
        PrimaryTabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
            Tab(tab == 0, { tab = 0 }, text = { Text(str(R.string.mind_meld_their_brain)) }, icon = { Icon(Icons.Rounded.CallReceived, null) })
            Tab(tab == 1, { tab = 1 }, text = { Text(str(R.string.mind_meld_your_brain)) }, icon = { Icon(Icons.Rounded.Send, null) })
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (tab == 0) {
                if (theirs.isEmpty()) item { Text(str(R.string.mind_meld_nothing_shared_yet), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(theirs, key = { "r" + it.id }) { f ->
                    val whole = f.id in pickedFolders
                    val expanded = f.id in open
                    FolderPickCard(
                        title = f.name, sub = "${pluralVideos(f.count)} · ${formatBytes(f.bytes)}", hidden = f.hidden,
                        thumb = { m -> AsyncImage(f.cover?.let { remote?.thumbUrl(f.id, it) }, null, m, contentScale = ContentScale.Crop) },
                        checked = whole, expanded = expanded,
                        onCheck = { pickedFolders = if (it) pickedFolders + f.id else pickedFolders - f.id },
                        onExpand = {
                            open = if (expanded) open - f.id else open + f.id
                            if (!expanded && f.id !in theirVideos) meld.loadRemoteVideos(f.id)
                        },
                    ) {
                        val vids = theirVideos[f.id]
                        if (vids == null) LoadingIndicator(Modifier.size(32.dp))
                        vids?.forEach { v ->
                            val on = whole || v.name in pickedVideos[f.id].orEmpty()
                            VideoPickRow(
                                title = v.name.substringBeforeLast('.'), sub = formatBytes(v.size), checked = on, enabled = !whole,
                                thumb = { m -> AsyncImage(remote?.thumbUrl(f.id, v.name), null, m, contentScale = ContentScale.Crop) },
                                onCheck = { c ->
                                    val cur = pickedVideos[f.id].orEmpty()
                                    pickedVideos = pickedVideos + (f.id to (if (c) cur + v.name else cur - v.name))
                                },
                            )
                        }
                    }
                }
            } else {
                items(mine, key = { "m" + it.path }) { f ->
                    val paths = f.videos.map { it.path }.toSet()
                    val all = paths.isNotEmpty() && myPicked.containsAll(paths)
                    val expanded = f.path in open
                    FolderPickCard(
                        title = f.name, sub = "${pluralVideos(f.count)} · ${formatBytes(f.totalBytes)}", hidden = f.isHidden,
                        thumb = { m -> ThumbTile(f.cover, m, corner = 0.dp) },
                        checked = all, expanded = expanded,
                        onCheck = { myPicked = if (it) myPicked + paths else myPicked - paths },
                        onExpand = { open = if (expanded) open - f.path else open + f.path },
                    ) {
                        f.videos.forEach { v ->
                            VideoPickRow(
                                title = v.title, sub = formatBytes(v.sizeBytes), checked = v.path in myPicked, enabled = true,
                                thumb = { m -> ThumbTile(v, m, corner = 0.dp) },
                                onCheck = { c -> myPicked = if (c) myPicked + v.path else myPicked - v.path },
                            )
                        }
                    }
                }
            }
        }

        TransfersBar(transfers, onRetry = meld::retryFailed, onClear = meld::clearFinished)

        // Action button for the current tab.
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            val n: Int
            val label: String
            if (tab == 0) {
                val folderVideos = theirs.filter { it.id in pickedFolders }.sumOf { it.count }
                val singles = pickedVideos.filterKeys { it !in pickedFolders }.values.sumOf { it.size }
                n = folderVideos + singles
                label = "Take ${pluralVideos(n)}"
            } else {
                n = myPicked.size
                label = "Send ${pluralVideos(n)}"
            }
            Button(
                enabled = n > 0,
                onClick = {
                    if (tab == 0) {
                        val singles = pickedVideos.filterKeys { it !in pickedFolders }.mapNotNull { (id, names) ->
                            val f = theirs.firstOrNull { it.id == id } ?: return@mapNotNull null
                            f to theirVideos[id].orEmpty().filter { it.name in names }
                        }.toMap()
                        meld.take(theirs.filter { it.id in pickedFolders }, singles, hiddenToVault = unlocked)
                        pickedFolders = emptySet(); pickedVideos = emptyMap()
                    } else {
                        val pairs: List<Pair<VideoFolder, Video>> = mine.flatMap { f -> f.videos.filter { it.path in myPicked }.map { f to it } }
                        meld.send(pairs)
                        myPicked = emptySet()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp)
                    .height(ButtonDefaults.MediumContainerHeight),
            ) {
                Icon(if (tab == 0) Icons.Rounded.CallReceived else Icons.Rounded.Send, null)
                Spacer(Modifier.width(8.dp))
                Text(label, style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
            }
        }
    }
}

@Composable
private fun FolderPickCard(
    title: String,
    sub: String,
    hidden: Boolean,
    thumb: @Composable (Modifier) -> Unit,
    checked: Boolean,
    expanded: Boolean,
    onCheck: (Boolean) -> Unit,
    onExpand: () -> Unit,
    content: @Composable () -> Unit,
) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column {
            Row(Modifier.clickable(onClick = onExpand).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = checked, onCheckedChange = onCheck)
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Ambr.Plum)) { thumb(Modifier.matchParentSize()) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (hidden) Icon(Icons.Rounded.Lock, null, Modifier.size(14.dp).padding(end = 4.dp))
                        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, if (expanded) str(R.string.mind_meld_collapse) else str(R.string.mind_meld_show_videos))
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp)) { content() }
            }
        }
    }
}

@Composable
private fun VideoPickRow(title: String, sub: String, checked: Boolean, enabled: Boolean, thumb: @Composable (Modifier) -> Unit, onCheck: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheck)
            .padding(vertical = 4.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled, modifier = Modifier.padding(horizontal = 12.dp))
        Box(Modifier.size(width = 36.dp, height = 48.dp).clip(RoundedCornerShape(10.dp)).background(Ambr.Plum)) { thumb(Modifier.matchParentSize()) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TransfersBar(transfers: List<com.ambr.reloaded.meld.Transfer>, onRetry: () -> Unit, onClear: () -> Unit) {
    if (transfers.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    val active = transfers.firstOrNull { it.state == TransferState.Active }
    val left = transfers.count { it.state == TransferState.Queued || it.state == TransferState.Active }
    val failed = transfers.count { it.state == TransferState.Failed }
    val doneCount = transfers.count { it.state == TransferState.Done || it.state == TransferState.Skipped }
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp)) {
            Text(
                when {
                    active != null -> "${if (active.sending) "Sending" else "Taking"} ${active.name}"
                    failed > 0 -> str(R.string.mind_meld_x_didn_t_finish, failed)
                    else -> str(R.string.mind_meld_all_done_x_moved, doneCount)
                },
                style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (active != null) {
                Spacer(Modifier.height(8.dp))
                LinearWavyProgressIndicator(progress = { if (active.total > 0) active.done / active.total.toFloat() else 0f }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Text(str(R.string.mind_meld_x_left_x_of_x, left, formatBytes(active.done), formatBytes(active.total)), style = MaterialTheme.typography.bodySmall)
            }
            Row {
                if (failed > 0) TextButton(onClick = onRetry) { Text(str(R.string.mind_meld_retry)) }
                if (left == 0) TextButton(onClick = onClear) { Text(str(R.string.mind_meld_clear)) }
            }
            AnimatedVisibility(expanded) {
                Column {
                    transfers.takeLast(30).forEach { t ->
                        Text(
                            "${if (t.sending) "↑" else "↓"} ${t.name} · " + when (t.state) {
                                TransferState.Queued -> str(R.string.meld_transfer_waiting)
                                TransferState.Active -> "${(100 * t.done / t.total.coerceAtLeast(1))}%"
                                TransferState.Done -> str(R.string.meld_transfer_done)
                                TransferState.Skipped -> str(R.string.mind_meld_already_there)
                                TransferState.Failed -> str(R.string.mind_meld_failed_x, t.error ?: "?")
                            },
                            style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
