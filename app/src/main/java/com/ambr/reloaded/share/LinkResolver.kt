package com.ambr.reloaded.share

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Turns app share links into the canonical post URL yt-dlp understands, e.g.
 * reddit.com/r/foo/s/AbC123 (Reddit app "Share") → reddit.com/r/foo/comments/1xyz/title/.
 * Uses the same browser identity + cookies as the in-app sign-in so sites don't flag it as a bot.
 */
object LinkResolver {
    /**
     * The phone's real Chrome identity, minus the WebView markers ("; wv", "Version/4.0") that make
     * Instagram and others refuse to show their login page. Used by the sign-in browser, the Reddit
     * page reader and yt-dlp alike, so every request looks like the same browser session.
     */
    fun browserUserAgent(context: Context): String =
        runCatching {
            WebSettings.getDefaultUserAgent(context).replace("; wv", "").replace(Regex("""\s*Version/\d+(\.\d+)*"""), "")
        }.getOrDefault(
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36",
        )

    /**
     * Makes a WebView present itself as plain Chrome: user agent without WebView markers, and
     * client hints ("Sec-CH-UA") listing Chrome rather than "Android WebView". Meta's sites refuse to
     * show their login page to embedded browsers otherwise.
     */
    fun disguise(webView: android.webkit.WebView) {
        val ua = browserUserAgent(webView.context)
        webView.settings.userAgentString = ua
        if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.USER_AGENT_METADATA)) {
            val major = Regex("""Chrome/(\d+)""").find(ua)?.groupValues?.get(1) ?: "130"
            val full = Regex("""Chrome/([\d.]+)""").find(ua)?.groupValues?.get(1) ?: "$major.0.0.0"
            val brands = listOf(
                androidx.webkit.UserAgentMetadata.BrandVersion.Builder().setBrand("Chromium").setMajorVersion(major).setFullVersion(full).build(),
                androidx.webkit.UserAgentMetadata.BrandVersion.Builder().setBrand("Google Chrome").setMajorVersion(major).setFullVersion(full).build(),
                androidx.webkit.UserAgentMetadata.BrandVersion.Builder().setBrand("Not?A_Brand").setMajorVersion("99").setFullVersion("99.0.0.0").build(),
            )
            val old = androidx.webkit.WebSettingsCompat.getUserAgentMetadata(webView.settings)
            androidx.webkit.WebSettingsCompat.setUserAgentMetadata(
                webView.settings,
                androidx.webkit.UserAgentMetadata.Builder(old).setBrandVersionList(brands).setFullVersion(full).build(),
            )
        }
    }

    suspend fun resolve(context: Context, url: String): String = withContext(Dispatchers.IO) {
        val needsResolving = Regex("""reddit\.com/r/[^/]+/s/|redd\.it/|reddit\.app\.link|vm\.tiktok\.com|vt\.tiktok\.com|t\.co/""")
            .containsMatchIn(url)
        if (!needsResolving) return@withContext clean(url)
        val ua = browserUserAgent(context)
        var current = url
        repeat(6) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("User-Agent", ua)
                setRequestProperty("Accept", "text/html,application/xhtml+xml")
                CookieManager.getInstance().getCookie(current)?.let { setRequestProperty("Cookie", it) }
            }
            try {
                val code = conn.responseCode
                val location = conn.getHeaderField("Location")
                if (code in 300..399 && location != null) {
                    current = URL(URL(current), location).toString()
                } else {
                    return@withContext clean(current)
                }
            } catch (_: Exception) {
                return@withContext clean(current)
            } finally {
                conn.disconnect()
            }
        }
        clean(current)
    }

    /**
     * Everything needed before handing a link to yt-dlp: follow share links, and for Reddit posts
     * read the video straight off the web page (its API is often blocked).
     */
    suspend fun prepare(context: Context, url: String, maxHeight: Int): Resolved {
        val resolved = resolve(context, url)
        if (RedditPage.isRedditPost(resolved)) RedditPage.extract(context, resolved, maxHeight)?.let { return it }
        return Resolved(resolved)
    }

    /** Drops tracking junk (?utm_…, share ids) that sometimes confuses extractors. */
    private fun clean(url: String): String {
        val noQuery = url.substringBefore('?')
        return if ("reddit.com" in url || "redd.it" in url) noQuery else url
    }
}
