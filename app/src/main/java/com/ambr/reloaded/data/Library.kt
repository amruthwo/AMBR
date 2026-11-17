package com.ambr.reloaded.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.core.content.FileProvider
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import coil3.video.videoFrameMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/** Video lengths, remembered by path + size + date so each file is only probed once. */
class DurationCache(context: Context) {
    private val file = File(context.filesDir, "durations.json")
    private val cache = mutableMapOf<String, Long>()
    private var loaded = false

    private fun key(v: Video) = "${v.path}|${v.sizeBytes}|${v.lastModified}"

    suspend fun durations(videos: List<Video>, progress: Progress): Map<String, Long> = withContext(Dispatchers.IO) {
        if (!loaded) {
            runCatching {
                val json = JSONObject(file.readText())
                json.keys().forEach { cache[it] = json.getLong(it) }
            }
            loaded = true
        }
        val out = HashMap<String, Long>()
        var dirty = false
        videos.forEachIndexed { i, v ->
            coroutineContext.ensureActive()
            progress(i, videos.size)
            val ms = cache[key(v)] ?: probe(v).also { cache[key(v)] = it; dirty = true }
            out[v.path] = ms
        }
        progress(videos.size, videos.size)
        if (dirty) {
            val live = videos.map(::key).toSet()
            runCatching { file.writeText(JSONObject(cache.filterKeys { it in live }.toMap()).toString()) }
        }
        out
    }

    private fun probe(v: Video): Long = runCatching {
        com.ambr.reloaded.util.withRetriever(v.path) { r ->
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        }
    }.getOrDefault(0L)
}

/**
 * Finds byte-identical videos. Cheap filters first, so big libraries stay fast:
 * same size → same first/last 1 MB → same full SHA-256.
 */
object DuplicateFinder {
    suspend fun find(videos: List<Video>, progress: Progress): List<List<Video>> = withContext(Dispatchers.IO) {
        val bySize = videos.filter { it.sizeBytes > 0 }.groupBy { it.sizeBytes }.values.filter { it.size > 1 }
        val candidates = bySize.flatten()
        var done = 0
        fun tick() { progress(++done, candidates.size * 2) }

        val byQuick = bySize.flatMap { group ->
            group.groupBy { coroutineContext.ensureActive(); quickHash(it.file).also { tick() } }.values
        }.filter { it.size > 1 }
        done = candidates.size
        val result = byQuick.flatMap { group ->
            group.groupBy { coroutineContext.ensureActive(); fullHash(it.file).also { tick() } }.values
        }.filter { it.size > 1 }
        progress(1, 1)
        // Oldest copy first: that's the one we suggest keeping.
        result.map { g -> g.sortedBy { it.lastModified } }.sortedByDescending { it.first().sizeBytes * (it.size - 1) }
    }

    private const val CHUNK = 1 shl 20

    private fun quickHash(f: File): String = runCatching {
        val md = MessageDigest.getInstance("SHA-256")
        RandomAccessFile(f, "r").use { raf ->
            val buf = ByteArray(CHUNK)
            val n = raf.read(buf); if (n > 0) md.update(buf, 0, n)
            if (raf.length() > CHUNK * 2) {
                raf.seek(raf.length() - CHUNK)
                val m = raf.read(buf); if (m > 0) md.update(buf, 0, m)
            }
        }
        md.digest().toHex()
    }.getOrDefault("err:${f.path}")

    private fun fullHash(f: File): String = runCatching {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().buffered(CHUNK).use { input ->
            val buf = ByteArray(CHUNK)
            while (true) {
                val n = input.read(buf); if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        md.digest().toHex()
    }.getOrDefault("err:${f.path}")

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}

/** Pulls a few vivid colours out of folder thumbnails for the home screen's ambient gradient. */
object Ambience {
    suspend fun colorsFrom(context: Context, covers: List<Video>): List<Int> = withContext(Dispatchers.IO) {
        val loader = SingletonImageLoader.get(context)
        covers.take(6).mapNotNull { v ->
            val request = ImageRequest.Builder(context)
                .data(v.file)
                .videoFrameMillis(1000)
                .size(96)
                .allowHardware(false)
                .build()
            val bmp: Bitmap = (loader.execute(request) as? SuccessResult)?.image?.toBitmap() ?: return@mapNotNull null
            val p = Palette.from(bmp).maximumColorCount(12).generate()
            (p.vibrantSwatch ?: p.lightVibrantSwatch ?: p.dominantSwatch ?: p.mutedSwatch)?.rgb
        }.distinct()
    }
}

object ShareOut {
    fun share(context: Context, videos: List<Video>) {
        if (videos.isEmpty()) return
        val authority = "${context.packageName}.files"
        val uris = videos.map { FileProvider.getUriForFile(context, authority, it.file) }
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }.apply {
            type = "video/*"
            clipData = ClipData.newRawUri(null, uris.first()).also { clip ->
                uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val title = if (videos.size == 1) videos.first().title else "${videos.size} videos"
        context.startActivity(Intent.createChooser(send, "Share $title").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
