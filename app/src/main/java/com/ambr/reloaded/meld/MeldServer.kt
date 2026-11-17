package com.ambr.reloaded.meld

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import com.ambr.reloaded.R
import com.ambr.reloaded.data.VideoFolder
import com.ambr.reloaded.data.VideoFormats
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.io.RandomAccessFile
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.concurrent.thread

/** Someone who connected to us: another AMBR phone (with its own server) or a web browser. */
data class MeldPeer(val name: String, val urls: List<String>, val token: String?, val isBrowser: Boolean)

/**
 * Mind Meld's tiny HTTP server. Runs only while the Mind Meld screen is open.
 *
 * Auth: AMBR phones send the token from the QR code; browsers type the 6-digit code once and get
 * a cookie. Hidden (vault) folders are only listed if [shareHidden] is on.
 */
class MeldServer(
    private val context: Context,
    port: Int,
    val token: String,
    val code: String,
    private val deviceName: String,
    private val catalog: () -> List<VideoFolder>,
    private val uploadRoot: File,
    private val onPeer: (MeldPeer) -> Unit,
    private val onReceived: (File) -> Unit,
) : NanoHTTPD(port) {

    @Volatile var shareHidden = false
    private var failedLogins = 0
    private var lockedUntil = 0L

    fun startServer() = start(60_000, false)

    private fun folderId(path: String) =
        MessageDigest.getInstance("SHA-1").digest(path.toByteArray()).joinToString("") { "%02x".format(it) }.take(12)

    private fun visibleFolders() = catalog().filter { !it.isVirtual && (shareHidden || !it.isHidden) }
    private fun folderById(id: String) = visibleFolders().firstOrNull { folderId(it.path) == id }

    override fun serve(session: IHTTPSession): Response = try {
        route(session)
    } catch (e: Exception) {
        android.util.Log.e("AMBR", "Meld server error on ${session.uri}", e)
        json(Response.Status.INTERNAL_ERROR, JSONObject().put("error", e.message ?: "error"))
    }.also {
        it.addHeader("Access-Control-Allow-Origin", "*")
        it.addHeader("Cache-Control", "no-store")
    }

    private fun authed(s: IHTTPSession): Boolean {
        val t = s.headers["x-ambr-token"] ?: s.cookies.read("ambr") ?: s.parameters["t"]?.firstOrNull()
        return t == token
    }

    private fun route(s: IHTTPSession): Response {
        val uri = s.uri
        // Public bits: the portal page and its assets, plus login.
        when {
            uri == "/" || uri == "/index.html" ->
                return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", portalHtml())
            uri == "/logo.webp" -> return resource(R.drawable.ambr_logo, "image/webp")
            uri == "/font.ttf" -> return resource(R.font.bricolage, "font/ttf")
            uri == "/api/login" && s.method == Method.POST -> return login(s)
            uri == "/api/hello" -> return json(Response.Status.OK, JSONObject().put("app", "ambr").put("name", deviceName))
        }
        if (!authed(s)) return json(Response.Status.UNAUTHORIZED, JSONObject().put("error", "Enter the code shown in AMBR"))

        val parts = uri.trim('/').split('/').map { java.net.URLDecoder.decode(it, "UTF-8") }
        return when {
            uri == "/api/peer" && s.method == Method.POST -> registerPeer(s)
            uri == "/api/folders" -> listFolders()
            parts.size == 3 && parts[0] == "api" && parts[1] == "folders" -> listVideos(parts[2])
            parts.size == 3 && parts[0] == "f" -> serveFile(s, parts[1], parts[2])
            parts.size == 3 && parts[0] == "thumb" -> thumbnail(parts[1], parts[2])
            parts.size == 2 && parts[0] == "zip" -> zipFolder(parts[1])
            uri == "/api/upload/status" -> uploadStatus(s)
            uri == "/api/upload" && (s.method == Method.PUT || s.method == Method.POST) -> upload(s)
            else -> json(Response.Status.NOT_FOUND, JSONObject().put("error", "not found"))
        }
    }

    // ------------------------------------------------------------------ auth

    private fun login(s: IHTTPSession): Response {
        val now = System.currentTimeMillis()
        if (now < lockedUntil) return json(Response.Status.FORBIDDEN, JSONObject().put("error", "Too many tries. Wait a few seconds."))
        val body = readBody(s)
        val entered = runCatching { JSONObject(body).getString("code") }.getOrDefault(body).trim()
        if (entered != code) {
            if (++failedLogins >= 5) { lockedUntil = now + 30_000; failedLogins = 0 }
            return json(Response.Status.UNAUTHORIZED, JSONObject().put("error", "Wrong code"))
        }
        failedLogins = 0
        val isApp = s.headers["x-ambr-client"] != null
        if (!isApp) onPeer(MeldPeer("Web browser (${s.remoteIpAddress})", emptyList(), null, isBrowser = true))
        return json(Response.Status.OK, JSONObject().put("token", token).put("name", deviceName)).also {
            it.addHeader("Set-Cookie", "ambr=$token; Path=/; HttpOnly; SameSite=Strict")
        }
    }

    private fun registerPeer(s: IHTTPSession): Response {
        val j = JSONObject(readBody(s))
        val urls = j.optJSONArray("urls")?.let { a -> List(a.length()) { a.getString(it) } }.orEmpty()
        onPeer(MeldPeer(j.optString("name", "AMBR"), urls, j.optString("token").ifEmpty { null }, isBrowser = false))
        return json(Response.Status.OK, JSONObject().put("name", deviceName))
    }

    // --------------------------------------------------------------- catalog

    private fun listFolders(): Response {
        val arr = JSONArray()
        visibleFolders().forEach { f ->
            arr.put(
                JSONObject()
                    .put("id", folderId(f.path)).put("name", f.name).put("count", f.count)
                    .put("bytes", f.totalBytes).put("hidden", f.isHidden)
                    .put("cover", f.cover?.name ?: JSONObject.NULL),
            )
        }
        return json(Response.Status.OK, JSONObject().put("name", deviceName).put("folders", arr))
    }

    private fun listVideos(id: String): Response {
        val f = folderById(id) ?: return notFound()
        val arr = JSONArray()
        f.videos.sortedByDescending { it.lastModified }.forEach { v ->
            arr.put(JSONObject().put("name", v.name).put("size", v.sizeBytes).put("mtime", v.lastModified))
        }
        return json(Response.Status.OK, JSONObject().put("id", id).put("name", f.name).put("videos", arr))
    }

    private fun fileIn(id: String, name: String): File? {
        val f = folderById(id) ?: return null
        val file = File(f.path, name)
        // No path tricks: the file must be a video directly inside that folder.
        return file.takeIf { it.parentFile?.absolutePath == File(f.path).absolutePath && VideoFormats.isVideo(it) }
    }

    // ------------------------------------------------------------- streaming

    private fun serveFile(s: IHTTPSession, id: String, name: String): Response {
        val file = fileIn(id, name) ?: return notFound()
        val len = file.length()
        val mime = mimeOf(file)
        val range = s.headers["range"]?.removePrefix("bytes=")?.split('-')
        val resp = if (range != null && range[0].isNotEmpty()) {
            val start = range[0].toLong()
            val end = range.getOrNull(1)?.takeIf { it.isNotEmpty() }?.toLong()?.coerceAtMost(len - 1) ?: (len - 1)
            if (start >= len) {
                return newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, "text/plain", "").also {
                    it.addHeader("Content-Range", "bytes */$len")
                }
            }
            val input = FileInputStream(file).apply { skipFully(start) }
            newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mime, Bounded(input, end - start + 1), end - start + 1).also {
                it.addHeader("Content-Range", "bytes $start-$end/$len")
            }
        } else {
            newFixedLengthResponse(Response.Status.OK, mime, FileInputStream(file), len)
        }
        resp.addHeader("Accept-Ranges", "bytes")
        if (s.parameters["dl"] != null) {
            resp.addHeader("Content-Disposition", "attachment; filename*=UTF-8''" + URLEncoder.encode(name, "UTF-8").replace("+", "%20"))
        }
        return resp
    }

    private fun thumbnail(id: String, name: String): Response {
        val file = fileIn(id, name) ?: return notFound()
        val cache = File(context.cacheDir, "meld-thumbs/${folderId(file.absolutePath)}-${file.lastModified()}.jpg")
        if (!cache.exists()) {
            cache.parentFile?.mkdirs()
            val bmp = runCatching {
                com.ambr.reloaded.util.withRetriever(file.absolutePath) { r ->
                    if (android.os.Build.VERSION.SDK_INT >= 27) {
                        r.getScaledFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 360, 640)
                    } else {
                        r.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { full ->
                            val scale = 360f / minOf(full.width, full.height).coerceAtLeast(1)
                            Bitmap.createScaledBitmap(full, (full.width * scale).toInt(), (full.height * scale).toInt(), true)
                        }
                    }
                }
            }.getOrNull() ?: return notFound()
            cache.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 78, it) }
        }
        return newFixedLengthResponse(Response.Status.OK, "image/jpeg", FileInputStream(cache), cache.length()).also {
            it.addHeader("Cache-Control", "max-age=3600")
        }
    }

    /** Streams a whole folder as a .zip (no recompression: videos are already compressed). */
    private fun zipFolder(id: String): Response {
        val f = folderById(id) ?: return notFound()
        val pipeIn = PipedInputStream(1 shl 16)
        val pipeOut = PipedOutputStream(pipeIn)
        thread(name = "meld-zip") {
            runCatching {
                ZipOutputStream(pipeOut).use { zip ->
                    zip.setLevel(0)
                    f.videos.forEach { v ->
                        zip.putNextEntry(ZipEntry("${f.name.substringAfterLast('/')}/${v.name}"))
                        v.file.inputStream().use { it.copyTo(zip, 1 shl 16) }
                        zip.closeEntry()
                    }
                }
            }
        }
        return newChunkedResponse(Response.Status.OK, "application/zip", pipeIn).also {
            it.addHeader("Content-Disposition", "attachment; filename*=UTF-8''" + URLEncoder.encode("${f.name}.zip", "UTF-8").replace("+", "%20"))
        }
    }

    // --------------------------------------------------------------- uploads

    private fun safeName(raw: String) = raw.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().trimStart('.').ifEmpty { "video.mp4" }

    private fun uploadTarget(s: IHTTPSession): Pair<File, File> {
        val folder = safeName(s.parameters["folder"]?.firstOrNull() ?: "Mind Meld")
        val name = safeName(s.parameters["name"]?.firstOrNull() ?: "video.mp4")
        val dir = File(uploadRoot, folder)
        return File(dir, name) to File(dir, ".part-$name")
    }

    private fun uploadStatus(s: IHTTPSession): Response {
        val (final, part) = uploadTarget(s)
        val size = s.parameters["size"]?.firstOrNull()?.toLongOrNull()
        val complete = final.exists() && (size == null || final.length() == size)
        return json(Response.Status.OK, JSONObject().put("received", if (complete) -1 else part.length()))
    }

    /** PUT ?folder=&name=&offset=&size= with the raw bytes (from offset). Resumable. */
    private fun upload(s: IHTTPSession): Response {
        val (final, part) = uploadTarget(s)
        val offset = s.parameters["offset"]?.firstOrNull()?.toLongOrNull() ?: 0L
        val total = s.parameters["size"]?.firstOrNull()?.toLongOrNull()
        val length = s.headers["content-length"]?.toLongOrNull() ?: return json(Response.Status.BAD_REQUEST, JSONObject().put("error", "length"))
        final.parentFile?.mkdirs()
        if (offset == 0L) part.delete()
        if (part.length() != offset) return json(Response.Status.BAD_REQUEST, JSONObject().put("error", "offset").put("received", part.length()))
        RandomAccessFile(part, "rw").use { raf ->
            raf.seek(offset)
            val buf = ByteArray(1 shl 16)
            var left = length
            val input = s.inputStream
            while (left > 0) {
                val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                if (n < 0) break
                raf.write(buf, 0, n)
                left -= n
            }
        }
        if (total != null && part.length() >= total) {
            var target = final
            var n = 2
            while (target.exists()) target = File(final.parentFile, "${final.nameWithoutExtension} ($n).${final.extension}").also { n++ }
            part.renameTo(target)
            onReceived(target)
            return json(Response.Status.OK, JSONObject().put("done", true).put("name", target.name))
        }
        return json(Response.Status.OK, JSONObject().put("received", part.length()))
    }

    // ----------------------------------------------------------------- utils

    private fun readBody(s: IHTTPSession): String {
        val len = s.headers["content-length"]?.toIntOrNull() ?: return ""
        val buf = ByteArray(len)
        var read = 0
        while (read < len) {
            val n = s.inputStream.read(buf, read, len - read)
            if (n < 0) break
            read += n
        }
        return String(buf, 0, read)
    }

    private fun json(status: Response.Status, body: JSONObject) =
        newFixedLengthResponse(status, "application/json", body.toString())

    private fun notFound() = json(Response.Status.NOT_FOUND, JSONObject().put("error", "not found"))

    private fun resource(id: Int, mime: String): Response {
        val bytes = context.resources.openRawResource(id).use { it.readBytes() }
        return newFixedLengthResponse(Response.Status.OK, mime, bytes.inputStream(), bytes.size.toLong()).also {
            it.addHeader("Cache-Control", "max-age=86400")
        }
    }

    private var portalCache: String? = null
    private fun portalHtml(): String = portalCache ?: context.assets.open("portal.html").bufferedReader().readText()
        .replace("{{NAME}}", deviceName.replace("<", "&lt;")).also { portalCache = it }

    private fun mimeOf(f: File) = when (f.extension.lowercase()) {
        "mp4", "m4v" -> "video/mp4"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "mov" -> "video/quicktime"
        "3gp" -> "video/3gpp"
        "avi" -> "video/x-msvideo"
        else -> "application/octet-stream"
    }

    private fun FileInputStream.skipFully(n: Long) {
        var left = n
        while (left > 0) {
            val s = skip(left)
            if (s <= 0) break
            left -= s
        }
    }

    private class Bounded(input: InputStream, private var left: Long) : FilterInputStream(input) {
        override fun read(): Int = if (left <= 0) -1 else super.read().also { if (it >= 0) left-- }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (left <= 0) return -1
            val n = super.read(b, off, minOf(len.toLong(), left).toInt())
            if (n > 0) left -= n
            return n
        }
    }
}
