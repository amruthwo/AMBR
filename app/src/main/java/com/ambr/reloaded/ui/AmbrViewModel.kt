package com.ambr.reloaded.ui

import com.ambr.reloaded.util.plural

import com.ambr.reloaded.util.str

import com.ambr.reloaded.R

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ambr.reloaded.data.Ambience
import com.ambr.reloaded.data.Cram
import com.ambr.reloaded.data.FolderMode
import com.ambr.reloaded.data.ProgressStore
import com.ambr.reloaded.data.Show
import com.ambr.reloaded.data.VideoScale
import com.ambr.reloaded.data.DuplicateFinder
import com.ambr.reloaded.data.DurationCache
import com.ambr.reloaded.data.FolderKind
import com.ambr.reloaded.data.PinLock
import com.ambr.reloaded.data.Prefs
import com.ambr.reloaded.data.Video
import com.ambr.reloaded.data.VideoFolder
import com.ambr.reloaded.data.VideoRepository
import com.ambr.reloaded.data.ViewMode
import com.ambr.reloaded.share.YtDlp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.ambr.reloaded.util.pluralVideos
import kotlin.random.Random

/** An open folder: its shuffled playlist and where to start. [generation] bumps on reshuffle. */
data class FeedState(
    val folder: VideoFolder,
    val playlist: List<Video>,
    val startIndex: Int,
    val generation: Int = 0,
)

/** A long-running file job shown as a blocking progress dialog. */
data class Busy(val label: String, val progress: Float? = null)

data class DuplicateState(
    val running: Boolean = false,
    val progress: Float = 0f,
    val groups: List<List<Video>>? = null,
)

data class StatsState(
    val durations: Map<String, Long> = emptyMap(),
    val progress: Float = 0f,
    val done: Boolean = false,
)

class AmbrViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)
    private val repo = VideoRepository(app, prefs)
    private val pin = PinLock(prefs)
    private val durationCache = DurationCache(app)

    // ------------------------------------------------------------------ state

    private val _folders = MutableStateFlow<List<VideoFolder>>(emptyList())
    private val _favorites = MutableStateFlow(prefs.favorites)
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private val _bigBrain = MutableStateFlow(prefs.bigBrainFolders)
    val bigBrain: StateFlow<Set<String>> = _bigBrain.asStateFlow()

    /** The Big Brain folder a path belongs to (itself or an ancestor), if any. */
    fun bigBrainRootOf(path: String): String? =
        _bigBrain.value.firstOrNull { path == it || path.startsWith(it + File.separator) }

    /** Every scanned folder, marked with its mode (seasons inside Big Brain folders included). */
    val allFolders: StateFlow<List<VideoFolder>> = combine(_folders, _bigBrain) { list, bb ->
        list.map { f -> if (f.path in bb) f.copy(mode = FolderMode.BigBrain) else f }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Home-screen folders: Favorites first, then real folders, with Big Brain seasons tucked inside their show. */
    val folders: StateFlow<List<VideoFolder>> = combine(allFolders, _favorites) { all, favs ->
        val list = all.filter { f -> _bigBrain.value.none { root -> f.path.startsWith(root + File.separator) } }
            .map { f ->
                // A show's tile counts every episode in its seasons (cover, size, "N videos", Continue).
                if (f.mode != FolderMode.BigBrain) f
                else f.copy(videos = all.filter { it.path == f.path || it.path.startsWith(f.path + File.separator) }.flatMap { it.videos })
            }
        val favVideos = all.flatMap { it.videos }.filter { it.path in favs }
        val favFolder = if (favVideos.isNotEmpty()) {
            listOf(VideoFolder(VideoFolder.FAVORITES_PATH, str(R.string.ambr_favorites), favVideos, FolderKind.Favorites))
        } else emptyList()
        favFolder + list
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Every video in every visible folder, for Shuffle All. */
    val allFolder: StateFlow<VideoFolder?> = combine(_folders, _bigBrain) { list, bb ->
        // Brain Rot only: long-form Big Brain videos stay out of the shuffle.
        val all = list.filter { f -> bb.none { f.path == it || f.path.startsWith(it + File.separator) } }.flatMap { it.videos }
        if (all.isEmpty()) null else VideoFolder(VideoFolder.ALL_PATH, str(R.string.ambr_shuffle_all), all, FolderKind.All)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // ------------------------------------------------------------- Big Brain

    private val progressStore = ProgressStore(app)
    private val _progress = MutableStateFlow(progressStore.all())
    val progress: StateFlow<Map<String, ProgressStore.Entry>> = _progress.asStateFlow()

    /** What the Big Brain player is playing. [key] changes on every open so the player restarts. */
    data class LongState(val title: String, val playlist: List<Video>, val startIndex: Int, val key: Long = System.nanoTime())
    private val _long = MutableStateFlow<LongState?>(null)
    val long: StateFlow<LongState?> = _long.asStateFlow()

    private val _scaleBrainRot = MutableStateFlow(prefs.scaleBrainRot)
    val scaleBrainRot: StateFlow<VideoScale> = _scaleBrainRot.asStateFlow()
    private val _scaleBigBrain = MutableStateFlow(prefs.scaleBigBrain)
    val scaleBigBrain: StateFlow<VideoScale> = _scaleBigBrain.asStateFlow()
    fun setScaleBrainRot(s: VideoScale) { _scaleBrainRot.value = s; prefs.scaleBrainRot = s }
    fun setScaleBigBrain(s: VideoScale) { _scaleBigBrain.value = s; prefs.scaleBigBrain = s }

    fun setBigBrain(folder: VideoFolder, on: Boolean) {
        _bigBrain.value = if (on) _bigBrain.value + folder.path else _bigBrain.value - folder.path
        prefs.bigBrainFolders = _bigBrain.value
        say(str(if (on) R.string.bigbrain_on else R.string.bigbrain_off, folder.name))
    }

    fun show(path: String): Show? = allFolders.value.firstOrNull { it.path == path }?.let { Show.of(it, allFolders.value) }

    fun playLong(title: String, playlist: List<Video>, start: Int) {
        if (playlist.isEmpty()) return
        _long.value = LongState(title, playlist, start.coerceIn(0, playlist.lastIndex))
    }

    fun closeLong() {
        _long.value = null
        _progress.value = progressStore.all()
    }

    fun saveProgress(path: String, positionMs: Long, durationMs: Long) {
        if (durationMs <= 0) return
        progressStore.set(path, positionMs, durationMs)
    }

    fun setWatched(v: Video, watched: Boolean) {
        val dur = _progress.value[v.path]?.durationMs ?: 1L
        if (watched) progressStore.markWatched(v.path, dur) else progressStore.clear(v.path)
        _progress.value = progressStore.all()
    }

    fun progressOf(path: String) = progressStore.get(path)

    /** Video lengths for a show's episode list (cached after the first look). */
    suspend fun durationsOf(videos: List<Video>): Map<String, Long> = durationCache.durations(videos) { _, _ -> }

    fun markLastFolder(path: String) { prefs.lastFolder = path }

    /** Opens a video from anywhere (search): Big Brain → player in show order; Brain Rot → its folder's feed at that video. */
    fun openVideo(v: Video) {
        val root = bigBrainRootOf(v.folderPath)
        if (root != null) {
            val s = show(root) ?: return
            val list = s.playlist
            playLong(s.folder.name, list, list.indexOfFirst { it.path == v.path }.coerceAtLeast(0))
            return
        }
        val folder = allFolders.value.firstOrNull { it.path == v.folderPath } ?: return
        prefs.lastFolder = folder.path
        val feed = buildFeed(folder, generation = 0)
        _feed.value = feed.copy(startIndex = feed.playlist.indexOfFirst { it.path == v.path }.coerceAtLeast(0))
    }

    private val _hideFromGalleries = MutableStateFlow(prefs.hideFromGalleries)
    val hideFromGalleries: StateFlow<Boolean> = _hideFromGalleries.asStateFlow()

    private val _cramHeight = MutableStateFlow(prefs.cramHeight)
    val cramHeight: StateFlow<Int> = _cramHeight.asStateFlow()
    private val _cramCharging = MutableStateFlow(prefs.cramChargingOnly)
    val cramCharging: StateFlow<Boolean> = _cramCharging.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _feed = MutableStateFlow<FeedState?>(null)
    val feed: StateFlow<FeedState?> = _feed.asStateFlow()

    private val _muted = MutableStateFlow(prefs.muted)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    private val _viewMode = MutableStateFlow(prefs.viewMode)
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()
    val pinIsSet: Boolean get() = pin.isSet

    private val _biometric = MutableStateFlow(prefs.biometric)
    val biometric: StateFlow<Boolean> = _biometric.asStateFlow()
    fun setBiometric(on: Boolean) { _biometric.value = on; prefs.biometric = on }

    private val _busy = MutableStateFlow<Busy?>(null)
    val busy: StateFlow<Busy?> = _busy.asStateFlow()

    private val _ambience = MutableStateFlow<List<Int>>(emptyList())
    val ambience: StateFlow<List<Int>> = _ambience.asStateFlow()

    private val _stats = MutableStateFlow(StatsState())
    val stats: StateFlow<StatsState> = _stats.asStateFlow()

    private val _dups = MutableStateFlow(DuplicateState())
    val duplicates: StateFlow<DuplicateState> = _dups.asStateFlow()

    private val _holdSpeed = MutableStateFlow(prefs.holdSpeed)
    val holdSpeed: StateFlow<Float> = _holdSpeed.asStateFlow()
    private val _seekStep = MutableStateFlow(prefs.seekStepSeconds)
    val seekStep: StateFlow<Int> = _seekStep.asStateFlow()
    private val _downloadHeight = MutableStateFlow(prefs.downloadHeight)
    val downloadHeight: StateFlow<Int> = _downloadHeight.asStateFlow()
    private val _autoLock = MutableStateFlow(prefs.autoLockSeconds)
    val autoLock: StateFlow<Int> = _autoLock.asStateFlow()

    private val _ytdlpVersion = MutableStateFlow<String?>(null)
    val ytdlpVersion: StateFlow<String?> = _ytdlpVersion.asStateFlow()
    private val _ytdlpUpdating = MutableStateFlow(false)
    val ytdlpUpdating: StateFlow<Boolean> = _ytdlpUpdating.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    val lastFolderPath: String? get() = prefs.lastFolder

    // ---------------------------------------------------------------- library

    fun refresh() {
        if (_scanning.value) return
        viewModelScope.launch {
            _scanning.value = true
            try {
                rescan()
            } finally {
                _scanning.value = false
                _loaded.value = true
            }
        }
    }

    private suspend fun rescan() {
        _favorites.value = prefs.favorites   // Cram Session may have renamed favourites
        val list = repo.scan(includeVault = _unlocked.value)
        _folders.value = list
        pruneFavorites()
        refreshAmbience(list)
    }

    private var ambienceJob: Job? = null
    private fun refreshAmbience(list: List<VideoFolder>) {
        val covers = list.filter { !it.isHidden }.mapNotNull { it.cover }.sortedByDescending { it.lastModified }
        ambienceJob?.cancel()
        ambienceJob = viewModelScope.launch {
            runCatching { Ambience.colorsFrom(getApplication(), covers) }.onSuccess { _ambience.value = it }
        }
    }

    private suspend fun pruneFavorites() = withContext(Dispatchers.IO) {
        val alive = _favorites.value.filterTo(mutableSetOf()) { File(it).exists() }
        if (alive.size != _favorites.value.size) setFavorites(alive)
    }

    fun setViewMode(mode: ViewMode) {
        _viewMode.value = mode
        prefs.viewMode = mode
    }

    // ------------------------------------------------------------------- feed

    fun openFolder(folder: VideoFolder) {
        prefs.lastFolder = folder.path
        _feed.value = buildFeed(folder, generation = 0)
    }

    fun closeFeed() {
        _feed.value = null
        refresh()
    }

    fun onPageSettled(index: Int) {
        val feed = _feed.value ?: return
        if (index in feed.playlist.indices) prefs.setIndex(feed.folder.path, index)
    }

    fun reshuffle() {
        val feed = _feed.value ?: return
        prefs.newSeed(feed.folder.path)
        val fresh = (folders.value + listOfNotNull(allFolder.value)).firstOrNull { it.path == feed.folder.path } ?: feed.folder
        _feed.value = buildFeed(fresh, feed.generation + 1)
    }

    fun toggleMute() {
        _muted.update { !it }
        prefs.muted = _muted.value
    }

    private fun buildFeed(folder: VideoFolder, generation: Int): FeedState {
        val seed = prefs.seed(folder.path)
        val playlist = folder.videos.sortedBy { it.path }.shuffled(Random(seed))
        val start = prefs.index(folder.path).coerceIn(0, (playlist.size - 1).coerceAtLeast(0))
        return FeedState(folder, playlist, start, generation)
    }

    /** Swap (or remove, when [transform] returns null) a video in the open playlist without reshuffling. */
    private fun replaceInFeed(old: Video, transform: (Video) -> Video?) {
        _feed.update { feed ->
            feed?.copy(playlist = feed.playlist.mapNotNull { if (it.path == old.path) transform(it) else it })
        }
    }

    // -------------------------------------------------------------- favorites

    private fun setFavorites(set: Set<String>) {
        _favorites.value = set
        prefs.favorites = set
    }

    fun isFavorite(v: Video) = v.path in _favorites.value

    /** Double-tap: only ever adds, like Instagram. */
    fun like(v: Video) {
        if (v.path !in _favorites.value) setFavorites(_favorites.value + v.path)
    }

    fun toggleFavorite(v: Video) {
        if (v.path in _favorites.value) {
            setFavorites(_favorites.value - v.path)
            say(str(R.string.ambr_removed_from_favorites))
        } else {
            setFavorites(_favorites.value + v.path)
            say(str(R.string.ambr_added_to_favorites))
        }
    }

    private fun movePathInFavorites(old: String, new: String?) {
        if (old !in _favorites.value) return
        setFavorites(_favorites.value - old + listOfNotNull(new))
    }

    // ----------------------------------------------------------- video edits

    fun rename(video: Video, newTitle: String) = viewModelScope.launch {
        repo.rename(video, newTitle)
            .onSuccess { renamed ->
                movePathInFavorites(video.path, renamed.path)
                progressStore.move(video.path, renamed.path)
                replaceInFeed(video) { renamed }
                say(str(R.string.ambr_renamed_to_x, renamed.title))
            }
            .onFailure { say(it.message ?: str(R.string.ambr_failed_to_rename)) }
        rescan()
    }

    fun move(video: Video, dest: VideoFolder) = viewModelScope.launch {
        _busy.value = Busy(str(R.string.ambr_moving_to_x, dest.name))
        repo.move(video, File(dest.path))
            .onSuccess { moved ->
                movePathInFavorites(video.path, moved.path)
                progressStore.move(video.path, moved.path)
                val virtualFeed = _feed.value?.folder?.isVirtual == true
                replaceInFeed(video) { if (virtualFeed) moved else null }
                say(str(R.string.ambr_moved_to_x, dest.name))
            }
            .onFailure { say(it.message ?: str(R.string.ambr_failed_to_move)) }
        _busy.value = null
        rescan()
    }

    fun delete(video: Video) = viewModelScope.launch {
        repo.delete(video)
            .onSuccess {
                movePathInFavorites(video.path, null)
                replaceInFeed(video) { null }
                say(str(R.string.ambr_deleted))
            }
            .onFailure { say(it.message ?: str(R.string.ambr_failed_to_delete)) }
        rescan()
    }

    // ---------------------------------------------------------------- folders

    fun createFolder(name: String, hidden: Boolean, noMedia: Boolean) = viewModelScope.launch {
        repo.createFolder(name, hidden, noMedia)
            .onSuccess { say(if (hidden) str(R.string.ambr_hidden_folder_x_created, it.name) else str(R.string.ambr_folder_x_created, it.name)) }
            .onFailure { say(it.message ?: str(R.string.ambr_couldn_t_create_folder)) }
        rescan()
    }

    fun renameFolder(folder: VideoFolder, name: String) = viewModelScope.launch {
        repo.renameFolder(folder, name)
            .onSuccess { dir ->
                folder.videos.forEach { v -> movePathInFavorites(v.path, File(dir, v.name).absolutePath) }
                if (prefs.lastFolder == folder.path) prefs.lastFolder = dir.absolutePath
                movePathInGalleryPrefs(folder.path, dir.absolutePath)
                if (folder.path in _bigBrain.value) {
                    _bigBrain.value = _bigBrain.value - folder.path + dir.absolutePath
                    prefs.bigBrainFolders = _bigBrain.value
                }
                say(str(R.string.ambr_renamed_to_x, dir.name))
            }
            .onFailure { say(it.message ?: str(R.string.ambr_couldn_t_rename_folder)) }
        rescan()
    }

    fun deleteFolder(folder: VideoFolder) = viewModelScope.launch {
        repo.deleteFolder(folder)
            .onSuccess {
                setFavorites(_favorites.value - folder.videos.map { it.path }.toSet())
                say(str(R.string.ambr_deleted_x, folder.name))
            }
            .onFailure { say(it.message ?: str(R.string.ambr_couldn_t_delete_folder)) }
        rescan()
    }

    /** Per-folder override of the app-wide "hide from galleries" default. */
    fun setNoMedia(folder: VideoFolder, enabled: Boolean) = viewModelScope.launch {
        prefs.galleryVisible = prefs.galleryVisible - folder.path + (if (enabled) emptySet() else setOf(folder.path))
        prefs.galleryHidden = prefs.galleryHidden - folder.path + (if (enabled) setOf(folder.path) else emptySet())
        rescan()
        val now = _folders.value.firstOrNull { it.path == folder.path }
        say(
            when {
                enabled -> str(R.string.ambr_hidden_from_other_apps_some)
                now?.noMedia == true -> str(R.string.ambr_still_hidden_a_folder_above)
                else -> str(R.string.ambr_visible_to_other_apps_again)
            },
        )
    }

    fun setHideFromGalleries(on: Boolean) = viewModelScope.launch {
        _hideFromGalleries.value = on
        prefs.hideFromGalleries = on
        rescan()
        say(if (on) str(R.string.ambr_folders_hidden_from_galleries_except) else str(R.string.ambr_folders_visible_in_galleries_except))
    }

    private fun movePathInGalleryPrefs(old: String, new: String) {
        if (old in prefs.galleryVisible) prefs.galleryVisible = prefs.galleryVisible - old + new
        if (old in prefs.galleryHidden) prefs.galleryHidden = prefs.galleryHidden - old + new
    }

    fun openShuffleAll() {
        allFolder.value?.let(::openFolder)
    }

    // ------------------------------------------------------------ Cram Session

    fun setCramHeight(h: Int) { _cramHeight.value = h; prefs.cramHeight = h }
    fun setCramCharging(on: Boolean) { _cramCharging.value = on; prefs.cramChargingOnly = on }

    suspend fun cramCandidates(videos: List<Video>, progress: (Int, Int) -> Unit) =
        Cram.candidates(videos, _cramHeight.value, progress)

    fun startCram(paths: List<String>) {
        if (paths.isEmpty()) return
        Cram.enqueue(getApplication(), paths, _cramHeight.value, _cramCharging.value)
        say(str(R.string.ambr_cramming_x_to_x_p, pluralVideos(paths.size), _cramHeight.value))
    }

    /** Cram everything worth cramming in one folder (or a single video). */
    fun cramQuick(videos: List<Video>) = viewModelScope.launch {
        val picks = Cram.candidates(videos, _cramHeight.value) { _, _ -> }
        if (picks.isEmpty()) say(str(R.string.ambr_already_small_nothing_to_cram, _cramHeight.value))
        else startCram(picks.map { it.video.path })
    }

    fun setHidden(folder: VideoFolder, hidden: Boolean) = viewModelScope.launch {
        val verb = if (hidden) str(R.string.ambr_hiding) else str(R.string.ambr_unhiding)
        _busy.value = Busy("$verb “${folder.name}”…", 0f)
        repo.setHidden(folder, hidden) { done, total ->
            _busy.value = Busy("$verb “${folder.name}”… $done/$total", if (total == 0) 1f else done / total.toFloat())
        }
            .onSuccess { dir ->
                folder.videos.forEach { v -> movePathInFavorites(v.path, File(dir, v.name).absolutePath) }
                if (prefs.lastFolder == folder.path) prefs.lastFolder = dir.absolutePath
                say(if (hidden) str(R.string.ambr_x_is_now_hidden, folder.name) else str(R.string.ambr_x_is_visible_again, folder.name))
            }
            .onFailure { say(it.message ?: str(R.string.ambr_couldn_t_move_the_folder)) }
        _busy.value = null
        rescan()
    }

    // ------------------------------------------------------------------ vault

    suspend fun unlock(code: String): Boolean {
        val ok = withContext(Dispatchers.Default) { pin.check(code) }
        if (ok) {
            _unlocked.value = true
            refresh()
        }
        return ok
    }

    /** Fingerprint/face already confirmed it's you. */
    fun unlockTrusted() {
        _unlocked.value = true
        refresh()
    }

    suspend fun createPin(code: String) {
        withContext(Dispatchers.Default) { pin.set(code) }
        _unlocked.value = true
        refresh()
    }

    suspend fun checkPin(code: String) = withContext(Dispatchers.Default) { pin.check(code) }

    fun lock() {
        if (!_unlocked.value) return
        _unlocked.value = false
        // Never leave hidden videos on screen.
        val f = _feed.value
        if (f != null && (f.folder.isHidden || f.folder.isVirtual)) _feed.value = null
        _stats.value = StatsState()
        _dups.value = DuplicateState()
        refresh()
    }

    /** Forgot PIN: wipes hidden folders, then removes the PIN. */
    fun eraseVault() = viewModelScope.launch {
        withContext(Dispatchers.IO) { repo.vault.deleteRecursively() }
        pin.clear()
        setBiometric(false)
        _unlocked.value = false
        say(str(R.string.ambr_hidden_folders_erased_and_pin))
        rescan()
    }

    private var backgroundedAt = 0L
    fun onAppBackground() { backgroundedAt = SystemClock.elapsedRealtime() }
    fun onAppForeground() {
        if (backgroundedAt == 0L) return
        val away = (SystemClock.elapsedRealtime() - backgroundedAt) / 1000
        backgroundedAt = 0L
        if (away >= _autoLock.value) lock()
    }

    // ------------------------------------------------------------ info/dups

    private var statsJob: Job? = null
    fun computeStats() {
        statsJob?.cancel()
        statsJob = viewModelScope.launch {
            val videos = repo.allVideos(_unlocked.value)
            _stats.value = _stats.value.copy(done = false)
            val map = durationCache.durations(videos) { done, total ->
                _stats.update { it.copy(progress = if (total == 0) 1f else done / total.toFloat()) }
            }
            _stats.value = StatsState(map, 1f, true)
        }
    }

    private var dupJob: Job? = null
    fun findDuplicates() {
        dupJob?.cancel()
        dupJob = viewModelScope.launch {
            _dups.value = DuplicateState(running = true)
            val videos = repo.allVideos(_unlocked.value)
            val groups = DuplicateFinder.find(videos) { done, total ->
                _dups.update { it.copy(progress = if (total == 0) 1f else done / total.toFloat()) }
            }
            _dups.value = DuplicateState(running = false, progress = 1f, groups = groups)
        }
    }

    fun deleteVideos(videos: List<Video>) = viewModelScope.launch {
        _busy.value = Busy(str(R.string.ambr_deleting_x_copies, videos.size))
        var ok = 0
        videos.forEach { v -> repo.delete(v).onSuccess { ok++; movePathInFavorites(v.path, null) } }
        _busy.value = null
        say(str(R.string.ambr_deleted_x_x, plural(R.plurals.copies, ok)))
        _dups.update { s ->
            val gone = videos.map { it.path }.toSet()
            s.copy(groups = s.groups?.map { g -> g.filter { it.path !in gone } }?.filter { it.size > 1 })
        }
        rescan()
    }

    // --------------------------------------------------------------- settings

    fun setHoldSpeed(v: Float) { _holdSpeed.value = v; prefs.holdSpeed = v }
    fun setSeekStep(v: Int) { _seekStep.value = v; prefs.seekStepSeconds = v }
    fun setDownloadHeight(v: Int) { _downloadHeight.value = v; prefs.downloadHeight = v }
    fun setAutoLock(v: Int) { _autoLock.value = v; prefs.autoLockSeconds = v }

    fun loadYtDlpVersion() = viewModelScope.launch {
        _ytdlpVersion.value = YtDlp.version(getApplication()) ?: str(R.string.ambr_bundled_version_tap_update_for)
    }

    fun updateYtDlp() = viewModelScope.launch {
        _ytdlpUpdating.value = true
        runCatching { YtDlp.update(getApplication()) }
            .onSuccess { updated -> say(if (updated) str(R.string.ambr_yt_dlp_updated) else str(R.string.ambr_yt_dlp_is_already_up)) }
            .onFailure { say(str(R.string.ambr_update_failed_x, it.message?.take(80))) }
        _ytdlpVersion.value = YtDlp.version(getApplication()) ?: str(R.string.ambr_bundled_version)
        _ytdlpUpdating.value = false
    }

    fun say(msg: String) {
        _messages.tryEmit(msg)
    }
}
