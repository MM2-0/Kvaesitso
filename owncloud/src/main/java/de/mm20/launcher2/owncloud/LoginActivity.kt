package de.mm20.launcher2.owncloud

import android.app.Activity
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.android.synthetic.main.activity_owncloud_login.*
import kotlinx.android.synthetic.main.activity_owncloud_login_username_password.*
import kotlinx.coroutines.*

class LoginActivity : AppCompatActivity() {

    private val owncloudClient = OwncloudClient(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_owncloud_login)
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
                if (owncloudClient.checkOwncloudInstallation(url)) {
                    openLoginPage(url)
                } else {
                    serverUrlInputLayout.error = getString(R.string.owncloud_server_invalid_url)
                }
            }
        }
    }

    private fun openLoginPage(url: String) {
        setContentView(R.layout.activity_owncloud_login_username_password)
        loginButton.setOnClickListener {
            val username = username.text.toString()
            val password = password.text.toString()
            if (username.isEmpty()) {
                usernameInputLayout.error = getString(R.string.owncloud_username_empty)
            }
            if (password.isEmpty()) {
                passwordInputLayout.error = getString(R.string.owncloud_password_empty)
            }
            if(username.isEmpty() || password.isEmpty()) {
                return@setOnClickListener
            }
            lifecycleScope.launch {
                if (owncloudClient.tryLogin(url, username, password)) {
                    setResult(Activity.RESULT_OK)
                    finish()
                } else {
                    passwordInputLayout.error = getString(R.string.owncloud_login_failed)
                }
            }
        }
    }
}