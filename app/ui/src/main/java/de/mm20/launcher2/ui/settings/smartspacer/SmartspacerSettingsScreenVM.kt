package de.mm20.launcher2.ui.settings.smartspacer

import android.content.Intent
import android.os.Process
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.applications.AppRepository
import de.mm20.launcher2.preferences.ui.ClockWidgetSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.getValue

class SmartspacerSettingsScreenVM: ViewModel(), KoinComponent {
    private val appRepository: AppRepository by inject()
    private val clockWidgetSettings: ClockWidgetSettings by inject()

    val smartspacerApp = appRepository.findOne("com.kieronquinn.app.smartspacer", Process.myUserHandle())
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(), 1)

    val isSmartspacerAppInstalled = smartspacerApp
        .map { it != null }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(), 1)

    fun downloadSmartspacerApp(activity: AppCompatActivity) {
        activity.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                data = "https://github.com/KieronQuinn/Smartspacer".toUri()
            }
        )
    }

    fun launchSmartspacerApp(activity: AppCompatActivity) {
        viewModelScope.launch {
            smartspacerApp.first()?.launch(activity, null)
        }
    }

    val isSmartspacerEnabled = clockWidgetSettings.useSmartspacer
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(), 1)

    fun setSmartspacerEnabled(enabled: Boolean) {
        clockWidgetSettings.setUseSmartspacer(enabled)
    }

}