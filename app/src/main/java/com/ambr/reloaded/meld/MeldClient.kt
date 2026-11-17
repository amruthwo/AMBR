package com.ambr.reloaded.meld

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.coroutineContext

data class RemoteFolder(val id: String, val name: String, val count: Int, val bytes: Long, val hidden: Boolean, val cover: String?)
data class RemoteVideo(val name: String, val size: Long, val mtime: Long)

/** Talks to another AMBR's [MeldServer]. */
class MeldClient(val base: String, val token: String) {

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun open(path: String, method: String = "GET", timeout: Int = 15_000): HttpURLConnection =
        (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = timeout
            readTimeout = 30_000
            setRequestProperty("X-AMBR-Token", token)
            setRequestProperty("X-AMBR-Client", "app")
        }

    private fun getJson(path: String): JSONObject {
        val c = open(path)
        try {
            check(c.responseCode == 200) { "HTTP ${c.responseCode}" }
            return JSONObject(c.inputStream.bufferedReader().readText())
        } finally { c.disconnect() }
    }

    fun thumbUrl(folderId: String, name: String) = "$base/thumb/$folderId/${enc(name)}?t=$token"
    fun fileUrl(folderId: String, name: String) = "$base/f/$folderId/${enc(name)}?t=$token"

    suspend fun folders(): Pair<String, List<RemoteFolder>> = withContext(Dispatchers.IO) {
        val j = getJson("/api/folders")
        val arr = j.getJSONArray("folders")
        j.optString("name", "AMBR") to List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            RemoteFolder(o.getString("id"), o.getString("name"), o.getInt("count"), o.getLong("bytes"), o.optBoolean("hidden"), o.optString("cover").ifEmpty { null })
        }
    }

    suspend fun videos(folderId: String): List<RemoteVideo> = withContext(Dispatchers.IO) {
        val arr: JSONArray = getJson("/api/folders/$folderId").getJSONArray("videos")
        List(arr.length()) { i -> arr.getJSONObject(i).let { RemoteVideo(it.getString("name"), it.getLong("size"), it.getLong("mtime")) } }
    }

    /** Tells the host how to reach us back so it can browse our videos too. */
    suspend fun introduce(myName: String, myUrls: List<String>, myToken: String) = withContext(Dispatchers.IO) {
        val c = open("/api/peer", "POST")
        try {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use {
                it.write(JSONObject().put("name", myName).put("urls", JSONArray(myUrls)).put("token", myToken).toString().toByteArray())
            }
            c.responseCode
        } finally { c.disconnect() }
    }

    /** Pulls one file into [destDir], resuming a partial download. Returns false if skipped (already have it). */
    suspend fun download(folderId: String, video: RemoteVideo, destDir: File, onBytes: (Long) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            destDir.mkdirs()
            val final = File(destDir, video.name)
            if (final.exists() && final.length() == video.size) return@withContext false
            val part = File(destDir, ".part-${video.name}")
            val from = part.length().coerceAtMost(video.size)
            val c = open("/f/$folderId/${enc(video.name)}")
            try {
                if (from > 0) c.setRequestProperty("Range", "bytes=$from-")
                val code = c.responseCode
                check(code == 200 || code == 206) { "HTTP $code" }
                val append = code == 206
                RandomAccessFile(part, "rw").use { raf ->
                    if (append) raf.seek(from) else raf.setLength(0)
                    var done = if (append) from else 0L
                    onBytes(done)
                    val buf = ByteArray(1 shl 16)
                    c.inputStream.use { input ->
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            raf.write(buf, 0, n)
                            done += n
                            onBytes(done)
                        }
                    }
                }
            } finally { c.disconnect() }
            check(part.length() == video.size) { "Transfer interrupted" }
            var target = final
            var n = 2
            while (target.exists()) target = File(destDir, "${final.nameWithoutExtension} ($n).${final.extension}").also { n++ }
            part.renameTo(target)
            target.setLastModified(video.mtime)
            true
        }

    /** Pushes one file into the other phone's [folderName], resuming where it left off. */
    suspend fun upload(file: File, folderName: String, onBytes: (Long) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val q = "folder=${enc(folderName)}&name=${enc(file.name)}&size=${file.length()}"
        val received = getJson("/api/upload/status?$q").optLong("received", 0)
        if (received < 0) return@withContext false   // they already have it
        val offset = received.coerceAtMost(file.length())
        val c = open("/api/upload?$q&offset=$offset", "PUT", timeout = 15_000)
        try {
            c.doOutput = true
            c.setFixedLengthStreamingMode(file.length() - offset)
            c.setRequestProperty("Content-Type", "application/octet-stream")
            var done = offset
            onBytes(done)
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(offset)
                val buf = ByteArray(1 shl 16)
                c.outputStream.use { out ->
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = raf.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        onBytes(done)
                    }
                }
            }
            check(c.responseCode == 200) { "HTTP ${c.responseCode}" }
        } finally { c.disconnect() }
        true
    }

    companion object {
        /** Finds which of the host's addresses answers (first one wins). */
        suspend fun reach(hosts: List<String>, port: Int): String? = withContext(Dispatchers.IO) {
            hosts.firstNotNullOfOrNull { h ->
                runCatching {
                    val c = URL("http://$h:$port/api/hello").openConnection() as HttpURLConnection
                    c.connectTimeout = 2500; c.readTimeout = 2500
                    try { if (c.responseCode == 200 && "ambr" in c.inputStream.bufferedReader().readText()) "http://$h:$port" else null }
                    finally { c.disconnect() }
                }.getOrNull()
            }
        }

        /** Trades the 6-digit code shown on the other phone for its access token. */
        suspend fun login(base: String, code: String): String? = withContext(Dispatchers.IO) {
            runCatching {
                val c = URL("$base/api/login").openConnection() as HttpURLConnection
                c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 5000
                c.setRequestProperty("X-AMBR-Client", "app")
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(JSONObject().put("code", code).toString().toByteArray()) }
                try { if (c.responseCode == 200) JSONObject(c.inputStream.bufferedReader().readText()).getString("token") else null }
                finally { c.disconnect() }
            }.getOrNull()
        }
    }
}
