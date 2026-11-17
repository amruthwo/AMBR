package com.ambr.reloaded.share

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
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.coroutines.coroutineContext

/** yt-dlp (Python) + FFmpeg, unpacked once per process. */
object YtDlp {
    @Volatile private var ready = false

    suspend fun ensure(context: Context) = withContext(Dispatchers.IO) {
        if (ready) return@withContext
        synchronized(this@YtDlp) {
            if (!ready) {
                YoutubeDL.getInstance().init(context.applicationContext)
                FFmpeg.getInstance().init(context.applicationContext)
                ready = true
            }
        }
    }

    /** What the share sheet shows before downloading. */
    data class Preview(val title: String?, val uploader: String?, val durationSec: Int, val thumbnail: String?)

    suspend fun info(context: Context, url: String, maxHeight: Int): Preview = withContext(Dispatchers.IO) {
        val prepared = LinkResolver.prepare(context, url, maxHeight)
        // Reddit pages already told us everything; no need to ask yt-dlp.
        if (prepared.title != null) return@withContext Preview(prepared.title, prepared.uploader, 0, prepared.thumbnail)
        ensure(context)
        val resolved = prepared.url
        val req = YoutubeDLRequest(resolved).apply {
            addOption("--no-playlist")
            addOption("--socket-timeout", "15")
            withCookies(context)
            withBrowserIdentity(context, resolved)
        }
        val i: VideoInfo = YoutubeDL.getInstance().getInfo(req)
        Preview(i.title, i.uploader, i.duration, i.thumbnail)
    }

    suspend fun version(context: Context): String? = withContext(Dispatchers.IO) {
        runCatching { ensure(context); YoutubeDL.getInstance().versionName(context) ?: YoutubeDL.getInstance().version(context) }.getOrNull()
    }

    /** Returns true when a newer yt-dlp was installed. */
    suspend fun update(context: Context): Boolean = withContext(Dispatchers.IO) {
        ensure(context)
        YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel._STABLE) == YoutubeDL.UpdateStatus.DONE
    }
}

/** Passes your in-app sign-ins (if any) to yt-dlp. */
private fun YoutubeDLRequest.withCookies(context: Context) {
    if (SiteCookies.hasAny(context)) addOption("--cookies", SiteCookies.file(context).absolutePath)
}

/** For sites with sign-ins, look like the same browser that holds the login cookies. */
private fun YoutubeDLRequest.withBrowserIdentity(context: Context, url: String) {
    if (Site.forUrl(url) != null) addOption("--user-agent", LinkResolver.browserUserAgent(context))
}

/** Background download with a progress notification. Survives the share sheet closing. */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val notifId = id.hashCode()

    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_URL) ?: return Result.failure()
        val dir = File(inputData.getString(KEY_DIR) ?: return Result.failure())
        val label = inputData.getString(KEY_TITLE) ?: url
        val folderName = inputData.getString(KEY_FOLDER) ?: dir.name
        val height = inputData.getInt(KEY_HEIGHT, 1080)
        val hidden = inputData.getBoolean(KEY_HIDDEN, false)

        ensureChannel(applicationContext)
        setForeground(foregroundInfo(label, 0, indeterminate = true))

        return try {
            YtDlp.ensure(applicationContext)
            dir.mkdirs()
            val before = dir.listFiles()?.map { it.name }?.toSet().orEmpty()
            val processId = id.toString()
            coroutineContext[Job]?.invokeOnCompletion { cause ->
                if (cause != null) YoutubeDL.getInstance().destroyProcessById(processId)
            }

            val prepared = LinkResolver.prepare(applicationContext, url, height)
            val resolved = prepared.url
            // Direct streams (e.g. Reddit) have no useful title of their own, so name the file after the post.
            val outName = prepared.title?.let { t ->
                t.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(90).replace("%", "%%") + ".%(ext)s"
            } ?: "%(title).90B [%(id)s].%(ext)s"
            val req = YoutubeDLRequest(resolved).apply {
                addOption("--no-playlist")
                addOption("--no-mtime")                   // file date = when you saved it
                addOption("--windows-filenames")          // safe names on every storage type
                addOption("--merge-output-format", "mp4")
                // Prefer H.264/AAC in MP4: plays everywhere, shares everywhere.
                addOption("-S", if (height > 0) "res:$height,vcodec:h264,acodec:aac" else "vcodec:h264,acodec:aac")
                addOption("-o", File(dir, outName).absolutePath)
                addOption("--newline")
                withCookies(applicationContext)
                withBrowserIdentity(applicationContext, resolved)
            }
            var lastUpdate = 0L
            withContext(Dispatchers.IO) {
                YoutubeDL.getInstance().execute(req, processId) { progress, _, _ ->
                    val now = System.currentTimeMillis()
                    if (now - lastUpdate > 700 && progress >= 0f) {
                        lastUpdate = now
                        notify(foregroundInfo(label, progress.toInt(), indeterminate = false).notification)
                        setProgressAsync(workDataOf(KEY_PROGRESS to progress))
                    }
                }
            }

            val added = dir.listFiles()?.filter { it.name !in before && VideoFormats.isVideo(it) }.orEmpty()
            if (!hidden && added.isNotEmpty()) {
                android.media.MediaScannerConnection.scanFile(
                    applicationContext, added.map { it.absolutePath }.toTypedArray(), null, null,
                )
            }
            val name = added.firstOrNull()?.nameWithoutExtension ?: label
            finished(true, "Saved to $folderName", name)
            Result.success()
        } catch (e: YoutubeDL.CanceledException) {
            Result.failure()
        } catch (e: Exception) {
            if (isStopped) return Result.failure()
            android.util.Log.e("AMBR", "Download failed for $url", e)
            val reason = e.message?.lines()?.lastOrNull { it.contains("ERROR", true) }
                ?.substringAfter("ERROR:")?.trim()?.take(160)
                ?: e.message?.take(160) ?: "Unknown error"
            val site = Site.forUrl(url)
            if (e.message.orEmpty().contains("unable to access the Reddit API", ignoreCase = true)) {
                finished(
                    false, "Reddit blocked this download",
                    "Reddit refused this network, and AMBR couldn't find the video on the post's page either. " +
                        "Try again on another network (Wi-Fi vs. mobile data), or sign in under Settings → Site sign-ins.",
                )
            } else if (site != null && SiteCookies.isAuthError(e.message.orEmpty())) {
                finished(
                    false, "${site.label} wants you to sign in",
                    "Tap to sign in to ${site.label} inside AMBR, then share the video again.",
                    signIn = site,
                )
            } else {
                finished(false, "Couldn't download", reason)
            }
            Result.failure(workDataOf(KEY_ERROR to reason))
        }
    }

    private fun foregroundInfo(title: String, percent: Int, indeterminate: Boolean): ForegroundInfo {
        val cancel = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val n = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_ambr)
            .setContentTitle("Downloading to AMBR")
            .setContentText(title)
            .setProgress(100, percent, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setColor(0xFFD52B84.toInt())
            .addAction(0, "Cancel", cancel)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(notifId, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(notifId, n)
    }

    private fun notify(n: android.app.Notification) {
        if (canNotify(applicationContext)) NotificationManagerCompat.from(applicationContext).notify(notifId, n)
    }

    private fun finished(ok: Boolean, title: String, text: String, signIn: Site? = null) {
        if (!canNotify(applicationContext)) return
        val target = if (signIn != null) SiteLoginActivity.intent(applicationContext, signIn)
        else Intent(applicationContext, MainActivity::class.java)
        val open = PendingIntent.getActivity(
            applicationContext, notifId,
            target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_ambr)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setColor(0xFFD52B84.toInt())
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        // A fresh id: the foreground notification is removed when the worker ends.
        NotificationManagerCompat.from(applicationContext).notify(notifId + 1, n)
    }

    companion object {
        const val CHANNEL = "downloads"
        const val KEY_URL = "url"
        const val KEY_DIR = "dir"
        const val KEY_TITLE = "title"
        const val KEY_FOLDER = "folder"
        const val KEY_HEIGHT = "height"
        const val KEY_HIDDEN = "hidden"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"

        fun enqueue(context: Context, url: String, dir: File, folderName: String, title: String?, height: Int, hidden: Boolean): UUID {
            val req = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(
                    workDataOf(
                        KEY_URL to url, KEY_DIR to dir.absolutePath, KEY_FOLDER to folderName,
                        KEY_TITLE to (title ?: url), KEY_HEIGHT to height, KEY_HIDDEN to hidden,
                    ),
                )
                .addTag("download")
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("dl:$url", ExistingWorkPolicy.KEEP, req)
            return req.id
        }

        fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW))
            }
        }

        fun canNotify(context: Context) = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
}

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
                    val name = displayName(context, uri) ?: "Shared video ${System.currentTimeMillis()}.mp4"
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
