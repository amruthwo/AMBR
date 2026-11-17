package com.ambr.reloaded.meld

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ambr.reloaded.data.Prefs
import com.ambr.reloaded.data.Video
import com.ambr.reloaded.data.VideoFolder
import com.ambr.reloaded.data.VideoRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.security.SecureRandom

enum class MeldStage { Idle, Starting, Hosting, Joining, Connecting, Connected }

data class HostInfo(val urls: List<String>, val port: Int, val code: String, val invite: MeldInvite, val hotspot: HotspotInfo?)

data class Nearby(val name: String, val host: String, val port: Int)

enum class TransferState { Queued, Active, Done, Skipped, Failed }

data class Transfer(
    val id: Long,
    val name: String,
    val folder: String,
    val sending: Boolean,
    val total: Long,
    val done: Long = 0,
    val state: TransferState = TransferState.Queued,
    val error: String? = null,
)

private sealed interface Job2 {
    val transfer: Transfer
    data class Pull(override val transfer: Transfer, val folderId: String, val video: RemoteVideo, val dest: File) : Job2
    data class Push(override val transfer: Transfer, val file: File) : Job2
}

/** Everything about Mind Meld: our server, the other phone, and the transfer queue. */
class MeldViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)
    private val repo = VideoRepository(app, prefs)
    private val rnd = SecureRandom()

    private val _stage = MutableStateFlow(MeldStage.Idle)
    val stage: StateFlow<MeldStage> = _stage.asStateFlow()
    private val _host = MutableStateFlow<HostInfo?>(null)
    val host: StateFlow<HostInfo?> = _host.asStateFlow()
    private val _peers = MutableStateFlow<List<MeldPeer>>(emptyList())
    val peers: StateFlow<List<MeldPeer>> = _peers.asStateFlow()
    private val _nearby = MutableStateFlow<List<Nearby>>(emptyList())
    val nearby: StateFlow<List<Nearby>> = _nearby.asStateFlow()

    private val _remote = MutableStateFlow<MeldClient?>(null)
    val remote: StateFlow<MeldClient?> = _remote.asStateFlow()
    private val _remoteName = MutableStateFlow("")
    val remoteName: StateFlow<String> = _remoteName.asStateFlow()
    private val _remoteFolders = MutableStateFlow<List<RemoteFolder>>(emptyList())
    val remoteFolders: StateFlow<List<RemoteFolder>> = _remoteFolders.asStateFlow()
    private val _remoteVideos = MutableStateFlow<Map<String, List<RemoteVideo>>>(emptyMap())
    val remoteVideos: StateFlow<Map<String, List<RemoteVideo>>> = _remoteVideos.asStateFlow()

    private val _mine = MutableStateFlow<List<VideoFolder>>(emptyList())
    val mine: StateFlow<List<VideoFolder>> = _mine.asStateFlow()
    private val _transfers = MutableStateFlow<List<Transfer>>(emptyList())
    val transfers: StateFlow<List<Transfer>> = _transfers.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    val myName: String get() = prefs.meldName
    fun setMyName(n: String) { if (n.isNotBlank()) prefs.meldName = n.trim().take(40) }

    private var server: MeldServer? = null
    private var includeHidden = false
    @Volatile private var catalog: List<VideoFolder> = emptyList()
    private var receivedSomething = false
    val changedLibrary: Boolean get() = receivedSomething

    // ------------------------------------------------------------- our side

    /** Hidden folders are shared only while the vault is unlocked AND the user switched it on. */
    fun setIncludeHidden(on: Boolean) {
        includeHidden = on
        server?.shareHidden = on
        refreshMine()
    }

    fun refreshMine() = viewModelScope.launch {
        val list = repo.scan(includeVault = includeHidden).filter { it.count > 0 || !it.isRoot }
        catalog = list
        _mine.value = list
    }

    private suspend fun ensureServer(): MeldServer {
        server?.let { return it }
        refreshMine().join()
        val token = List(24) { "abcdefghijkmnpqrstuvwxyz23456789"[rnd.nextInt(32)] }.joinToString("")
        val code = (100000 + rnd.nextInt(900000)).toString()
        val port = MeldNet.freePort()
        val s = MeldServer(
            getApplication(), port, token, code, myName,
            catalog = { catalog },
            uploadRoot = repo.root,
            onPeer = ::onPeer,
            onReceived = { f ->
                receivedSomething = true
                repo.notifyMediaStore(f.absolutePath)
                say("Received ${f.name}")
                refreshMine()
            },
        )
        s.shareHidden = includeHidden
        s.startServer()
        server = s
        return s
    }

    private fun myUrls(port: Int) = MeldNet.localAddresses().map { "http://$it:$port" }

    // ----------------------------------------------------------------- hosting

    fun host(useHotspot: Boolean) = viewModelScope.launch {
        _stage.value = MeldStage.Starting
        try {
            val s = ensureServer()
            var hotspot: HotspotInfo? = null
            if (useHotspot) {
                MeldNet.stopHotspot()
                hotspot = MeldNet.startHotspot(getApplication())
                delay(1200)   // let the hotspot interface come up and get its address
            } else {
                MeldNet.stopHotspot()
            }
            val addrs = MeldNet.localAddresses()
            val invite = MeldInvite(addrs, s.listeningPort, s.token, myName, hotspot?.ssid, hotspot?.pass, hotspot?.wpa3 == true)
            _host.value = HostInfo(addrs.map { "http://$it:${s.listeningPort}" }, s.listeningPort, s.code, invite, hotspot)
            if (!useHotspot) MeldNet.advertise(getApplication(), myName, s.listeningPort)
            _stage.value = MeldStage.Hosting
        } catch (e: Exception) {
            say(e.message ?: "Couldn't start Mind Meld")
            _stage.value = MeldStage.Idle
        }
    }

    private fun onPeer(peer: MeldPeer) {
        _peers.update { list -> (list.filterNot { it.name == peer.name && it.isBrowser == peer.isBrowser }) + peer }
        if (peer.isBrowser) { say("${peer.name} connected"); return }
        // Another phone joined us: connect back so we can browse theirs too.
        if (_remote.value == null && peer.token != null) viewModelScope.launch {
            val hosts = peer.urls.mapNotNull { runCatching { java.net.URL(it) }.getOrNull() }
            val base = MeldClient.reach(hosts.map { it.host }, hosts.firstOrNull()?.port ?: return@launch) ?: return@launch
            connected(MeldClient(base, peer.token), peer.name)
        }
    }

    // ----------------------------------------------------------------- joining

    fun startJoining() {
        _stage.value = MeldStage.Joining
        _nearby.value = emptyList()
        MeldNet.discover(getApplication()) { name, host, port ->
            if (name != myName) _nearby.update { l -> (l.filterNot { it.host == host && it.port == port } + Nearby(name, host, port)) }
        }
    }

    /** From a scanned QR code. */
    fun join(raw: String) = viewModelScope.launch {
        val invite = MeldInvite.parse(raw) ?: run { say("That QR code isn't a Mind Meld invite"); return@launch }
        _stage.value = MeldStage.Connecting
        MeldNet.stopDiscovery(getApplication())
        var base = MeldClient.reach(invite.hosts, invite.port)
        if (base == null && invite.ssid != null) {
            say("Joining ${invite.name}'s hotspot…")
            val net = MeldNet.joinHotspot(getApplication(), invite.ssid, invite.pass.orEmpty(), invite.wpa3)
            if (net == null) {
                say("Join Wi-Fi “${invite.ssid}” (password ${invite.pass}) in your phone's settings, then scan again")
                _stage.value = MeldStage.Joining
                return@launch
            }
            delay(800)
            base = MeldClient.reach(invite.hosts, invite.port)
        }
        if (base == null) {
            say("Couldn't reach ${invite.name}. Are you on the same Wi-Fi?")
            _stage.value = MeldStage.Joining
            return@launch
        }
        finishJoin(MeldClient(base, invite.token), invite.name)
    }

    /** From the "nearby on this Wi-Fi" list + the 6-digit code. Returns false for a wrong code. */
    suspend fun joinWithCode(n: Nearby, code: String): Boolean {
        val base = "http://${n.host}:${n.port}"
        val token = MeldClient.login(base, code) ?: return false
        _stage.value = MeldStage.Connecting
        MeldNet.stopDiscovery(getApplication())
        finishJoin(MeldClient(base, token), n.name)
        return true
    }

    private suspend fun finishJoin(client: MeldClient, name: String) {
        val s = ensureServer()
        runCatching { client.introduce(myName, myUrls(s.listeningPort), s.token) }
        connected(client, name)
    }

    private suspend fun connected(client: MeldClient, name: String) {
        _remote.value = client
        _remoteName.value = name
        _stage.value = MeldStage.Connected
        loadRemote()
        say("Mind Meld with $name 🧠⚡🧠")
    }

    fun loadRemote() = viewModelScope.launch {
        val c = _remote.value ?: return@launch
        runCatching { c.folders() }
            .onSuccess { (name, folders) -> _remoteName.value = name; _remoteFolders.value = folders }
            .onFailure { say("Lost connection: ${it.message}") }
    }

    fun loadRemoteVideos(folderId: String) = viewModelScope.launch {
        val c = _remote.value ?: return@launch
        runCatching { c.videos(folderId) }.onSuccess { v -> _remoteVideos.update { it + (folderId to v) } }
    }

    // --------------------------------------------------------------- transfers

    private val queue = Channel<Job2>(Channel.UNLIMITED)
    private var nextId = 1L
    private var runner: Job? = null
    private val jobs = mutableMapOf<Long, Job2>()

    /** Pull whole folders and/or single videos from the other phone. */
    fun take(folders: List<RemoteFolder>, singles: Map<RemoteFolder, List<RemoteVideo>>, hiddenToVault: Boolean) = viewModelScope.launch {
        val c = _remote.value ?: return@launch
        val plan = mutableListOf<Pair<RemoteFolder, RemoteVideo>>()
        folders.forEach { f -> runCatching { c.videos(f.id) }.getOrNull()?.forEach { plan += f to it } }
        singles.forEach { (f, vs) -> if (f !in folders) vs.forEach { plan += f to it } }
        plan.forEach { (f, v) ->
            val folderName = f.name.substringAfterLast('/').let { if (f.name == "AMBR (Root)") "" else it }
            val dest = when {
                f.hidden && hiddenToVault -> File(repo.vault, folderName.ifEmpty { "From ${_remoteName.value}" })
                folderName.isEmpty() -> repo.root
                else -> File(repo.root, folderName)
            }
            enqueue(Job2.Pull(Transfer(nextId++, v.name, f.name, sending = false, total = v.size), f.id, v, dest))
        }
        if (plan.isNotEmpty()) say("Taking ${plan.size} ${if (plan.size == 1) "video" else "videos"}")
    }

    /** Push videos to the other phone, into folders with the same names. */
    fun send(videos: List<Pair<VideoFolder, Video>>) {
        videos.forEach { (f, v) ->
            val folderName = if (f.isRoot) "AMBR" else f.name.substringAfterLast('/')
            enqueue(Job2.Push(Transfer(nextId++, v.name, folderName, sending = true, total = v.sizeBytes), v.file))
        }
        if (videos.isNotEmpty()) say("Sending ${videos.size} ${if (videos.size == 1) "video" else "videos"}")
    }

    private fun enqueue(job: Job2) {
        jobs[job.transfer.id] = job
        _transfers.update { it + job.transfer }
        queue.trySend(job)
        if (runner?.isActive != true) runner = viewModelScope.launch { runQueue() }
    }

    private fun update(id: Long, f: (Transfer) -> Transfer) = _transfers.update { l -> l.map { if (it.id == id) f(it) else it } }

    private suspend fun runQueue() {
        for (job in queue) {
            val c = _remote.value ?: break
            val id = job.transfer.id
            update(id) { it.copy(state = TransferState.Active) }
            var last = 0L
            val progress: (Long) -> Unit = { b ->
                val now = System.currentTimeMillis()
                if (now - last > 200) { last = now; update(id) { it.copy(done = b) } }
            }
            val result = runCatching {
                when (job) {
                    is Job2.Pull -> c.download(job.folderId, job.video, job.dest, progress)
                    is Job2.Push -> c.upload(job.file, job.transfer.folder, progress)
                }
            }
            result.onSuccess { did ->
                if (job is Job2.Pull && did) { receivedSomething = true; repo.notifyMediaStore(File(job.dest, job.video.name).absolutePath) }
                update(id) { it.copy(state = if (did) TransferState.Done else TransferState.Skipped, done = it.total) }
            }.onFailure { e -> update(id) { it.copy(state = TransferState.Failed, error = e.message) } }
            if (job is Job2.Pull) refreshMine()
        }
    }

    /** Re-queues failed transfers; partial files resume where they stopped. */
    fun retryFailed() {
        val failed = _transfers.value.filter { it.state == TransferState.Failed }
        failed.forEach { t ->
            update(t.id) { it.copy(state = TransferState.Queued, error = null) }
            jobs[t.id]?.let { queue.trySend(it) }
        }
        if (failed.isNotEmpty() && runner?.isActive != true) runner = viewModelScope.launch { runQueue() }
    }

    fun clearFinished() = _transfers.update { l ->
        l.filter { it.state == TransferState.Active || it.state == TransferState.Queued || it.state == TransferState.Failed }
            .also { keep -> jobs.keys.retainAll(keep.map { it.id }.toSet()) }
    }

    // ------------------------------------------------------------------- leave

    fun leave() {
        server?.stop(); server = null
        MeldNet.stopHotspot()
        MeldNet.stopAdvertising(getApplication())
        MeldNet.stopDiscovery(getApplication())
        MeldNet.leaveHotspot(getApplication())
        runner?.cancel()
        _remote.value = null
        _host.value = null
        _peers.value = emptyList()
        _remoteFolders.value = emptyList()
        _remoteVideos.value = emptyMap()
        _stage.value = MeldStage.Idle
    }

    override fun onCleared() {
        leave()
        super.onCleared()
    }

    private fun say(m: String) { _messages.tryEmit(m) }
}
