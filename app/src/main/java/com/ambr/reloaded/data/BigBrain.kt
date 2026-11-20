package com.ambr.reloaded.data

import android.content.Context
import androidx.core.content.edit
import java.io.File

/** How a folder plays: the shuffled, looping short-video feed, or in-order long-form with full controls. */
enum class FolderMode { BrainRot, BigBrain }

/** How a video fills the screen. */
enum class VideoScale { Auto, Fit, Fill, Stretch, Original }

/** "Episode 2" before "Episode 10": compares digit runs as numbers, ignoring case. */
object NaturalOrder : Comparator<String> {
    private val chunk = Regex("""\d+|\D+""")
    override fun compare(a: String, b: String): Int {
        val x = chunk.findAll(a.lowercase()).map { it.value }.toList()
        val y = chunk.findAll(b.lowercase()).map { it.value }.toList()
        for (i in 0 until minOf(x.size, y.size)) {
            val p = x[i]; val q = y[i]
            val c = if (p[0].isDigit() && q[0].isDigit()) {
                p.trimStart('0').length.compareTo(q.trimStart('0').length).takeIf { it != 0 } ?: p.trimStart('0').compareTo(q.trimStart('0'))
            } else p.compareTo(q)
            if (c != 0) return c
        }
        return x.size.compareTo(y.size)
    }
}

/** Where you left off in each long-form video (and whether you finished it). */
class ProgressStore(context: Context) {
    private val sp = context.getSharedPreferences("ambr_progress", Context.MODE_PRIVATE)

    data class Entry(val positionMs: Long, val durationMs: Long) {
        val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
        val watched: Boolean get() = durationMs > 0 && positionMs >= durationMs * 0.95
    }

    fun get(path: String): Entry? = sp.getString(path, null)?.split(':')?.let { p ->
        runCatching { Entry(p[0].toLong(), p[1].toLong()) }.getOrNull()
    }

    fun set(path: String, positionMs: Long, durationMs: Long) = sp.edit { putString(path, "$positionMs:$durationMs") }
    fun markWatched(path: String, durationMs: Long) = set(path, durationMs, durationMs)
    fun clear(path: String) = sp.edit { remove(path) }
    fun move(old: String, new: String) { get(old)?.let { set(new, it.positionMs, it.durationMs); clear(old) } }
    fun all(): Map<String, Entry> = sp.all.keys.mapNotNull { k -> get(k)?.let { k to it } }.toMap()
}

/** A Big Brain folder: its own videos plus its sub-folders as seasons, all in natural order. */
data class Show(val folder: VideoFolder, val seasons: List<VideoFolder>) {
    val episodes: List<Video> get() = folder.videos.sortedWith(compareBy(NaturalOrder) { it.name })
    /** Everything in viewing order: loose episodes first, then each season in order. */
    val playlist: List<Video> get() = episodes + seasons.flatMap { s -> s.videos.sortedWith(compareBy(NaturalOrder) { it.name }) }

    companion object {
        /** Builds a show from the scanned folder list (seasons = folders anywhere under [root]). */
        fun of(root: VideoFolder, all: List<VideoFolder>): Show {
            val prefix = root.path + File.separator
            val seasons = all.filter { it.path.startsWith(prefix) }
                .sortedWith(compareBy(NaturalOrder) { it.path.removePrefix(prefix) })
            return Show(root, seasons)
        }
    }
}
