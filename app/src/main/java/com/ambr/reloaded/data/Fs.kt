package com.ambr.reloaded.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import java.io.File
import java.io.IOException
import java.io.OutputStream

/**
 * Thrown when Android won't let AMBR write to a removable volume (SD card) with plain file
 * access, and the user hasn't granted it through the system folder picker yet.
 */
class NeedsSafAccess(val file: File) : IOException("AMBR needs permission to change files on this SD card.")

/**
 * The persisted folder grants from Android's folder picker (Storage Access Framework), mapped
 * to ordinary file paths, so the rest of AMBR can keep thinking in Files.
 */
object Saf {
    const val AUTHORITY = "com.android.externalstorage.documents"

    private fun trees(c: Context): List<Uri> =
        c.contentResolver.persistedUriPermissions
            .filter { it.isWritePermission && it.uri.authority == AUTHORITY }
            .map { it.uri }

    /** "XXXX-XXXX:Movies" → /storage/XXXX-XXXX/Movies; "primary:…" → internal storage. */
    fun pathOf(tree: Uri): File? = runCatching {
        val id = DocumentsContract.getTreeDocumentId(tree)
        val vol = id.substringBefore(':')
        val rel = id.substringAfter(':', "")
        val base = if (vol == "primary") Environment.getExternalStorageDirectory() else File("/storage/$vol")
        if (rel.isEmpty()) base else File(base, rel)
    }.getOrNull()

    /** The document URI for [file] under any granted tree, or null if none covers it. */
    fun docUri(c: Context, file: File): Uri? {
        for (tree in trees(c)) {
            val root = pathOf(tree) ?: continue
            val inside = file.absolutePath == root.absolutePath || file.absolutePath.startsWith(root.absolutePath + File.separator)
            if (!inside) continue
            val treeId = DocumentsContract.getTreeDocumentId(tree)
            val rel = file.relativeTo(root).path
            val docId = when {
                rel.isEmpty() -> treeId
                treeId.endsWith(":") -> treeId + rel
                else -> "$treeId/$rel"
            }
            return DocumentsContract.buildDocumentUriUsingTree(tree, docId)
        }
        return null
    }

    /**
     * Where the folder picker should open: [folder] itself. (Android refuses to grant a whole SD
     * card, so AMBR asks for each added folder instead.)
     */
    fun initialUri(folder: File): Uri {
        val m = Regex("^/storage/([^/]+)/?(.*)$").find(folder.absolutePath)
        val id = if (m == null) "primary:" else "${m.groupValues[1]}:${m.groupValues[2]}"
        return DocumentsContract.buildDocumentUri(AUTHORITY, id)
    }

    fun persist(c: Context, tree: Uri) {
        c.contentResolver.takePersistableUriPermission(
            tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    }

    fun covers(c: Context, file: File) = docUri(c, file) != null
}

/**
 * File operations that work everywhere AMBR stores or links videos: plain java.io where Android
 * allows it (internal storage, app storage, and SD cards on most Android 11+ phones with
 * "All files access"), and the SD-card folder grant where it doesn't.
 */
class Fs(private val context: Context) {
    private val cr get() = context.contentResolver
    private val primary = Environment.getExternalStorageDirectory().absolutePath
    private val probed = HashMap<String, Boolean>()

    /** /storage/emulated/0 or /storage/XXXX-XXXX for any path on shared storage. */
    fun volumeOf(f: File): File {
        val p = f.absolutePath
        if (p == primary || p.startsWith("$primary/")) return File(primary)
        val m = Regex("^/storage/([^/]+)").find(p)
        return if (m != null) File("/storage/${m.groupValues[1]}") else File("/")
    }

    fun isVolumeRoot(f: File) = f.absolutePath == volumeOf(f).absolutePath

    /** Can plain file calls change things in [dir]? Checked once per volume by writing a probe file. */
    fun direct(dir: File): Boolean {
        val p = dir.absolutePath
        if (p == primary || p.startsWith("$primary/") || p.startsWith(context.applicationInfo.dataDir)) return true
        val vol = volumeOf(dir).absolutePath
        return synchronized(probed) {
            probed.getOrPut(vol) {
                val probe = File(dir, ".ambr-probe-${System.nanoTime()}")
                runCatching { probe.createNewFile().also { probe.delete() } }.getOrDefault(false)
            }
        }
    }

    /** True if AMBR can change files in [dir] one way or the other. */
    fun writable(dir: File) = direct(dir) || Saf.covers(context, dir)

    private fun doc(f: File): Uri = Saf.docUri(context, f) ?: throw NeedsSafAccess(f)

    private fun nameOf(uri: Uri) = DocumentsContract.getDocumentId(uri).substringAfterLast('/').substringAfter(':')

    fun delete(f: File) {
        if (!f.exists()) return
        if (direct(f.parentFile ?: f)) {
            check(if (f.isDirectory) f.deleteRecursively() else f.delete()) { "Couldn't delete ${f.name}" }
        } else {
            check(DocumentsContract.deleteDocument(cr, doc(f))) { "Couldn't delete ${f.name}" }
        }
    }

    /** Renames in place; returns the new File. */
    fun rename(f: File, newName: String): File {
        val parent = f.parentFile!!
        if (direct(parent)) {
            val target = File(parent, newName)
            check(f.renameTo(target)) { "Couldn't rename ${f.name}" }
            return target
        }
        val uri = DocumentsContract.renameDocument(cr, doc(f), newName) ?: error("Couldn't rename ${f.name}")
        return File(parent, nameOf(uri))
    }

    fun mkdir(parent: File, name: String): File {
        if (direct(parent)) {
            val dir = File(parent, name)
            check(dir.mkdirs()) { "Couldn't create $name" }
            return dir
        }
        val uri = DocumentsContract.createDocument(cr, doc(parent), DocumentsContract.Document.MIME_TYPE_DIR, name)
            ?: error("Couldn't create $name")
        return File(parent, nameOf(uri))
    }

    /** Creates [name] in [dir] for writing. Returns the stream and the File it will end up as. */
    fun create(dir: File, name: String, mime: String = "video/mp4"): Pair<OutputStream, File> {
        if (direct(dir)) {
            val f = File(dir, name)
            return f.outputStream() to f
        }
        val uri = DocumentsContract.createDocument(cr, doc(dir), mime, name) ?: error("Couldn't create $name")
        val out = cr.openOutputStream(uri, "w") ?: error("Couldn't write $name")
        return out to File(dir, nameOf(uri))
    }

    /** Moves [src] into [destDir] as [name]. Same volume: a quick rename. Otherwise copy, then delete. */
    fun move(src: File, destDir: File, name: String = src.name): File {
        val sameVolume = volumeOf(src).absolutePath == volumeOf(destDir).absolutePath
        if (direct(src.parentFile!!) && direct(destDir)) {
            val target = File(destDir, name)
            if (src.renameTo(target)) return target
            src.copyTo(target)
            target.setLastModified(src.lastModified())
            check(src.delete()) { "Copied, but couldn't remove the original" }
            return target
        }
        if (sameVolume && name == src.name) {
            runCatching {
                val moved = DocumentsContract.moveDocument(cr, doc(src), doc(src.parentFile!!), doc(destDir))
                if (moved != null) return File(destDir, nameOf(moved))
            }.onFailure { if (it is NeedsSafAccess) throw it }
        }
        val (out, target) = create(destDir, name, if (src.isDirectory) DocumentsContract.Document.MIME_TYPE_DIR else "video/*")
        out.use { o -> src.inputStream().use { it.copyTo(o, 1 shl 20) } }
        if (direct(destDir)) target.setLastModified(src.lastModified())
        delete(src)
        return target
    }
}
