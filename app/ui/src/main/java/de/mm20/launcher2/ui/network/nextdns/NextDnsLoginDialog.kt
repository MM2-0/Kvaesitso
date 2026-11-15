package de.mm20.launcher2.ui.network.nextdns

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

private class NextDnsJsBridge(private val onFound: (String, String) -> Unit) {
    @JavascriptInterface
    fun onCredentials(profileId: String, apiKey: String) {
        if (profileId.isNotBlank() && apiKey.isNotBlank()) {
            onFound(profileId.trim(), apiKey.trim())
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NextDnsLoginDialog(
    onDismiss: () -> Unit,
    onSuccess: (String, String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { TelosCyberHeading {  Text(androidx.compose.ui.res.stringResource(I18nR.string.au7_trakt_sign_in) + " · NextDNS")  } },
        text = {
            AndroidView(
                onRelease = { it.stopLoading(); it.webViewClient = WebViewClient(); it.destroy() },
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        settings.userAgentString = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                        
                        addJavascriptInterface(NextDnsJsBridge { pid, pkey ->
                            post { onSuccess(pid, pkey) }
                        }, "TelosNextDnsBridge")

                        val checkJs = """
                            (function() {
                                try {
                                    var pid = "";
                                    var pkey = "";
                                    var m = window.location.pathname.match(/\/([a-zA-Z0-9]{6})\b/);
                                    if (m) pid = m[1];
                                    
                                    for (var i = 0; i < localStorage.length; i++) {
                                        var k = localStorage.key(i);
                                        var v = localStorage.getItem(k);
                                        if (!pkey && v && v.length > 20 && v.length < 100 && /^[a-fA-F0-9]+$/.test(v)) pkey = v;
                                        if (v && v.indexOf("apiKey") !== -1) {
                                            try {
                                                var obj = JSON.parse(v);
                                                if (obj.apiKey) pkey = obj.apiKey;
                                                if (obj.account && obj.account.apiKey) pkey = obj.account.apiKey;
                                                if (obj.config && obj.config.id) pid = obj.config.id;
                                                if (obj.id && obj.id.length === 6) pid = obj.id;
                                            } catch(e) {}
                                        }
                                    }
                                    if (pid && pkey) {
                                        window.TelosNextDnsBridge.onCredentials(pid, pkey);
                                    }
                                } catch(e) {}
                            })();
                        """.trimIndent()

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                return request.url.scheme != "https" || request.url.host !in setOf("my.nextdns.io", "apple.nextdns.io", "nextdns.io")
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                super.onPageFinished(view, url)
                                view.evaluateJavascript(checkJs, null)
                            }

                            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                                super.doUpdateVisitedHistory(view, url, isReload)
                                view.evaluateJavascript(checkJs, null)
                            }
                        }
                        loadUrl("https://my.nextdns.io/login")
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(androidx.compose.ui.res.stringResource(I18nR.string.hc_close)) }
        }
    )
}
