package dev.intelia.launcher.ui

import android.app.role.RoleManager
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import de.mm20.launcher2.ui.base.BaseActivity
import java.time.LocalDate

class InteliaHomeActivity : BaseActivity() {
    private val model: InteliaHomeViewModel by viewModels()
    private var homePulse by mutableIntStateOf(0)
    private val backup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.intelia.backup")) {
        it?.let(model::export)
    }
    private val restore = registerForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::prepareRestore) }
    private val source = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { it?.let(model::exportSource) }
    private val homeRole = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { model.refreshHomeRole() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.enableEdgeToEdge(window)
        applyValidationWindow(intent)
        setContent {
            InteliaHome(model, homePulse,
                onBackup = { backup.launch("intelia-${LocalDate.now()}.intelia") },
                onRestore = { restore.launch(arrayOf("*/*")) },
                onSource = { source.launch("intelia-launcher-source.zip") },
                onDefaultHome = {
                    if (Build.VERSION.SDK_INT >= 29) {
                        val manager = getSystemService(RoleManager::class.java)
                        if (manager != null && manager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                            homeRole.launch(manager.createRequestRoleIntent(RoleManager.ROLE_HOME))
                        }
                    } else {
                        startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                    }
                },
            )
        }
    }
    override fun onResume() { super.onResume(); model.refreshHomeRole() }
    override fun onPause() { setValidationKeepAwake(false); super.onPause() }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); applyValidationWindow(intent); model.refreshHomeRole(); homePulse++ }
    private fun applyValidationWindow(intent: Intent) {
        // Temporary, explicitly requested by the local debug tester; normal Home never keeps the display awake.
        setValidationKeepAwake(de.mm20.launcher2.ui.BuildConfig.DEBUG && intent.getBooleanExtra("intelia.validation.keepAwake", false))
    }
    private fun setValidationKeepAwake(enabled: Boolean) {
        val flag = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        if ((window.attributes.flags and flag != 0) != enabled) {
            if (enabled) window.addFlags(flag) else window.clearFlags(flag)
        }
    }
}
