package com.ambr.reloaded.data

import com.ambr.reloaded.util.str

import com.ambr.reloaded.R

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object VideoFormats {
    val EXTENSIONS = setOf("mp4", "mkv", "webm", "avi", "mov", "m4v", "3gp", "flv")
    fun isVideo(file: File) = file.isFile && file.extension.lowercase() in EXTENSIONS
}

/** Progress for long file operations: [done] of [total] files. */
typealias Progress = (done: Int, total: Int) -> Unit

/**
 * Everything AMBR does to files on disk.
 *
 * - Normal videos live in Internal storage/AMBR/ and its sub-folders.
 * - Hidden folders live in AMBR's private storage (filesDir/vault), which no other app or
 *   gallery can see. They are deleted if the app is uninstalled.
 */
class VideoRepository(private val context: Context, private val prefs: Prefs = Prefs(context)) {

    val root: File get() = File(Environment.getExternalStorageDirectory(), "AMBR")
    val fs = Fs(context)
    val vault: File get() = File(context.filesDir, "vault")

    fun isInVault(path: String) = path.startsWith(vault.absolutePath + File.separator)

    suspend fun scan(includeVault: Boolean): List<VideoFolder> = withContext(Dispatchers.IO) {
        val root = root
        if (!root.exists()) root.mkdirs()
        val out = mutableListOf<VideoFolder>()

        fun videosIn(dir: File) = dir.listFiles()?.filter(VideoFormats::isVideo)?.map(Video::from).orEmpty()

        fun walk(dir: File) {
            val videos = videosIn(dir)
            val noMedia = File(dir, NOMEDIA).exists()
            if (dir == root) {
                if (videos.isNotEmpty()) out += VideoFolder(dir.absolutePath, "AMBR (Root)", videos, FolderKind.Root, noMedia)
            } else {
                out += VideoFolder(dir.absolutePath, dir.relativeTo(root).path, videos, FolderKind.Normal, noMedia)
            }
            dir.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }?.forEach(::walk)
        }
        walk(root)
        applyGalleryPolicy(out)   // only AMBR's own folders: never drop .nomedia files into the user's

        // Linked folders: walked like /AMBR, but empty branches are skipped (think Download/).
        // Names read like AMBR's: "Movies", "Movies/Extras". Inside a show library, the show
        // itself is the top level ("Ocean Show", "Ocean Show/Season 1"), not "TV/Ocean Show".
        fun walkLinked(dir: File, linkRoot: File, library: Boolean): Boolean {
            val here = mutableListOf<VideoFolder>()
            val videos = videosIn(dir)
            var any = videos.isNotEmpty()
            val kids = dir.listFiles()?.filter {
                it.isDirectory && !it.name.startsWith(".") && it != root &&
                    !(it.name == "Android" && fs.isVolumeRoot(dir))
            }.orEmpty()
            val mark = out.size
            kids.forEach { if (walkLinked(it, linkRoot, library)) any = true }
            if (any || dir == linkRoot) {
                here += VideoFolder(
                    dir.absolutePath,
                    when {
                        dir == linkRoot -> dir.name.ifEmpty { dir.absolutePath }
                        library -> dir.relativeTo(linkRoot).path
                        else -> dir.relativeTo(linkRoot.parentFile ?: linkRoot).path
                    },
                    videos, FolderKind.Normal, File(dir, NOMEDIA).exists(),
                    linked = true, linkRoot = dir == linkRoot,
                )
                out.addAll(mark, here)
            }
            return any
        }
        val libraries = prefs.showLibraries
        prefs.linkedFolders.map(::File).filter { it.isDirectory }.forEach { walkLinked(it, it, it.absolutePath in libraries) }
        out.sortWith(compareBy<VideoFolder>({ !it.isRoot }, { it.name.lowercase() }))

        if (includeVault) {
            vault.mkdirs()
            vault.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name.lowercase() }?.forEach { dir ->
                out += VideoFolder(dir.absolutePath, dir.name, videosIn(dir), FolderKind.Hidden, noMedia = true)
            }
        }
        out
    }

    /**
     * Keeps each shared folder's .nomedia in line with the user's choice: the app-wide default
     * (hidden) unless they overrode that folder. Android hides a folder's whole subtree when a
     * .nomedia sits higher up, so a folder only gets one if nothing beneath it should be visible.
     */
    private fun applyGalleryPolicy(folders: MutableList<VideoFolder>) {
        val default = prefs.hideFromGalleries
        val forceShow = prefs.galleryVisible
        val forceHide = prefs.galleryHidden
        fun wantsHidden(path: String) = when (path) {
            in forceShow -> false
            in forceHide -> true
            else -> default
        }
        val visible = folders.filter { !wantsHidden(it.path) }.map { it.path + File.separator }
        folders.replaceAll { f ->
            val descendantVisible = visible.any { it.startsWith(f.path + File.separator) && it != f.path + File.separator }
            val shouldHide = wantsHidden(f.path) && !descendantVisible
            val marker = File(f.path, NOMEDIA)
            if (marker.exists() != shouldHide) {
                runCatching { if (shouldHide) marker.createNewFile() else marker.delete() }
                notifyMediaStore(*f.videos.map { it.path }.toTypedArray(), marker.absolutePath)
            }
            f.copy(noMedia = marker.exists())
        }
    }

    /** Every video AMBR knows about, for stats and the duplicate finder. */
    suspend fun allVideos(includeVault: Boolean) = scan(includeVault).flatMap { it.videos }

    // ---------------------------------------------------------------- videos

    suspend fun rename(video: Video, newTitle: String): Result<Video> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = cleanName(newTitle)
            val src = video.file
            val target = File(src.parentFile, if (video.extension.isEmpty()) clean else "$clean.${video.extension}")
            if (target.absolutePath == src.absolutePath) return@runCatching video
            require(!target.exists()) { str(R.string.video_repository_a_video_called_x_already, clean) }
            val done = runCatching { fs.rename(src, target.name) }
                .getOrElse { if (it is NeedsSafAccess) throw it else error(str(R.string.video_repository_couldn_t_rename_x, video.name)) }
            notifyMediaStore(src.absolutePath, done.absolutePath)
            Video.from(done)
        }
    }

    suspend fun move(video: Video, destDir: File): Result<Video> = withContext(Dispatchers.IO) {
        runCatching {
            require(destDir.isDirectory) { str(R.string.video_repository_that_folder_no_longer_exists) }
            val target = moveFile(video.file, destDir)
            notifyMediaStore(video.path, target.absolutePath)
            Video.from(target)
        }
    }

    suspend fun delete(video: Video): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            runCatching { fs.delete(video.file) }
                .onFailure { if (it is NeedsSafAccess) throw it else error(str(R.string.video_repository_couldn_t_delete_x, video.name)) }
            notifyMediaStore(video.path)
        }
    }

    // --------------------------------------------------------------- folders

    suspend fun createFolder(name: String, hidden: Boolean, noMedia: Boolean, inside: File? = null): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = cleanName(name)
            val parent = inside ?: if (hidden) vault else root
            parent.mkdirs()
            require(!File(parent, clean).exists()) { str(R.string.video_repository_x_already_exists, clean) }
            val dir = runCatching { fs.mkdir(parent, clean) }
                .getOrElse { if (it is NeedsSafAccess) throw it else error(str(R.string.video_repository_couldn_t_create_x, clean)) }
            if (noMedia && !hidden) File(dir, NOMEDIA).createNewFile()
            dir
        }
    }

    suspend fun renameFolder(folder: VideoFolder, newName: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val src = File(folder.path)
            val target = File(src.parentFile, cleanName(newName))
            if (target == src) return@runCatching src
            require(!target.exists()) { str(R.string.video_repository_x_already_exists, target.name) }
            val done = runCatching { fs.rename(src, target.name) }
                .getOrElse { if (it is NeedsSafAccess) throw it else error(str(R.string.ambr_couldn_t_rename_folder)) }
            if (!folder.isHidden) rescanFolder(src, done)
            done
        }
    }

    /** Deletes the folder and every video in it. */
    suspend fun deleteFolder(folder: VideoFolder): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(folder.path)
            val paths = folder.videos.map { it.path }
            runCatching { fs.delete(dir) }
                .onFailure { if (it is NeedsSafAccess) throw it else error(str(R.string.video_repository_couldn_t_delete_everything_in, folder.name)) }
            if (!folder.isHidden) notifyMediaStore(*paths.toTypedArray())
        }
    }

    /**
     * Moves a whole folder into the private vault (hide) or back to /AMBR (unhide).
     * Shared storage and private storage are different file systems, so this copies then deletes.
     */
    suspend fun setHidden(folder: VideoFolder, hidden: Boolean, progress: Progress): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val src = File(folder.path)
                val parent = if (hidden) vault else root
                parent.mkdirs()
                val target = uniqueDir(parent, src.name)
                check(target.mkdirs()) { str(R.string.video_repository_couldn_t_create_the_destination) }
                val files = src.walkTopDown().filter { it.isFile && it.name != NOMEDIA }.toList()
                files.forEachIndexed { i, f ->
                    progress(i, files.size)
                    val dest = File(target, f.relativeTo(src).path)
                    dest.parentFile?.mkdirs()
                    f.copyTo(dest, overwrite = false)
                    dest.setLastModified(f.lastModified())
                    f.delete()
                }
                progress(files.size, files.size)
                src.deleteRecursively()
                if (hidden) {
                    notifyMediaStore(*files.map { it.absolutePath }.toTypedArray())
                } else {
                    notifyMediaStore(*target.walkTopDown().filter { it.isFile }.map { it.absolutePath }.toList().toTypedArray())
                }
                target
            }
        }

    // ----------------------------------------------------------------- utils

    private fun moveFile(src: File, destDir: File): File =
        fs.move(src, destDir, uniqueFile(destDir, src.name).name)

    /**
     * Why [dir] can't be added as a linked folder, or null if it can. Prevents overlaps that
     * would list videos twice (AMBR's own folder, other links) and app-private areas.
     */
    fun linkProblem(dir: File): String? {
        val p = dir.absolutePath
        fun under(a: String, b: String) = a == b || a.startsWith(b + File.separator)
        return when {
            !dir.isDirectory || !dir.canRead() -> str(R.string.video_repository_ambr_can_t_read_that)
            under(p, root.absolutePath) -> str(R.string.video_repository_that_s_already_inside_ambr)
            under(root.absolutePath, p) -> str(R.string.video_repository_that_folder_contains_ambr_s)
            Regex("^/storage/[^/]+(/0)?/Android(/|$)").containsMatchIn(p) -> "Android keeps app data there. Pick another folder."
            prefs.linkedFolders.any { under(p, it) } -> str(R.string.video_repository_that_folder_is_already_added)
            prefs.linkedFolders.any { under(it, p) } -> str(R.string.video_repository_a_folder_inside_it_is)
            else -> null
        }
    }

    private fun cleanName(raw: String): String {
        val clean = raw.trim().replace(Regex("""[\\/:*?"<>|]"""), "_").trimStart('.')
        require(clean.isNotEmpty()) { str(R.string.video_repository_name_can_t_be_empty) }
        return clean
    }

    private fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        var n = 2
        while (candidate.exists()) candidate = File(dir, "$base ($n)$ext").also { n++ }
        return candidate
    }

    private fun uniqueDir(parent: File, name: String): File {
        var candidate = File(parent, name)
        var n = 2
        while (candidate.exists()) candidate = File(parent, "$name ($n)").also { n++ }
        return candidate
    }

    private fun rescanFolder(old: File, new: File) {
        val newFiles = new.walkTopDown().filter { it.isFile }.toList()
        val oldPaths = newFiles.map { File(old, it.relativeTo(new).path).absolutePath }
        notifyMediaStore(*(oldPaths + newFiles.map { it.absolutePath }).toTypedArray())
    }

    /** Lets galleries and other apps notice files we added, renamed or removed. */
    fun notifyMediaStore(vararg paths: String) {
        val shared = paths.filterNot(::isInVault)
        if (shared.isNotEmpty()) MediaScannerConnection.scanFile(context, shared.toTypedArray(), null, null)
    }

    companion object {
        const val NOMEDIA = ".nomedia"
    }
}
