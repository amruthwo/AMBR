package com.ambr.reloaded.share

import com.ambr.reloaded.util.str

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ambr.reloaded.MainActivity
import com.ambr.reloaded.R
import com.ambr.reloaded.data.VideoFormats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.coroutines.coroutineContext

/** Copies videos another app shared directly (content:// URIs) into a folder. */
object DirectImport {
    suspend fun copy(context: Context, uris: List<Uri>, dir: File, hidden: Boolean, progress: (Int, Int) -> Unit): Int =
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            var ok = 0
            val saved = mutableListOf<String>()
            uris.forEachIndexed { i, uri ->
                progress(i, uris.size)
                runCatching {
                    val name = displayName(context, uri) ?: str(R.string.downloader_shared_video_x_mp4, System.currentTimeMillis())
                    val safe = name.replace(Regex("""[\\/:*?"<>|]"""), "_")
                    var target = File(dir, safe)
                    var n = 2
                    while (target.exists()) target = File(dir, "${safe.substringBeforeLast('.')} ($n).${safe.substringAfterLast('.', "mp4")}").also { n++ }
                    context.contentResolver.openInputStream(uri)!!.use { input ->
                        target.outputStream().use { input.copyTo(it, 1 shl 16) }
                    }
                    saved += target.absolutePath
                    ok++
                }
            }
            progress(uris.size, uris.size)
            if (!hidden && saved.isNotEmpty()) {
                android.media.MediaScannerConnection.scanFile(context, saved.toTypedArray(), null, null)
            }
            ok
        }

    private fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()?.let { if ('.' in it) it else "$it.mp4" }
}
