package com.ambr.reloaded.data

import android.net.Uri
import java.io.File

data class Video(
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long,
) {
    val file: File get() = File(path)
    val uri: Uri get() = Uri.fromFile(file)
    val title: String get() = name.substringBeforeLast('.')
    val extension: String get() = name.substringAfterLast('.', "")
    val folderPath: String get() = file.parent.orEmpty()

    companion object {
        fun from(file: File) = Video(file.absolutePath, file.name, file.length(), file.lastModified())
    }
}

enum class FolderKind { Root, Normal, Hidden, Favorites, All }

data class VideoFolder(
    val path: String,
    val name: String,
    val videos: List<Video>,
    val kind: FolderKind,
    /** A .nomedia file keeps the folder out of galleries. Hidden folders are always invisible. */
    val noMedia: Boolean = false,
) {
    val count: Int get() = videos.size
    val totalBytes: Long = videos.sumOf { it.sizeBytes }
    /** Most recently added video, used as the folder's cover art. */
    val cover: Video? = videos.maxByOrNull { it.lastModified }
    val isHidden: Boolean get() = kind == FolderKind.Hidden
    val isRoot: Boolean get() = kind == FolderKind.Root
    val isVirtual: Boolean get() = kind == FolderKind.Favorites || kind == FolderKind.All
    val hiddenFromGalleries: Boolean get() = isHidden || noMedia

    companion object {
        const val FAVORITES_PATH = "::favorites"
        const val ALL_PATH = "::all"
    }
}

enum class ViewMode { List, Grid2, Grid4 }
