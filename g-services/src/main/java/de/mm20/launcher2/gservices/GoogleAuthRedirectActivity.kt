package de.mm20.launcher2.gservices

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class GoogleAuthRedirectActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val gServiceHelper = GoogleApiHelper.getInstance(this)
        val code = intent.data?.getQueryParameter("code")
        if (code == null) {
            gServiceHelper.cancelAuthFlow(this)
            finish()
        }
        else {
            lifecycleScope.launch {
                gServiceHelper.finishAuthFlow(this@GoogleAuthRedirectActivity, code)
                finish()
            }
        }
    }
}