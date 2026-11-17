package com.ambr.reloaded.ui.screens

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarExitDirection
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ambr.reloaded.R
import com.ambr.reloaded.data.VideoFolder
import com.ambr.reloaded.data.ViewMode
import com.ambr.reloaded.ui.AmbrViewModel
import com.ambr.reloaded.ui.components.AuroraBackground
import com.ambr.reloaded.ui.components.PinDialog
import com.ambr.reloaded.ui.components.PinMode
import com.ambr.reloaded.ui.components.PixelField
import com.ambr.reloaded.ui.components.ThumbTile
import com.ambr.reloaded.ui.components.VideoThumb
import com.ambr.reloaded.ui.theme.Ambr
import com.ambr.reloaded.util.Biometrics
import com.ambr.reloaded.util.formatBytes
import com.ambr.reloaded.util.pluralVideos
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    vm: AmbrViewModel,
    snackbar: SnackbarHostState,
    onOpenInfo: () -> Unit,
    onOpenDuplicates: () -> Unit,
    onOpenSettings: () -> Unit,
    onDownloadLink: () -> Unit,
    onOpenMeld: () -> Unit,
    onOpenCram: () -> Unit,
) {
    val folders by vm.folders.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val viewMode by vm.viewMode.collectAsStateWithLifecycle()
    val unlocked by vm.unlocked.collectAsStateWithLifecycle()
    val ambience by vm.ambience.collectAsStateWithLifecycle()
    val allFolder by vm.allFolder.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val toolbarScroll = FloatingToolbarDefaults.exitAlwaysScrollBehavior(exitDirection = FloatingToolbarExitDirection.Bottom)
    val real = folders.filter { !it.isVirtual }
    val totalVideos = real.sumOf { it.count }
    val totalBytes = real.sumOf { it.totalBytes }
    val lastFolder = folders.firstOrNull { it.path == vm.lastFolderPath && it.count > 0 }

    var options by remember { mutableStateOf<VideoFolder?>(null) }
    var renaming by remember { mutableStateOf<VideoFolder?>(null) }
    var deleting by remember { mutableStateOf<VideoFolder?>(null) }
    var hiding by remember { mutableStateOf<VideoFolder?>(null) }
    var creating by remember { mutableStateOf(false) }
    var pinMode by remember { mutableStateOf<PinMode?>(null) }
    var afterPin by remember { mutableStateOf<(() -> Unit)?>(null) }
    var menu by remember { mutableStateOf(false) }

    /** Runs [action] once a PIN exists, asking the user to create one first if needed. */
    fun withPin(action: () -> Unit) {
        if (vm.pinIsSet) action() else { afterPin = action; pinMode = PinMode.Create }
    }

    Box(Modifier.fillMaxSize()) {
        AuroraBackground(ambience)
        PixelField(
            Modifier.fillMaxSize(), count = 40, intensity = 0.6f,
            colors = (ambience.map { Color(it) } + Ambr.Pixels).take(6),
        )
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = 88.dp)) },
            modifier = Modifier
                .nestedScroll(scroll.nestedScrollConnection)
                .nestedScroll(toolbarScroll),
            topBar = {
                LargeFlexibleTopAppBar(
                    title = { Text("AMBR") },
                    subtitle = {
                        Text(if (loaded) "${pluralVideos(totalVideos)} · ${formatBytes(totalBytes)}" else "Scanning…")
                    },
                    navigationIcon = {
                        Image(
                            painterResource(R.drawable.ambr_logo), null,
                            Modifier
                                .padding(start = 12.dp, end = 4.dp)
                                .size(44.dp),
                        )
                    },
                    actions = {
                        IconButton(onClick = onOpenMeld) { Icon(Icons.Rounded.Hub, "Mind Meld") }
                        IconButton(onClick = onOpenInfo) { Icon(Icons.Rounded.PieChart, "Storage & stats") }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                            DropdownMenu(menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(20.dp)) {
                                DropdownMenuItem(
                                    text = { Text("Download from link") },
                                    leadingIcon = { Icon(Icons.Rounded.Download, null) },
                                    onClick = { menu = false; onDownloadLink() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Cram Session") },
                                    leadingIcon = { Icon(Icons.Rounded.Compress, null) },
                                    onClick = { menu = false; onOpenCram() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Find duplicates") },
                                    leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                                    onClick = { menu = false; onOpenDuplicates() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Settings") },
                                    leadingIcon = { Icon(Icons.Rounded.Settings, null) },
                                    onClick = { menu = false; onOpenSettings() },
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.9f),
                    ),
                    scrollBehavior = scroll,
                )
            },
        ) { padding ->
            val refreshState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = scanning && loaded,
                onRefresh = vm::refresh,
                state = refreshState,
                modifier = Modifier.padding(top = padding.calculateTopPadding()),
                indicator = {
                    PullToRefreshDefaults.LoadingIndicator(
                        state = refreshState,
                        isRefreshing = scanning && loaded,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                },
            ) {
                if (loaded && folders.isEmpty()) {
                    EmptyHome()
                } else {
                    val columns = when (viewMode) { ViewMode.List -> 1; ViewMode.Grid2 -> 2; ViewMode.Grid4 -> 4 }
                    val gap = if (viewMode == ViewMode.Grid4) 8.dp else 10.dp
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(gap),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (lastFolder != null) {
                            item(key = "hero", span = { GridItemSpan(maxLineSpan) }) {
                                ContinueCard(lastFolder, onClick = { vm.openFolder(lastFolder) }, modifier = Modifier.animateItem())
                            }
                        }
                        allFolder?.let { all ->
                            item(key = "shuffle-all", span = { GridItemSpan(maxLineSpan) }) {
                                ShuffleAllCard(all.count, onClick = vm::openShuffleAll, modifier = Modifier.animateItem())
                            }
                        }
                        if (lastFolder != null || allFolder != null) {
                            item(key = "label", span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    "Folders",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp),
                                )
                            }
                        }
                        items(folders, key = { it.path }) { folder ->
                            val open = { vm.openFolder(folder) }
                            val longPress = { if (!folder.isVirtual) options = folder }
                            val mod = Modifier.animateItem()
                            when (viewMode) {
                                ViewMode.List -> FolderRow(folder, open, longPress, mod)
                                ViewMode.Grid2 -> FolderTile(folder, open, longPress, mod, compact = false)
                                ViewMode.Grid4 -> FolderTile(folder, open, longPress, mod, compact = true)
                            }
                        }
                        if (unlocked && folders.none { it.isHidden }) {
                            item(key = "vault-hint", span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    "No hidden folders yet. Long-press a folder and turn on “Hidden folder”, or tap + to make one.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(24.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Floating toolbar: view mode, padlock, and a FAB for new folders. Slides away while scrolling down.
        HorizontalFloatingToolbar(
            expanded = true,
            scrollBehavior = toolbarScroll,
            colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
            floatingActionButton = {
                FloatingToolbarDefaults.VibrantFloatingActionButton(onClick = { creating = true }) {
                    Icon(Icons.Rounded.Add, "New folder")
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            ViewToggle(Icons.AutoMirrored.Rounded.ViewList, "List", viewMode == ViewMode.List) { vm.setViewMode(ViewMode.List) }
            ViewToggle(Icons.Rounded.GridView, "2 per row", viewMode == ViewMode.Grid2) { vm.setViewMode(ViewMode.Grid2) }
            ViewToggle(Icons.Rounded.Apps, "4 per row", viewMode == ViewMode.Grid4) { vm.setViewMode(ViewMode.Grid4) }
            VerticalDivider(
                Modifier.height(28.dp).padding(horizontal = 6.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.25f),
            )
            FilledIconToggleButton(
                checked = unlocked,
                onCheckedChange = {
                    when {
                        unlocked -> vm.lock()
                        !vm.pinIsSet -> pinMode = PinMode.Create
                        vm.biometric.value && Biometrics.available(context) -> Biometrics.prompt(
                            context, "Unlock hidden folders",
                            onSuccess = vm::unlockTrusted,
                            onFallback = { pinMode = PinMode.Unlock },
                        )
                        else -> pinMode = PinMode.Unlock
                    }
                },
                colors = toggleColors(),
            ) {
                AnimatedContent(
                    unlocked,
                    transitionSpec = { scaleIn(spring(0.4f, 500f)) + fadeIn() togetherWith fadeOut() },
                    label = "lock",
                ) { open ->
                    Icon(
                        if (open) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                        if (open) "Lock hidden folders" else "Show hidden folders",
                    )
                }
            }
        }
    }

    // ---------------------------------------------------------------- dialogs

    pinMode?.let { mode ->
        PinDialog(
            mode = mode,
            title = if (mode == PinMode.Create) "Set a PIN for hidden folders" else null,
            onDismiss = { pinMode = null; afterPin = null },
            check = { vm.unlock(it) },
            onSuccess = { code ->
                pinMode = null
                if (mode == PinMode.Create) scope.launch {
                    vm.createPin(code)
                    afterPin?.invoke(); afterPin = null
                }
            },
        )
    }
    if (creating) {
        CreateFolderDialog(
            onDismiss = { creating = false },
            onCreate = { name, hidden, noMedia ->
                creating = false
                if (hidden) withPin { vm.createFolder(name, true, true) } else vm.createFolder(name, false, noMedia)
            },
        )
    }
    options?.let { f ->
        FolderOptionsSheet(
            folder = f,
            onDismiss = { options = null },
            onNoMedia = { vm.setNoMedia(f, it); options = null },
            onHidden = { hide -> options = null; if (hide) withPin { hiding = f } else vm.setHidden(f, false) },
            onRename = { options = null; renaming = f },
            onCram = { options = null; vm.cramQuick(f.videos) },
            onDelete = { options = null; deleting = f },
        )
    }
    hiding?.let { f ->
        HideWarningDialog(f, onDismiss = { hiding = null }, onConfirm = { hiding = null; vm.setHidden(f, true) })
    }
    renaming?.let { f ->
        RenameFolderDialog(f, onDismiss = { renaming = null }, onConfirm = { renaming = null; vm.renameFolder(f, it) })
    }
    deleting?.let { f ->
        DeleteFolderDialog(f, onDismiss = { deleting = null }, onConfirm = { deleting = null; vm.deleteFolder(f) })
    }
}

@Composable
private fun toggleColors() = IconButtonDefaults.filledIconToggleButtonColors(
    containerColor = Color.Transparent,
    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    checkedContainerColor = MaterialTheme.colorScheme.onPrimaryContainer,
    checkedContentColor = MaterialTheme.colorScheme.primaryContainer,
)

@Composable
private fun ViewToggle(icon: ImageVector, label: String, checked: Boolean, onClick: () -> Unit) {
    FilledIconToggleButton(checked = checked, onCheckedChange = { onClick() }, colors = toggleColors()) {
        Icon(icon, label)
    }
}

@Composable
private fun ShuffleAllCard(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = MaterialShapes.Sunny.toShape(),
                color = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                modifier = Modifier.size(48.dp),
            ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Shuffle, null) } }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Shuffle All", style = MaterialTheme.typography.titleMedium)
                Text("${pluralVideos(count)} from every folder", style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
        }
    }
}

@Composable
private fun ContinueCard(folder: VideoFolder, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp),
    ) {
        Box(Modifier.fillMaxSize()) {
            VideoThumb(
                folder.cover,
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.55f }
                    .then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(18.dp) else Modifier),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.horizontalGradient(listOf(Ambr.Night.copy(alpha = 0.85f), Ambr.Berry.copy(alpha = 0.25f)))),
            )
            Row(Modifier.fillMaxSize().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Continue watching", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Text(folder.name, style = MaterialTheme.typography.headlineMedium, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${pluralVideos(folder.count)} · ${formatBytes(folder.totalBytes)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.75f),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Surface(
                    onClick = onClick,
                    shape = MaterialShapes.Cookie9Sided.toShape(),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(76.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.PlayArrow, "Play", Modifier.size(40.dp)) }
                }
            }
        }
    }
}

/** Little icons on a folder: heart (Favorites), lock (hidden), eye-off (.nomedia). */
@Composable
private fun FolderBadges(folder: VideoFolder, small: Boolean, modifier: Modifier = Modifier) {
    val icons = buildList {
        if (folder.isVirtual) add(Icons.Rounded.Favorite)
        if (folder.isHidden) add(Icons.Rounded.Lock)
        else if (folder.noMedia) add(Icons.Rounded.VisibilityOff)
    }
    if (icons.isEmpty()) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        icons.forEach {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.55f),
                contentColor = if (it == Icons.Rounded.Favorite) Ambr.Glow else Color.White,
            ) {
                Icon(it, null, Modifier.padding(if (small) 3.dp else 5.dp).size(if (small) 11.dp else 14.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderRow(folder: VideoFolder, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    val container = when {
        folder.isVirtual -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        folder.isHidden -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.78f)
    }
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = MaterialTheme.colorScheme.onSurface),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp)) {
                ThumbTile(folder.cover, Modifier.fillMaxSize(), corner = 18.dp)
                if (folder.cover == null) {
                    Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.Center))
                }
                FolderBadges(folder, small = true, modifier = Modifier.align(Alignment.TopStart).padding(3.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(folder.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${pluralVideos(folder.count)} · ${formatBytes(folder.totalBytes)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 6.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderTile(
    folder: VideoFolder,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean,
) {
    val shape = RoundedCornerShape(if (compact) 16.dp else 26.dp)
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(if (compact) 0.8f else 0.75f)
            .clip(shape)
            .background(
                if (folder.isVirtual) Brush.linearGradient(listOf(Ambr.Magenta, Ambr.Grape))
                else Brush.linearGradient(listOf(Ambr.Plum, Ambr.Ink)),
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        VideoThumb(folder.cover, Modifier.matchParentSize())
        if (folder.cover == null) {
            Icon(
                if (folder.isVirtual) Icons.Rounded.Favorite else Icons.Rounded.Folder, null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.Center).size(if (compact) 24.dp else 40.dp),
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))),
        )
        FolderBadges(folder, small = compact, modifier = Modifier.align(Alignment.TopStart).padding(if (compact) 4.dp else 8.dp))
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(if (compact) 6.dp else 12.dp),
        ) {
            Text(
                folder.name,
                style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (compact) "${folder.count}" else "${pluralVideos(folder.count)} · ${formatBytes(folder.totalBytes)}",
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.75f),
            )
        }
    }
}

@Composable
private fun EmptyHome() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Image(painterResource(R.drawable.ambr_logo), null, Modifier.fillMaxWidth(0.5f))
        Spacer(Modifier.height(24.dp))
        Text("The brain is hungry", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Copy videos into Internal storage › AMBR, share them to AMBR from any app, or tap + to make a folder.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
