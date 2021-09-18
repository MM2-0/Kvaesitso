package de.mm20.launcher2.nextcloud

import android.app.Activity
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.android.synthetic.main.activity_nextcloud_login.*
import kotlinx.coroutines.*

class LoginActivity : AppCompatActivity() {

    private val nextcloudClient = NextcloudApiHelper(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nextcloud_login)
        nextButton.setOnClickListener {
            serverUrlInputLayout.error = null
            lifecycleScope.launch {
                var url = serverUrlInput.text.toString()
                if (!(url.startsWith("http://") || url.startsWith("https://"))) {
                    url = "https://$url"
                }
                if (url.isBlank()) {
                    serverUrlInputLayout.error = getString(R.string.next_cloud_server_url_empty)
                    return@launch
                }
                if (nextcloudClient.checkNextcloudInstallation(url)) {
                    openLoginPage(url)
                } else {
                    serverUrlInputLayout.error = getString(R.string.next_cloud_server_invalid_url)
                }
            }
        }
    }

    private fun openLoginPage(url: String) {
        val webView = WebView(this)
        webView.settings.userAgentString = getString(R.string.app_name)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                if (request?.url?.scheme == "nc") {
                    val path = request.url?.path?.trim('/') ?: run {
                        setResult(0)
                        finish()
                        return false
                    }
                    val segments = path.split('&')
                    var username: String? = null
                    var token: String? = null
                    var server: String? = null
                    for (segment in segments) {
                        when {
                            segment.startsWith("server") -> server = segment.substringAfter(":")
                            segment.startsWith("user") -> username = segment.substringAfter(":")
                            segment.startsWith("password") -> token = segment.substringAfter(":")
                        }
                    }
                    if (username != null && server != null && token != null) {
                        nextcloudClient.setServer(server, username, token)
                    }
                    setResult(Activity.RESULT_OK)
                    finish()
                    return true
                }
                webView.loadUrl(request?.url?.toString() ?: "")
                return false
            }
        }
        webView.settings.javaScriptEnabled = true
        setContentView(webView)
        val headers = mapOf(
            "OCS-APIREQUEST" to "true"
        )
        webView.loadUrl("$url/index.php/login/flow", headers)

    }
}