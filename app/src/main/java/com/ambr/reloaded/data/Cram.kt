package com.ambr.reloaded.data

import com.ambr.reloaded.util.pluralVideos

import com.ambr.reloaded.util.str

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ambr.reloaded.MainActivity
import com.ambr.reloaded.R
import com.ambr.reloaded.share.DownloadWorker
import com.ambr.reloaded.util.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** What a video looks like on disk, for deciding whether cramming it is worth it. */
data class Probe(val width: Int, val height: Int, val durationMs: Long, val bitrate: Long) {
    val shortSide: Int get() = minOf(width, height)
}

data class CramCandidate(val video: Video, val probe: Probe, val estimatedBytes: Long) {
    val savings: Long get() = (video.sizeBytes - estimatedBytes).coerceAtLeast(0)
}

/**
 * Cram Session: re-encode big videos smaller with the phone's hardware encoder (Media3 Transformer),
 * one at a time, from a queue saved on disk so it survives the app closing or the phone restarting.
 */
object Cram {
    const val WORK = "cram"
    private const val CHANNEL = "cram"

    fun videoBitrate(height: Int) = when {
        height <= 480 -> 1_200_000
        height <= 720 -> 2_500_000
        else -> 5_000_000
    }
    private const val AUDIO_BITRATE = 128_000

    fun probe(file: File): Probe? = runCatching {
        com.ambr.reloaded.util.withRetriever(file.absolutePath) { r ->
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            var w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            var h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 270) { val t = w; w = h; h = t }
            val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val br = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()
                ?: if (dur > 0) file.length() * 8000 / dur else 0L
            Probe(w, h, dur, br)
        }
    }.getOrNull()?.takeIf { it.width > 0 && it.durationMs > 0 }

    /** Expected size after cramming to [height]. */
    fun estimate(p: Probe, height: Int): Long {
        val vb = if (p.shortSide <= height) minOf(p.bitrate, videoBitrate(height).toLong()) else videoBitrate(height).toLong()
        return (vb + AUDIO_BITRATE) * p.durationMs / 8000
    }

    /** Videos that would shrink by at least 25%. */
    suspend fun candidates(videos: List<Video>, height: Int, progress: Progress): List<CramCandidate> =
        withContext(Dispatchers.IO) {
            val out = mutableListOf<CramCandidate>()
            videos.forEachIndexed { i, v ->
                coroutineContext.ensureActive()
                progress(i, videos.size)
                val p = probe(v.file) ?: return@forEachIndexed
                val est = estimate(p, height)
                if (est < v.sizeBytes * 0.75) out += CramCandidate(v, p, est)
            }
            progress(videos.size, videos.size)
            out.sortedByDescending { it.savings }
        }

    // ---------------------------------------------------------------- queue

    private fun queueFile(c: Context) = File(c.filesDir, "cram_queue.json")

    data class QueueState(val pending: List<String>, val height: Int, val done: Int, val failed: Int, val saved: Long, val kept: Int = 0)

    @Synchronized
    fun readQueue(c: Context): QueueState = runCatching {
        val j = JSONObject(queueFile(c).readText())
        val arr = j.getJSONArray("pending")
        QueueState(
            List(arr.length()) { arr.getString(it) },
            j.optInt("height", 720), j.optInt("done"), j.optInt("failed"), j.optLong("saved"), j.optInt("kept"),
        )
    }.getOrDefault(QueueState(emptyList(), 720, 0, 0, 0))

    @Synchronized
    private fun writeQueue(c: Context, q: QueueState) {
        val j = JSONObject()
            .put("pending", JSONArray(q.pending))
            .put("height", q.height).put("done", q.done).put("failed", q.failed).put("saved", q.saved).put("kept", q.kept)
        queueFile(c).writeText(j.toString())
    }

    /** Adds videos to the queue (deduplicated) and starts / resumes the worker. */
    fun enqueue(c: Context, paths: List<String>, height: Int, chargingOnly: Boolean) {
        val q = readQueue(c)
        val fresh = if (q.pending.isEmpty()) QueueState(emptyList(), height, 0, 0, 0) else q
        writeQueue(c, fresh.copy(pending = (fresh.pending + paths).distinct(), height = height))
        start(c, chargingOnly)
    }

    fun start(c: Context, chargingOnly: Boolean) {
        val req = OneTimeWorkRequestBuilder<CramWorker>()
            .setConstraints(Constraints.Builder().setRequiresCharging(chargingOnly).build())
            .build()
        WorkManager.getInstance(c).enqueueUniqueWork(WORK, ExistingWorkPolicy.KEEP, req)
    }

    fun pause(c: Context) = WorkManager.getInstance(c).cancelUniqueWork(WORK)

    fun clear(c: Context) {
        pause(c)
        writeQueue(c, QueueState(emptyList(), 720, 0, 0, 0))
    }

    @Synchronized
    internal fun pop(c: Context, path: String, ok: Boolean, saved: Long) {
        val q = readQueue(c)
        writeQueue(
            c,
            q.copy(
                pending = q.pending - path,
                done = q.done + if (ok && saved > 0) 1 else 0,
                kept = q.kept + if (ok && saved == 0L) 1 else 0,
                failed = q.failed + if (ok) 0 else 1,
                saved = q.saved + saved,
            ),
        )
    }

    // -------------------------------------------------------------- encoding

    /** Re-encodes [src] to H.264/AAC MP4 at [height] (short side) into [out]. Runs on the main looper. */
    /** Thrown when the new file is on track to be no smaller than the original. */
    class NotWorthIt : Exception(str(R.string.cram_wouldn_t_get_smaller))

    suspend fun transcode(context: Context, src: File, out: File, height: Int, probe: Probe, onProgress: (Float) -> Unit) =
        withContext(Dispatchers.Main) {
            val effects = if (probe.shortSide > height) listOf(Presentation.createForShortSide(height)) else emptyList()
            val item = EditedMediaItem.Builder(MediaItem.fromUri(android.net.Uri.fromFile(src)))
                .setEffects(Effects(emptyList(), effects))
                .build()
            suspendCancellableCoroutine { cont ->
                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(
                        DefaultEncoderFactory.Builder(context)
                            .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(videoBitrate(height)).build())
                            .setEnableFallback(true)
                            .build(),
                    )
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (cont.isActive) cont.resume(Unit)
                        }
                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            if (cont.isActive) cont.resumeWithException(exportException)
                        }
                    })
                    .build()
                transformer.start(item, out.absolutePath)
                // Poll progress while it runs.
                val poller = kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                    val holder = ProgressHolder()
                    while (isActive) {
                        if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress / 100f)
                        // Bail out early if the new file is already nearly as big as the original.
                        if (out.length() > src.length() * 0.9) {
                            transformer.cancel()
                            if (cont.isActive) cont.resumeWithException(NotWorthIt())
                            break
                        }
                        delay(500)
                    }
                }
                cont.invokeOnCancellation { transformer.cancel(); poller.cancel() }
                cont.context[kotlinx.coroutines.Job]?.invokeOnCompletion { poller.cancel() }
            }
        }

    fun ensureChannel(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Cram Session", NotificationManager.IMPORTANCE_LOW))
        }
    }

    internal fun channel() = CHANNEL
}

class CramWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val notifId = 4242
    private val prefs = Prefs(context)

    override suspend fun doWork(): Result {
        Cram.ensureChannel(applicationContext)
        setForeground(info(str(R.string.cram_getting_ready), 0f, 0, 0))
        while (true) {
            coroutineContext.ensureActive()
            val q = Cram.readQueue(applicationContext)
            val path = q.pending.firstOrNull() ?: break
            val total = q.pending.size + q.done + q.failed
            val index = q.done + q.failed + 1
            val src = File(path)
            if (!src.exists()) { Cram.pop(applicationContext, path, ok = false, saved = 0); continue }
            val name = src.nameWithoutExtension
            setProgress(workDataOf("name" to name, "index" to index, "total" to total, "fraction" to 0f))
            setForeground(info(name, 0f, index, total))

            val probe = Cram.probe(src)
            if (probe == null) { Cram.pop(applicationContext, path, ok = false, saved = 0); continue }
            val tmp = File(src.parentFile, ".cram-$name.mp4")
            tmp.delete()
            val result = runCatching {
                var last = 0L
                Cram.transcode(applicationContext, src, tmp, q.height, probe) { f ->
                    val now = System.currentTimeMillis()
                    if (now - last > 800) {
                        last = now
                        setProgressAsync(workDataOf("name" to name, "index" to index, "total" to total, "fraction" to f))
                        if (DownloadWorker.canNotify(applicationContext)) {
                            NotificationManagerCompat.from(applicationContext).notify(notifId, info(name, f, index, total).notification)
                        }
                    }
                }
                finalize(src, tmp, probe)
            }
            tmp.takeIf { result.isFailure }?.delete()
            if (isStopped) { tmp.delete(); return Result.failure() }
            val notWorthIt = result.exceptionOrNull() is Cram.NotWorthIt
            if (result.isFailure && !notWorthIt) android.util.Log.e("AMBR", "Cram failed for $path", result.exceptionOrNull())
            Cram.pop(applicationContext, path, ok = result.isSuccess || notWorthIt, saved = result.getOrDefault(0L))
        }
        val q = Cram.readQueue(applicationContext)
        done(q)
        return Result.success()
    }

    /** Checks the new file, swaps it in, keeps date + favourite. Returns bytes saved (0 if kept original). */
    private fun finalize(src: File, tmp: File, before: Probe): Long {
        val after = Cram.probe(tmp) ?: error(str(R.string.cram_new_file_doesn_t_play))
        check(after.durationMs >= before.durationMs * 0.97 - 500) { str(R.string.cram_new_file_is_shorter_than) }
        if (tmp.length() >= src.length() * 0.9) { tmp.delete(); return 0L }  // not worth it: keep the original
        val saved = src.length() - tmp.length()
        val mtime = src.lastModified()
        var target = File(src.parentFile, "${src.nameWithoutExtension}.mp4")
        check(src.delete()) { str(R.string.cram_couldn_t_remove_the_original) }
        if (target.exists()) target = File(src.parentFile, str(R.string.cram_x_small_mp4, src.nameWithoutExtension))
        check(tmp.renameTo(target)) { str(R.string.cram_couldn_t_rename_the_new) }
        target.setLastModified(mtime)
        if (src.absolutePath != target.absolutePath && src.absolutePath in prefs.favorites) {
            prefs.favorites = prefs.favorites - src.absolutePath + target.absolutePath
        }
        android.media.MediaScannerConnection.scanFile(
            applicationContext, arrayOf(src.absolutePath, target.absolutePath), null, null,
        )
        return saved
    }

    private fun info(name: String, fraction: Float, index: Int, total: Int): ForegroundInfo {
        val pause = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val n = NotificationCompat.Builder(applicationContext, Cram.channel())
            .setSmallIcon(R.drawable.ic_stat_ambr)
            .setContentTitle(if (total > 0) str(R.string.cram_cram_session_x_of_x, index, total) else str(R.string.cram_cram_session))
            .setContentText(name)
            .setProgress(100, (fraction * 100).toInt(), total == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setColor(0xFFD52B84.toInt())
            .addAction(0, str(R.string.bits_pause), pause)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(notifId, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(notifId, n)
    }

    private fun done(q: Cram.QueueState) {
        if (!DownloadWorker.canNotify(applicationContext)) return
        val open = PendingIntent.getActivity(
            applicationContext, 0, Intent(applicationContext, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val text = buildString {
            append(str(R.string.cram_shrank_x_x_saved_x, pluralVideos(q.done), formatBytes(q.saved)))
            if (q.kept > 0) append(str(R.string.cram_x_already_as_small_as, q.kept))
            if (q.failed > 0) append(str(R.string.cram_x_couldn_t_be_converted, q.failed))
        }
        NotificationManagerCompat.from(applicationContext).notify(
            notifId + 1,
            NotificationCompat.Builder(applicationContext, Cram.channel())
                .setSmallIcon(R.drawable.ic_stat_ambr)
                .setContentTitle(str(R.string.cram_cram_session_finished))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setColor(0xFFD52B84.toInt())
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }
}
