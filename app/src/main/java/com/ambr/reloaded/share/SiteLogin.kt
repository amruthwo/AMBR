package com.ambr.reloaded.share

import com.ambr.reloaded.util.str

import com.ambr.reloaded.R

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.ambr.reloaded.ui.theme.AmbrTheme
import java.io.File

/** Sites people commonly need to be signed in to for yt-dlp to see their videos. */
enum class Site(
    val label: String,
    val loginUrl: String,
    val cookieUrls: List<String>,
    /** The cookie that only exists while you're actually logged in. */
    val authCookie: String,
) {
    Reddit(str(R.string.site_login_reddit), "https://www.reddit.com/login/", listOf("https://www.reddit.com", "https://old.reddit.com"), "reddit_session"),
    Instagram(str(R.string.site_login_instagram), "https://www.instagram.com/accounts/login/", listOf("https://www.instagram.com"), "sessionid"),
    X(str(R.string.site_login_x_twitter), "https://x.com/i/flow/login", listOf("https://x.com", "https://twitter.com"), "auth_token"),
    ;

    companion object {
        fun forUrl(url: String): Site? {
            val host = runCatching { android.net.Uri.parse(url).host.orEmpty() }.getOrDefault("").lowercase()
            return when {
                host.endsWith("reddit.com") || host.endsWith("redd.it") -> Reddit
                host.endsWith("instagram.com") -> Instagram
                host.endsWith("x.com") || host.endsWith("twitter.com") -> X
                else -> null
            }
        }
    }
}

/**
 * Keeps a Netscape-format cookies.txt (what yt-dlp's --cookies reads) in AMBR's private storage,
 * built from the in-app browser's cookies after you sign in. Only sites you're actually signed in
 * to are written. Never leaves the phone.
 */
object SiteCookies {
    fun file(context: Context) = File(context.filesDir, "cookies.txt")

    fun hasAny(context: Context) = file(context).let { it.exists() && it.readLines().any { l -> l.isNotBlank() && !l.startsWith("#") } }

    private fun cookiePairs(url: String): List<Pair<String, String>> =
        CookieManager.getInstance().getCookie(url)?.split(";")?.map { it.trim() }?.filter { '=' in it }
            ?.map { it.substringBefore('=') to it.substringAfter('=') }.orEmpty()

    /** Live check against the browser's cookie store: is the site's login cookie there? */
    fun isSignedIn(@Suppress("UNUSED_PARAMETER") context: Context, site: Site): Boolean =
        site.cookieUrls.any { url -> cookiePairs(url).any { (n, v) -> n == site.authCookie && v.isNotBlank() } }

    private fun cookieDomain(url: String) = "." + android.net.Uri.parse(url).host.orEmpty().removePrefix("www.").removePrefix("old.")

    /** Re-exports cookies for every signed-in site from the WebView cookie store. */
    fun export(context: Context) {
        CookieManager.getInstance().flush()
        val farFuture = System.currentTimeMillis() / 1000 + 365L * 24 * 3600
        val lines = mutableListOf(str(R.string.site_login_netscape_http_cookie_file), str(R.string.site_login_written_by_ambr_from_your))
        Site.entries.filter { isSignedIn(context, it) }.forEach { site ->
            val seen = mutableSetOf<String>()
            site.cookieUrls.forEach { url ->
                val domain = cookieDomain(url)
                cookiePairs(url).forEach { (name, value) ->
                    if (seen.add("$domain|$name")) {
                        lines += listOf(domain, str(R.string.site_login_true), "/", str(R.string.site_login_true), farFuture.toString(), name, value).joinToString("\t")
                    }
                }
            }
        }
        file(context).writeText(lines.joinToString("\n") + "\n")
    }

    /**
     * Logs out by expiring every cookie for the site. Login cookies are "Secure", and the browser
     * refuses to overwrite a Secure cookie with a non-Secure one, so the expiry must be Secure too,
     * and match the cookie's domain (host-only or .domain) to replace it.
     */
    fun signOut(context: Context, site: Site) {
        val cm = CookieManager.getInstance()
        val gone = str(R.string.site_login_expires_thu_01_jan_1970)
        site.cookieUrls.forEach { url ->
            val host = android.net.Uri.parse(url).host.orEmpty()
            val bare = host.removePrefix("www.").removePrefix("old.")
            cookiePairs(url).forEach { (name, _) ->
                cm.setCookie(url, "$name=; $gone")
                cm.setCookie(url, str(R.string.site_login_x_x_domain_x, name, gone, host))
                cm.setCookie(url, str(R.string.site_login_x_x_domain_x_2, name, gone, bare))
            }
        }
        cm.flush()
        export(context)
    }

    /** Does this yt-dlp error mean "you need to log in"? */
    fun isAuthError(message: String) = listOf(
        str(R.string.site_login_authentication_is_required), "--cookies", str(R.string.site_login_login_required), str(R.string.site_login_log_in), str(R.string.site_login_sign_in_to_confirm), "private",
        str(R.string.site_login_requested_content_is_not_available), str(R.string.site_login_rate_limit_reached),
    ).any { message.contains(it, ignoreCase = true) }
}

/** A plain in-app browser for signing in. Tap Done when you're logged in. */
class SiteLoginActivity : ComponentActivity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val site = runCatching { Site.valueOf(intent.getStringExtra(EXTRA_SITE)!!) }.getOrDefault(Site.Reddit)
        CookieManager.getInstance().setAcceptCookie(true)

        setContent {
            AmbrTheme {
                var progress by remember { mutableFloatStateOf(0f) }
                var webView by remember { mutableStateOf<WebView?>(null) }
                fun done() {
                    SiteCookies.export(this)
                    setResult(RESULT_OK)
                    finish()
                }
                BackHandler {
                    if (webView?.canGoBack() == true) webView?.goBack() else done()
                }
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
                        TopAppBar(
                            title = { Text(str(R.string.share_sign_in_to_x, site.label)) },
                            navigationIcon = { IconButton(onClick = { finish() }) { Icon(Icons.Rounded.Close, str(R.string.pin_pad_cancel)) } },
                            actions = { Button(onClick = ::done) { Text(str(R.string.cram_done)) } },
                        )
                        if (progress < 1f) LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    // Without this the WebView "wraps content", and full-height pages
                                    // (X, Instagram) collapse to a sliver or nothing.
                                    layoutParams = android.view.ViewGroup.LayoutParams(
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    )
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    LinkResolver.disguise(this)
                                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                    webViewClient = WebViewClient()
                                    webChromeClient = object : WebChromeClient() {
                                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                            progress = newProgress / 100f
                                        }
                                    }
                                    loadUrl(site.loginUrl)
                                    webView = this
                                }
                            },
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_SITE = "site"
        fun intent(context: Context, site: Site) =
            Intent(context, SiteLoginActivity::class.java).putExtra(EXTRA_SITE, site.name)
    }
}
