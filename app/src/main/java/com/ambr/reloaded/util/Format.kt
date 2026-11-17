package com.ambr.reloaded.util

import java.util.Locale

fun formatDuration(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes / 1024.0
    var i = 0
    while (value >= 1024 && i < units.lastIndex) { value /= 1024; i++ }
    return if (value >= 100) String.format(Locale.US, "%.0f %s", value, units[i])
    else String.format(Locale.US, "%.1f %s", value, units[i])
}

fun pluralVideos(n: Int): String = com.ambr.reloaded.AmbrApp.instance.resources.getQuantityString(com.ambr.reloaded.R.plurals.videos, n, n)

/**
 * Looks up a translatable string from res/values(-xx)/strings.xml. Works from Compose screens,
 * ViewModels, workers and notifications alike (AMBR's activity is recreated on language change).
 */
fun str(@androidx.annotation.StringRes id: Int, vararg args: Any?): String =
    com.ambr.reloaded.AmbrApp.instance.getString(id, *args)

/** MediaMetadataRetriever is only AutoCloseable on Android 10+; this releases it on every version. */
inline fun <T> withRetriever(path: String, block: (android.media.MediaMetadataRetriever) -> T): T {
    val r = android.media.MediaMetadataRetriever()
    try {
        r.setDataSource(path)
        return block(r)
    } finally {
        runCatching { r.release() }
    }
}

/** Quantity strings ("1 copy" / "3 copies"), correct for each language's plural rules. */
fun plural(@androidx.annotation.PluralsRes id: Int, n: Int): String =
    com.ambr.reloaded.AmbrApp.instance.resources.getQuantityString(id, n, n)
