package tv.hobby.player.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Text
import tv.hobby.player.data.ANTONOV_LOGIN
import tv.hobby.player.data.BROWSER_UA
import tv.hobby.player.data.SessionCookies

@Composable
fun LoginScreen(
    accountName: String?,
    onFinished: () -> Unit,
    onPageSettled: () -> Unit,
) {
    val done = remember { FocusRequester() }
    var host by remember { mutableStateOf<FrameLayout?>(null) }
    LaunchedEffect(accountName) {
        if (accountName != null) done.requestFocus()
    }

    Column(modifier = Modifier.fillMaxSize().background(Ink)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (accountName == null) "Вход на сайте Антонов Медиа" else "Вход выполнен: $accountName",
                    color = Paper,
                    fontSize = 22.sp,
                )
                Text(
                    text = "Кнопки входа — Яндекс, VK, Google, Telegram и почта — на странице сайта. Пароль остаётся там.",
                    color = Muted,
                    fontSize = 14.sp,
                )
            }
            TvButton(
                text = if (accountName == null) "Готово" else "Продолжить",
                onClick = onFinished,
                modifier = Modifier.focusRequester(done),
            )
        }
        AndroidView(
            factory = { context ->
                FrameLayout(context).also { frame ->
                    host = frame
                    val page = loginWebView(context, onPageSettled) { popup ->
                        frame.addView(
                            popup,
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            ),
                        )
                        popup.requestFocus()
                    }
                    frame.addView(
                        page,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        ),
                    )
                    page.loadUrl(ANTONOV_LOGIN)
                    page.requestFocus()
                }
            },
            modifier = Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 8.dp),
            onRelease = { frame ->
                frame.releaseWebViews()
                host = null
            },
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun loginWebView(
    context: android.content.Context,
    onPageSettled: () -> Unit,
    onPopup: (WebView) -> Unit,
): WebView {
    return WebView(context).apply {
        setBackgroundColor(AndroidColor.parseColor("#101114"))
        prepareForLogin()
        webViewClient = LoginClient(onPageSettled)
        webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message,
            ): Boolean {
                val popup = WebView(view.context).apply {
                    setBackgroundColor(AndroidColor.parseColor("#101114"))
                    prepareForLogin()
                    webViewClient = LoginClient(onPageSettled) {
                        if (parent == null) return@LoginClient
                        (parent as? ViewGroup)?.removeView(this)
                        destroy()
                        view.reload()
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onCloseWindow(window: WebView) {
                            (window.parent as? ViewGroup)?.removeView(window)
                            window.destroy()
                            view.reload()
                        }
                    }
                }
                val transport = resultMsg.obj as WebView.WebViewTransport
                transport.webView = popup
                resultMsg.sendToTarget()
                onPopup(popup)
                return true
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.prepareForLogin() {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.javaScriptCanOpenWindowsAutomatically = true
    settings.setSupportMultipleWindows(true)
    settings.mediaPlaybackRequiresUserGesture = false
    settings.useWideViewPort = true
    settings.loadWithOverviewMode = true
    settings.userAgentString = BROWSER_UA
    settings.textZoom = 140
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    isFocusable = true
    isFocusableInTouchMode = true
    val cookies = CookieManager.getInstance()
    cookies.setAcceptCookie(true)
    cookies.setAcceptThirdPartyCookies(this, true)
}

private class LoginClient(
    private val onPageSettled: () -> Unit,
    private val onReturnToAntonov: (() -> Unit)? = null,
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        return handle(view, request.url)
    }

    @Deprecated("Kept for API 24 WebView builds that still call the string overload.")
    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
        return handle(view, Uri.parse(url))
    }

    override fun onPageFinished(view: WebView, url: String?) {
        SessionCookies.flush()
        val uri = url?.let(Uri::parse)
        if (uri != null && isAntonov(uri.host)) {
            onPageSettled()
            val closePopup = onReturnToAntonov ?: return
            view.postDelayed({
                if (view.parent != null) closePopup()
            }, 1_200)
        }
    }

    private fun handle(view: WebView, uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase()
        if (scheme == "http" || scheme == "https") return false
        if (scheme == "about" || scheme == "javascript" || scheme == "data") return true
        val fallback = fallbackHttps(uri)
        if (fallback != null) {
            view.loadUrl(fallback)
            return true
        }
        return try {
            view.context.startActivity(
                Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        } catch (_: Exception) {
            true
        }
    }
}

private fun fallbackHttps(uri: Uri): String? {
    val direct = uri.getQueryParameter("browser_fallback_url")
        ?: uri.getQueryParameter("link")
    if (direct != null && direct.startsWith("https://")) return direct
    if (uri.scheme == "intent") {
        val parsed = runCatching { Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME) }.getOrNull()
        val extra = parsed?.getStringExtra("browser_fallback_url")
        if (extra != null && extra.startsWith("https://")) return extra
        val data = parsed?.dataString
        if (data != null && data.startsWith("https://")) return data
    }
    return null
}

private fun isAntonov(host: String?): Boolean {
    if (host.isNullOrBlank()) return false
    val name = host.lowercase()
    return name == "antonov.media" || name.endsWith(".antonov.media")
}

private fun FrameLayout.releaseWebViews() {
    for (index in 0 until childCount) {
        (getChildAt(index) as? WebView)?.destroy()
    }
    removeAllViews()
}
