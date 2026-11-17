package com.ambr.reloaded.ui.screens

import com.ambr.reloaded.util.str

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import com.ambr.reloaded.R
import com.ambr.reloaded.data.ShareOut
import com.ambr.reloaded.data.Video
import com.ambr.reloaded.data.VideoFolder
import com.ambr.reloaded.ui.FeedState
import com.ambr.reloaded.ui.components.ImmersiveMode
import com.ambr.reloaded.ui.components.PixelField
import com.ambr.reloaded.ui.theme.Ambr

@Composable
fun FeedScreen(
    feed: FeedState,
    folders: List<VideoFolder>,
    muted: Boolean,
    favorites: Set<String>,
    holdSpeed: Float,
    seekStep: Int,
    tabletop: FoldingFeature?,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onPageSettled: (Int) -> Unit,
    onReshuffle: () -> Unit,
    onToggleMute: () -> Unit,
    onLike: (Video) -> Unit,
    onToggleFavorite: (Video) -> Unit,
    onRename: (Video, String) -> Unit,
    onMove: (Video, VideoFolder) -> Unit,
    onDelete: (Video) -> Unit,
    onCram: (Video) -> Unit,
) {
    ImmersiveMode()
    BackHandler(onBack = onBack)

    val playlist = feed.playlist
    // A fresh pager per shuffle generation, so a reshuffle starts from the top.
    val pagerState = key(feed.generation) {
        rememberPagerState(initialPage = feed.startIndex) { playlist.size + 1 }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect(onPageSettled)
    }

    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Video?>(null) }
    var moving by remember { mutableStateOf<Video?>(null) }
    var deleting by remember { mutableStateOf<Video?>(null) }
    val dialogOpen = menuOpen || renaming != null || moving != null || deleting != null

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (playlist.isEmpty()) {
            EmptyFolder(feed.folder.name, onBack)
        } else {
            VerticalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                key = { if (it < playlist.size) playlist[it].path else "end-${feed.generation}" },
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                if (page < playlist.size) {
                    VideoPage(
                        video = playlist[page],
                        selected = pagerState.settledPage == page,
                        suspended = dialogOpen,
                        muted = muted,
                        favorite = playlist[page].path in favorites,
                        holdSpeed = holdSpeed,
                        seekStepSeconds = seekStep,
                        tabletop = tabletop,
                        onToggleMute = onToggleMute,
                        onLike = { onLike(playlist[page]) },
                        onMenuOpenChange = { menuOpen = it },
                        onAction = { action ->
                            val v = playlist[page]
                            when (action) {
                                VideoAction.Favorite -> onToggleFavorite(v)
                                VideoAction.Share -> ShareOut.share(context, listOf(v))
                                VideoAction.Rename -> renaming = v
                                VideoAction.Move -> moving = v
                                VideoAction.Delete -> deleting = v
                                VideoAction.Cram -> onCram(v)
                            }
                        },
                    )
                } else {
                    AllWatchedPage(count = playlist.size, onReshuffle = onReshuffle, onBack = onBack)
                }
            }
        }

        // Top bar: back, folder name, position.
        Row(
            Modifier
                .fillMaxWidth()
                .displayCutoutPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(
                onClick = onBack,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.38f), contentColor = Color.White,
                ),
            ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, str(R.string.feed_back_to_folders)) }
            Spacer(Modifier.width(8.dp))
            Surface(shape = RoundedCornerShape(50), color = Color.Black.copy(alpha = 0.38f), contentColor = Color.White) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (feed.folder.isHidden) Icon(Icons.Rounded.Lock, null, Modifier.size(16.dp).padding(end = 4.dp))
                    if (feed.folder.isVirtual) Icon(Icons.Rounded.Favorite, null, Modifier.size(16.dp).padding(end = 4.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(
                        feed.folder.name,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (playlist.isNotEmpty() && pagerState.currentPage < playlist.size) {
                        Text(
                            "  ${pagerState.currentPage + 1}/${playlist.size}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.TopCenter)
                .displayCutoutPadding()
                .padding(top = 64.dp),
        )
    }

    renaming?.let { v ->
        RenameDialog(v, onDismiss = { renaming = null }, onConfirm = { onRename(v, it); renaming = null })
    }
    moving?.let { v ->
        MoveSheet(
            video = v,
            folders = folders.filter { it.path != v.folderPath && !it.isVirtual },
            onDismiss = { moving = null },
            onMove = { onMove(v, it); moving = null },
        )
    }
    deleting?.let { v ->
        DeleteDialog(v, onDismiss = { deleting = null }, onConfirm = { onDelete(v); deleting = null })
    }
}

@Composable
private fun AllWatchedPage(count: Int, onReshuffle: () -> Unit, onBack: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Ambr.Night, Ambr.Plum, Ambr.Night))),
        contentAlignment = Alignment.Center,
    ) {
        PixelField(Modifier.fillMaxSize(), count = 70, speed = 3f)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Image(painterResource(R.drawable.ambr_logo), null, Modifier.fillMaxWidth(0.45f))
            Spacer(Modifier.height(24.dp))
            Text(str(R.string.feed_brain_fully_rotted), style = MaterialTheme.typography.headlineLarge, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text(
                str(R.string.feed_you_ve_watched_all_x, count),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onReshuffle,
                modifier = Modifier.height(ButtonDefaults.MediumContainerHeight),
                contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
            ) {
                Icon(Icons.Rounded.Shuffle, null)
                Spacer(Modifier.width(8.dp))
                Text(str(R.string.feed_shuffle_go_again), style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onBack) { Text(str(R.string.feed_back_to_folders), color = Color.White) }
        }
    }
}

@Composable
private fun EmptyFolder(name: String, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(painterResource(R.drawable.ambr_logo), null, Modifier.size(140.dp))
        Spacer(Modifier.height(16.dp))
        Text(str(R.string.feed_x_is_empty, name), style = MaterialTheme.typography.headlineSmall, color = Color.White)
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onBack) { Text(str(R.string.feed_back_to_folders), color = Color.White) }
    }
}
