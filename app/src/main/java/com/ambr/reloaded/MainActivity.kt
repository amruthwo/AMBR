package com.ambr.reloaded

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.fragment.app.FragmentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.ambr.reloaded.data.StorageAccess
import com.ambr.reloaded.share.ShareActivity
import com.ambr.reloaded.ui.AmbrViewModel
import com.ambr.reloaded.ui.FeedState
import com.ambr.reloaded.meld.MeldViewModel
import com.ambr.reloaded.ui.screens.BusyDialog
import com.ambr.reloaded.ui.screens.CramScreen
import com.ambr.reloaded.ui.screens.MindMeldScreen
import com.ambr.reloaded.ui.screens.DuplicatesScreen
import com.ambr.reloaded.ui.screens.FeedScreen
import com.ambr.reloaded.ui.screens.HomeScreen
import com.ambr.reloaded.ui.screens.InfoScreen
import com.ambr.reloaded.ui.screens.PermissionScreen
import com.ambr.reloaded.ui.screens.SettingsScreen
import com.ambr.reloaded.ui.screens.SplashScreen
import com.ambr.reloaded.ui.theme.AmbrTheme

private enum class Route { Home, Info, Duplicates, Settings, Cram, Meld }

private sealed interface Screen {
    data object Splash : Screen
    data object Permission : Screen
    data class Page(val route: Route) : Screen
    data class Feed(val state: FeedState) : Screen
}

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent { AmbrTheme { AmbrRoot(this) } }
    }
}

@Composable
private fun AmbrRoot(activity: ComponentActivity) {
    val vm: AmbrViewModel = viewModel()
    val meld: MeldViewModel = viewModel()
    val context = LocalContext.current

    var splashDone by rememberSaveable { mutableStateOf(false) }
    var route by rememberSaveable { mutableStateOf(Route.Home) }
    var hasAccess by remember { mutableStateOf(StorageAccess.has(context)) }
    LifecycleResumeEffect(Unit) {
        hasAccess = StorageAccess.has(context)
        if (hasAccess) vm.refresh()
        onPauseOrDispose { }
    }

    // Relock hidden folders after time away from the app.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_STOP -> vm.onAppBackground()
                Lifecycle.Event.ON_START -> vm.onAppForeground()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    // While hidden folders are open: no screenshots, and a blank card in Recents.
    val unlocked by vm.unlocked.collectAsStateWithLifecycle()
    LaunchedEffect(unlocked) {
        if (unlocked) activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    // Foldables: notice tabletop posture (half-open, horizontal hinge).
    val layoutInfo by remember(activity) {
        WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity)
    }.collectAsStateWithLifecycle(initialValue = null)
    val fold = layoutInfo?.displayFeatures?.filterIsInstance<FoldingFeature>()?.firstOrNull {
        it.state == FoldingFeature.State.HALF_OPENED && it.orientation == FoldingFeature.Orientation.HORIZONTAL
    }

    val folders by vm.folders.collectAsStateWithLifecycle()
    val feed by vm.feed.collectAsStateWithLifecycle()
    val muted by vm.muted.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val holdSpeed by vm.holdSpeed.collectAsStateWithLifecycle()
    val seekStep by vm.seekStep.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(it) }
    }

    val screen: Screen = when {
        !splashDone -> Screen.Splash
        !hasAccess -> Screen.Permission
        feed != null -> Screen.Feed(feed!!)
        else -> Screen.Page(route)
    }

    AnimatedContent(
        targetState = screen,
        contentKey = { if (it is Screen.Page) it.route else it::class },
        transitionSpec = {
            val from = initialState
            val to = targetState
            when {
                to is Screen.Feed -> (scaleIn(spring(0.8f, 380f), initialScale = 0.92f) + fadeIn()) togetherWith fadeOut()
                from is Screen.Page && to is Screen.Page && to.route != Route.Home ->
                    slideInHorizontally(spring(0.85f, 400f)) { it / 3 } + fadeIn() togetherWith fadeOut()
                from is Screen.Page && to is Screen.Page ->
                    fadeIn() togetherWith slideOutHorizontally(spring(0.85f, 400f)) { it / 3 } + fadeOut()
                else -> fadeIn(spring(stiffness = 380f)) togetherWith (scaleOut(targetScale = 0.96f) + fadeOut())
            }
        },
        label = "screens",
    ) { s ->
        when (s) {
            Screen.Splash -> SplashScreen(onFinished = { splashDone = true })
            Screen.Permission -> PermissionScreen(onGrant = { StorageAccess.request(activity) })
            is Screen.Page -> when (s.route) {
                Route.Home -> HomeScreen(
                    vm = vm,
                    snackbar = snackbar,
                    onOpenInfo = { route = Route.Info },
                    onOpenDuplicates = { route = Route.Duplicates },
                    onOpenSettings = { route = Route.Settings },
                    onOpenMeld = { route = Route.Meld },
                    onOpenCram = { route = Route.Cram },
                    onDownloadLink = {
                        context.startActivity(
                            Intent(context, ShareActivity::class.java).setAction(ShareActivity.ACTION_PASTE_LINK),
                        )
                    },
                )
                Route.Info -> InfoScreen(vm, onBack = { route = Route.Home })
                Route.Duplicates -> DuplicatesScreen(vm, onBack = { route = Route.Home })
                Route.Settings -> SettingsScreen(vm, snackbar, onBack = { route = Route.Home })
                Route.Cram -> CramScreen(vm, snackbar, onBack = { route = Route.Home })
                Route.Meld -> MindMeldScreen(vm, meld, snackbar, onBack = { route = Route.Home })
            }
            // Read the live feed (renames/deletes update it); fall back to the snapshot while animating out.
            is Screen.Feed -> FeedScreen(
                feed = feed ?: s.state,
                folders = folders,
                muted = muted,
                favorites = favorites,
                holdSpeed = holdSpeed,
                seekStep = seekStep,
                tabletop = fold,
                snackbar = snackbar,
                onBack = vm::closeFeed,
                onPageSettled = vm::onPageSettled,
                onReshuffle = vm::reshuffle,
                onToggleMute = vm::toggleMute,
                onLike = vm::like,
                onToggleFavorite = vm::toggleFavorite,
                onRename = { v, name -> vm.rename(v, name) },
                onMove = { v, f -> vm.move(v, f) },
                onDelete = { vm.delete(it) },
                onCram = { vm.cramQuick(listOf(it)) },
            )
        }
    }

    busy?.let { BusyDialog(it) }
}
