package com.ambr.reloaded.share

import android.content.Context
import java.io.File
import java.util.UUID

/**
 * F-Droid build: no link downloader. F-Droid builds everything from source and doesn't allow the
 * prebuilt Python/yt-dlp/FFmpeg binaries the full build bundles. Sharing video *files* still works.
 */
object YtDlp {
    const val available = false

    data class Preview(val title: String?, val uploader: String?, val durationSec: Int, val thumbnail: String?)

    suspend fun info(context: Context, url: String, maxHeight: Int): Preview =
        throw UnsupportedOperationException("Link downloads aren't included in this version of AMBR")

    suspend fun version(context: Context): String? = null
    suspend fun update(context: Context): Boolean = false
}

object DownloadWorker {
    fun enqueue(context: Context, url: String, dir: File, folderName: String, title: String?, height: Int, hidden: Boolean): UUID =
        throw UnsupportedOperationException("Link downloads aren't included in this version of AMBR")
}
