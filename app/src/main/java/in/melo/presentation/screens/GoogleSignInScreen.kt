package `in`.melo.presentation.screens

import `in`.melo.presentation.icons.MeloIcons
import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import `in`.melo.R
import `in`.melo.MeloModel
import `in`.melo.data.youtube.cookieValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Google's sign-in page, then music.youtube.com: the cookies it leaves open the account's playlists. */
private const val SIGN_IN_URL = "https://accounts.google.com/ServiceLogin?ltmpl=music&service=youtube&passive=true" +
    "&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26next%3Dhttps%253A%252F%252Fmusic.youtube.com%252F"
private const val MUSIC_ORIGIN = "https://music.youtube.com"
private const val MUSIC_HOST = "music.youtube.com"
private val YOUTUBE_HOMES = setOf("www.youtube.com", "m.youtube.com", "youtube.com")

/** The WebView's cookies for YouTube once Google has signed the account in, or null before. */
private fun signedInCookie(): String? = CookieManager.getInstance().getCookie(MUSIC_ORIGIN)
    ?.takeIf { cookieValue(it, "SAPISID") != null || cookieValue(it, "__Secure-3PAPISID") != null }
/** The web client's configuration holds the anonymous identity and, for a brand account, its page id. */
/** True once the web client has loaded its configuration: before, the identity it reports is empty. */
private const val CONFIG_READY = "(function(){try{return !!ytcfg.get('VISITOR_DATA')}catch(e){return false}})()"
/** Hides the website's own player: the page is only here to sign in, and Melo's mini player sits below it. */
private const val HIDE_WEB_PLAYER = "(function(){try{document.querySelectorAll('video,audio').forEach(function(m){m.pause()});" +
    "if(!document.getElementById('melo-hide')){var s=document.createElement('style');s.id='melo-hide';" +
    "s.textContent='ytmusic-player-bar,ytmusic-player-page,#player-bar-background{display:none!important}';document.head.appendChild(s)}}catch(e){}})()"

private suspend fun WebView.evaluate(script: String): String = suspendCancellableCoroutine { done ->
    evaluateJavascript(script) { done.resume(it.orEmpty()) }
}

private const val READ_CONFIG = "(function(){try{return {v:ytcfg.get('VISITOR_DATA')||'',d:ytcfg.get('DATASYNC_ID')||''}}catch(e){return {v:'',d:''}}})()"

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GoogleSignInScreen(model: MeloModel, navController: NavHostController) {
    val scope = rememberCoroutineScope()
    val failed = stringResource(R.string.google_signin_failed)
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var sentToMusic by remember { mutableStateOf(false) }
    var signedIn by remember { mutableStateOf(false) }

    fun finish(view: WebView, cookie: String) {
        if (checking) return
        checking = true
        error = null
        view.evaluateJavascript(READ_CONFIG) { result ->
            val config = runCatching { JSONObject(result) }.getOrNull()
            scope.launch {
                try {
                    model.connectGoogle(cookie, config?.optString("v"), config?.optString("d"))
                    navController.popBackStack()
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) {
                    error = failed
                    checking = false
                    `in`.melo.data.diagnostics.DiagnosticLog.record("youtube-account", "sign-in ${failure.javaClass.simpleName} \"${failure.message}\"")
                }
            }
        }
    }

    /** Google may end the sign-in on www.youtube.com or m.youtube.com: the session is there, music.youtube.com reads it. */
    fun continueToMusic(view: WebView, host: String) {
        if (sentToMusic) return
        sentToMusic = true
        `in`.melo.data.diagnostics.DiagnosticLog.record("youtube-account", "sign-in ended on $host, opening music.youtube.com")
        view.loadUrl(MUSIC_ORIGIN)
    }
    /** Leaving after signing in keeps the account instead of dropping it. */
    fun leave() {
        val view = webView
        val cookie = signedInCookie()
        if (view == null || cookie == null || checking) { navController.popBackStack(); return }
        val host = view.url?.let { android.net.Uri.parse(it).host }.orEmpty()
        when {
            host == MUSIC_HOST -> finish(view, cookie)
            // Already sent there once (a consent page in between, for instance): leaving now means leaving.
            sentToMusic -> navController.popBackStack()
            else -> continueToMusic(view, host.ifBlank { "unknown" })
        }
    }
    /*
     * music.youtube.com is a single-page app: its load can finish before the session cookies land, and no
     * later page load comes to try again. Checking on a short timer confirms the account as soon as it can.
     */
    LaunchedEffect(Unit) {
        while (true) {
            delay(700)
            val view = webView ?: continue
            val cookie = signedInCookie()
            signedIn = cookie != null
            val host = view.url?.let { android.net.Uri.parse(it).host }.orEmpty()
            if (host == MUSIC_HOST) view.evaluateJavascript(HIDE_WEB_PLAYER, null)
            if (cookie == null || checking || error != null) continue
            when {
                host == MUSIC_HOST -> if (view.evaluate(CONFIG_READY) == "true") finish(view, cookie)
                host in YOUTUBE_HOMES -> continueToMusic(view, host)
            }
        }
    }
    BackHandler {
        val view = webView
        when {
            checking -> Unit
            signedInCookie() != null -> leave()
            view != null && view.canGoBack() -> view.goBack()
            else -> navController.popBackStack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                // Same black as the page, also once content scrolls under it.
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                title = { Text(stringResource(R.string.google_signin_title)) },
                navigationIcon = {
                    IconButton(onClick = ::leave) {
                        Icon(MeloIcons.ArrowBack, stringResource(R.string.action_cancel))
                    }
                },
            )
        },
    ) { inner ->
        val hasSong by model.ui.derivedHasSong.collectAsStateWithLifecycle()
        Box(Modifier.padding(inner).padding(bottom = if (hasSong) `in`.melo.presentation.components.MiniPlayerHeight else 0.dp).fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = true
                        CookieManager.getInstance().setAcceptCookie(true)
                        // Shown once the first page has painted: until then a WebView is a blank
                        // white sheet, which flashes against the dark page behind it.
                        visibility = android.view.View.INVISIBLE
                        // An unstyled first frame then shows the page behind it, not white.
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        webViewClient = object : WebViewClient() {
                            override fun onPageCommitVisible(view: WebView, url: String?) {
                                if (view.visibility == android.view.View.INVISIBLE) view.visibility = android.view.View.VISIBLE
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                val host = url?.let { android.net.Uri.parse(it).host }.orEmpty()
                                // accounts.youtube.com sets the cookies and consent.youtube.com asks for the user's choice: never cut them short.
                                if (host != MUSIC_HOST && host !in YOUTUBE_HOMES) return
                                if (host == MUSIC_HOST) view.evaluateJavascript(HIDE_WEB_PLAYER, null)
                                val cookie = signedInCookie() ?: return
                                if (host == MUSIC_HOST) finish(view, cookie) else continueToMusic(view, host)
                            }
                        }
                        // A clean start: no other account's session is reused.
                        CookieManager.getInstance().removeAllCookies { loadUrl(SIGN_IN_URL) }
                        webView = this
                    }
                },
                onRelease = { it.stopLoading(); it.destroy() },
            )
            if (checking) Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    ContainedLoadingIndicator()
                    Text(stringResource(R.string.google_signin_checking), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
                }
            }
            // Signed in but still waiting on the page: one tap confirms the account now.
            if (signedIn && !checking && error == null) `in`.melo.presentation.components.MeloButton(
                text = stringResource(R.string.google_signin_use_account),
                onClick = ::leave,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            )
            error?.let { message ->
                Surface(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.large,
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
                        TextButton(onClick = { error = null; sentToMusic = false; webView?.loadUrl(SIGN_IN_URL) }) { Text(stringResource(R.string.google_signin_retry)) }
                    }
                }
            }
        }
    }
}
