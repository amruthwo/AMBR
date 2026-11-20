package com.ambr.reloaded.data

import android.content.Context
import androidx.core.content.edit
import kotlin.random.Random

class Prefs(private val context: Context) {
    private val sp = context.getSharedPreferences("ambr_prefs", Context.MODE_PRIVATE)

    var lastFolder: String?
        get() = sp.getString("current_folder_id", null)
        set(v) = sp.edit { putString("current_folder_id", v) }

    var muted: Boolean
        get() = sp.getBoolean("muted", false)
        set(v) = sp.edit { putBoolean("muted", v) }

    var viewMode: ViewMode
        get() = runCatching { ViewMode.valueOf(sp.getString("view_mode", null)!!) }.getOrDefault(ViewMode.List)
        set(v) = sp.edit { putString("view_mode", v.name) }

    var favorites: Set<String>
        get() = sp.getStringSet("favorites", emptySet())!!.toSet()
        set(v) = sp.edit { putStringSet("favorites", v) }

    /** Playback speed while holding a finger on the video. */
    var holdSpeed: Float
        get() = sp.getFloat("hold_speed", 2f)
        set(v) = sp.edit { putFloat("hold_speed", v) }

    var seekStepSeconds: Int
        get() = sp.getInt("seek_step", 10)
        set(v) = sp.edit { putInt("seek_step", v) }

    /** Max video height for downloads (0 = best available). */
    var downloadHeight: Int
        get() = sp.getInt("download_height", 1080)
        set(v) = sp.edit { putInt("download_height", v) }

    var lastDownloadFolder: String?
        get() = sp.getString("last_download_folder", null)
        set(v) = sp.edit { putString("last_download_folder", v) }

    /** Seconds the app may sit in the background before hidden folders relock (0 = immediately). */
    var autoLockSeconds: Int
        get() = sp.getInt("auto_lock_seconds", 30)
        set(v) = sp.edit { putInt("auto_lock_seconds", v) }

    /** Default for every folder: keep AMBR videos out of other apps' galleries (.nomedia). */
    var hideFromGalleries: Boolean
        get() = sp.getBoolean("hide_from_galleries", true)
        set(v) = sp.edit { putBoolean("hide_from_galleries", v) }

    /** Folders the user explicitly set visible / hidden, overriding the default. */
    var galleryVisible: Set<String>
        get() = sp.getStringSet("gallery_visible", emptySet())!!.toSet()
        set(v) = sp.edit { putStringSet("gallery_visible", v) }
    var galleryHidden: Set<String>
        get() = sp.getStringSet("gallery_hidden", emptySet())!!.toSet()
        set(v) = sp.edit { putStringSet("gallery_hidden", v) }

    var cramHeight: Int
        get() = sp.getInt("cram_height", 720)
        set(v) = sp.edit { putInt("cram_height", v) }
    var cramChargingOnly: Boolean
        get() = sp.getBoolean("cram_charging", false)
        set(v) = sp.edit { putBoolean("cram_charging", v) }

    var meldName: String
        get() = sp.getString("meld_name", null) ?: friendlyDeviceName()
        set(v) = sp.edit { putString("meld_name", v) }

    /** Folders set to Big Brain (in order, full controls). Everything else is Brain Rot. */
    var bigBrainFolders: Set<String>
        get() = sp.getStringSet("big_brain_folders", emptySet())!!.toSet()
        set(v) = sp.edit { putStringSet("big_brain_folders", v) }

    var scaleBrainRot: VideoScale
        get() = runCatching { VideoScale.valueOf(sp.getString("scale_brain_rot", null)!!) }.getOrDefault(VideoScale.Auto)
        set(v) = sp.edit { putString("scale_brain_rot", v.name) }
    var scaleBigBrain: VideoScale
        get() = runCatching { VideoScale.valueOf(sp.getString("scale_big_brain", null)!!) }.getOrDefault(VideoScale.Fit)
        set(v) = sp.edit { putString("scale_big_brain", v.name) }

    var biometric: Boolean
        get() = sp.getBoolean("biometric", false)
        set(v) = sp.edit { putBoolean("biometric", v) }

    // PIN (salted PBKDF2 hash; the PIN itself is never stored).
    var pinHash: String?
        get() = sp.getString("pin_hash", null)
        set(v) = sp.edit { putString("pin_hash", v) }
    var pinSalt: String?
        get() = sp.getString("pin_salt", null)
        set(v) = sp.edit { putString("pin_salt", v) }

    fun index(folder: String) = sp.getInt("index_$folder", 0)
    fun setIndex(folder: String, index: Int) = sp.edit { putInt("index_$folder", index) }

    /** Each folder keeps a stable shuffle until every video has been watched. */
    fun seed(folder: String): Long {
        val key = "seed_$folder"
        if (!sp.contains(key)) sp.edit { putLong(key, Random.nextLong()) }
        return sp.getLong(key, 0L)
    }

    fun newSeed(folder: String) = sp.edit {
        putLong("seed_$folder", Random.nextLong())
        putInt("index_$folder", 0)
    }

    /** "Galaxy S24 Ultra" rather than "SM-S928U", when the phone knows its marketing name. */
    private fun friendlyDeviceName(): String =
        runCatching { android.provider.Settings.Global.getString(context.contentResolver, "device_name") }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: android.os.Build.MODEL ?: "AMBR"
}
