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
        applyGalleryPolicy(out)
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
            check(src.renameTo(target)) { str(R.string.video_repository_couldn_t_rename_x, video.name) }
            notifyMediaStore(src.absolutePath, target.absolutePath)
            Video.from(target)
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
            check(video.file.delete()) { str(R.string.video_repository_couldn_t_delete_x, video.name) }
            notifyMediaStore(video.path)
        }
    }

    // --------------------------------------------------------------- folders

    suspend fun createFolder(name: String, hidden: Boolean, noMedia: Boolean): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = cleanName(name)
            val parent = if (hidden) vault else root
            parent.mkdirs()
            val dir = File(parent, clean)
            require(!dir.exists()) { str(R.string.video_repository_x_already_exists, clean) }
            check(dir.mkdirs()) { str(R.string.video_repository_couldn_t_create_x, clean) }
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
            check(src.renameTo(target)) { str(R.string.ambr_couldn_t_rename_folder) }
            if (!folder.isHidden) rescanFolder(src, target)
            target
        }
    }

    /** Deletes the folder and every video in it. */
    suspend fun deleteFolder(folder: VideoFolder): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(folder.path)
            val paths = folder.videos.map { it.path }
            check(dir.deleteRecursively()) { str(R.string.video_repository_couldn_t_delete_everything_in, folder.name) }
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

    private fun moveFile(src: File, destDir: File): File {
        val target = uniqueFile(destDir, src.name)
        if (!src.renameTo(target)) {
            // Different file system (e.g. shared ↔ private vault): copy, then delete.
            src.copyTo(target)
            target.setLastModified(src.lastModified())
            check(src.delete()) { str(R.string.video_repository_copied_but_couldn_t_remove) }
        }
        return target
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
