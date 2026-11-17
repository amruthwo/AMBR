package com.ambr.reloaded.share

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.coroutines.resume

/** A link we already know how to fetch, plus nicer metadata than a bare stream URL would give. */
data class Resolved(
    val url: String,
    val title: String? = null,
    val thumbnail: String? = null,
    val uploader: String? = null,
)

/**
 * Reddit blocks its JSON API (which yt-dlp uses) for many IP ranges, even for signed-in users, but
 * its normal web page still loads. So we open the post in an invisible WebView, with the same
 * cookies and network as a real browser, and read the video links Reddit embeds for its own player.
 * Those live on v.redd.it / packaged-media.redd.it, which aren't blocked.
 */
object RedditPage {
    fun isRedditPost(url: String) =
        Regex("""reddit\.com/r/[^/]+/comments/|reddit\.com/comments/|v\.redd\.it/""").containsMatchIn(url)

    private const val JS = """
(function(){
  try {
    var out = {perms: []};
    var p = document.querySelector('shreddit-player-2, shreddit-player');
    if (p) {
      var pk = p.getAttribute('packaged-media-json');
      if (pk) {
        try {
          var j = JSON.parse(pk);
          ((j.playbackMp4s || {}).permutations || []).forEach(function(x){
            if (x.source && x.source.url) out.perms.push({url: x.source.url, h: (x.source.dimensions || {}).height || 0});
          });
        } catch (e) {}
      }
      out.src = p.getAttribute('src') || '';
      out.poster = p.getAttribute('poster') || '';
    }
    if (!out.src) {
      var m = document.documentElement.innerHTML.match(/https:\/\/v\.redd\.it\/[a-z0-9]+/i);
      if (m) out.src = m[0];
    }
    var og = document.querySelector('meta[property="og:title"]');
    out.title = og ? og.content : document.title;
    var oi = document.querySelector('meta[property="og:image"]');
    out.image = oi ? oi.content : out.poster;
    var sp = document.querySelector('shreddit-post');
    out.sub = sp ? (sp.getAttribute('subreddit-prefixed-name') || '') : '';
    return JSON.stringify(out);
  } catch (e) { return '{}'; }
})()
"""

    /** [maxHeight] 0 = best available. Returns null if the page had no video we could find. */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun extract(context: Context, postUrl: String, maxHeight: Int): Resolved? = withContext(Dispatchers.Main) {
        withTimeoutOrNull(25_000) {
            suspendCancellableCoroutine { cont ->
                val handler = Handler(Looper.getMainLooper())
                val wv = WebView(context.applicationContext)
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
                wv.settings.javaScriptEnabled = true
                wv.settings.domStorageEnabled = true
                LinkResolver.disguise(wv)
                wv.settings.mediaPlaybackRequiresUserGesture = true
                var tries = 0
                var finished = false

                fun done(r: Resolved?) {
                    if (finished) return
                    finished = true
                    handler.removeCallbacksAndMessages(null)
                    runCatching { wv.stopLoading(); wv.destroy() }
                    if (cont.isActive) cont.resume(r)
                }

                fun poll() {
                    wv.evaluateJavascript(JS) { raw ->
                        val parsed = runCatching { JSONObject(JSONTokener(raw).nextValue() as String) }.getOrNull()
                        val r = parsed?.let { toResolved(it, maxHeight) }
                        when {
                            r != null -> done(r)
                            ++tries >= 16 -> done(null)
                            else -> handler.postDelayed(::poll, 700)
                        }
                    }
                }

                wv.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        if (tries == 0) handler.postDelayed(::poll, 600)
                    }
                }
                cont.invokeOnCancellation { handler.post { done(null) } }
                wv.loadUrl(postUrl)
            }
        }
    }

    private fun toResolved(j: JSONObject, maxHeight: Int): Resolved? {
        val perms = j.optJSONArray("perms")
        val mp4s = (0 until (perms?.length() ?: 0)).map { perms!!.getJSONObject(it) }
            .map { it.getString("url") to it.optInt("h") }
        val pickMp4 = mp4s.filter { maxHeight == 0 || it.second <= maxHeight }.maxByOrNull { it.second }
            ?: mp4s.minByOrNull { it.second }
        val vId = Regex("""v\.redd\.it/([a-z0-9]+)""", RegexOption.IGNORE_CASE).find(j.optString("src"))?.groupValues?.get(1)
        val media = pickMp4?.first ?: vId?.let { "https://v.redd.it/$it/DASHPlaylist.mpd" } ?: return null
        return Resolved(
            url = media,
            title = j.optString("title").takeIf { it.isNotBlank() }?.removeSuffix(" : reddit")?.trim(),
            thumbnail = j.optString("image").takeIf { it.startsWith("http") },
            uploader = j.optString("sub").takeIf { it.isNotBlank() },
        )
    }
}
