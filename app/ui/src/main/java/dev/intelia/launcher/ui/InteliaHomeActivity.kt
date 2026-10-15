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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import de.mm20.launcher2.ui.base.BaseActivity
import java.time.LocalDate

class InteliaHomeActivity : BaseActivity() {
    private val model: InteliaHomeViewModel by viewModels()
    private var homePulse by mutableIntStateOf(0)
    private var requestedPanel by mutableStateOf<String?>(null)
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
        requestedPanel = intent.getStringExtra("intelia.open")?.takeIf { it in setOf("search", "settings") }
        setContent {
            InteliaHome(model, homePulse, requestedPanel,
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
    override fun onResume() {
        super.onResume(); model.refreshHomeRole()
        if (model.ui.value.island && !InteliaIslandService.bridgeConnected && androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)) {
            runCatching { android.service.notification.NotificationListenerService.requestRebind(android.content.ComponentName(this, InteliaIslandService::class.java)) }
        }
        sendBroadcast(Intent("dev.intelia.launcher.REFRESH_PULSE").setPackage(packageName))
    }
    override fun onPause() { setValidationKeepAwake(false); super.onPause() }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) sendBroadcast(Intent("dev.intelia.launcher.REFRESH_PULSE").setPackage(packageName).putExtra("intelia.home.visible", true))
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); applyValidationWindow(intent); model.refreshHomeRole()
        requestedPanel = intent.getStringExtra("intelia.open")?.takeIf { it in setOf("search", "settings") }; homePulse++
        if (hasWindowFocus()) sendBroadcast(Intent("dev.intelia.launcher.REFRESH_PULSE").setPackage(packageName).putExtra("intelia.home.visible", true))
    }
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
