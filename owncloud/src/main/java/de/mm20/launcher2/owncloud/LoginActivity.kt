package de.mm20.launcher2.owncloud

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import de.mm20.launcher2.owncloud.databinding.ActivityOwncloudLoginBinding
import de.mm20.launcher2.owncloud.databinding.ActivityOwncloudLoginUsernamePasswordBinding
import kotlinx.coroutines.*

class LoginActivity : AppCompatActivity() {

    private val owncloudClient = OwncloudClient(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityOwncloudLoginBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)
        binding.nextButton.setOnClickListener {
            binding.serverUrlInputLayout.error = null
            lifecycleScope.launch {
                var url = binding.serverUrlInput.text.toString()
                if (!(url.startsWith("http://") || url.startsWith("https://"))) {
                    url = "https://$url"
                }
                if (url.isBlank()) {
                    binding.serverUrlInputLayout.error = getString(R.string.next_cloud_server_url_empty)
                    return@launch
                }
                if (owncloudClient.checkOwncloudInstallation(url)) {
                    openLoginPage(url)
                } else {
                    binding.serverUrlInputLayout.error = getString(R.string.owncloud_server_invalid_url)
                }
            }
        }
    }

    private fun openLoginPage(url: String) {
        val binding = ActivityOwncloudLoginUsernamePasswordBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)
        binding.loginButton.setOnClickListener {
            val username = binding.username.text.toString()
            val password = binding.password.text.toString()
            if (username.isEmpty()) {
                binding.usernameInputLayout.error = getString(R.string.owncloud_username_empty)
            }
            if (password.isEmpty()) {
                binding.passwordInputLayout.error = getString(R.string.owncloud_password_empty)
            }
            if(username.isEmpty() || password.isEmpty()) {
                return@setOnClickListener
            }
            lifecycleScope.launch {
                if (owncloudClient.tryLogin(url, username, password)) {
                    setResult(Activity.RESULT_OK)
                    finish()
                } else {
                    binding.passwordInputLayout.error = getString(R.string.owncloud_login_failed)
                }
            }
        }
    }
}