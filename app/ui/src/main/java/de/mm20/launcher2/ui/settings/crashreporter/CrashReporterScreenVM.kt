package de.mm20.launcher2.ui.settings.crashreporter

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.crashreporter.BuildConfig
import de.mm20.launcher2.crashreporter.CrashReport
import de.mm20.launcher2.crashreporter.CrashReportType
import de.mm20.launcher2.crashreporter.CrashReporter
import kotlinx.coroutines.launch

class CrashReporterScreenVM: ViewModel() {
    fun setShowCrashes(showCrashes: Boolean) {
        this.showCrashes.value = showCrashes
        updateReports()
    }

    fun setShowExceptions(showExceptions: Boolean) {
        this.showExceptions.value = showExceptions
        updateReports()
    }

    private fun updateReports() {
        val exceptions = showExceptions.value == true
        val crashes = showCrashes.value == true
        reports.value = _reports?.filter {
            it.type == CrashReportType.Exception && exceptions ||
            it.type == CrashReportType.Crash && crashes
        }
    }

    val showExceptions = mutableStateOf(false)
    val showCrashes = mutableStateOf(true)

    val reports = mutableStateOf<List<CrashReport>?>(null)
    private var _reports: List<CrashReport>? = null

    init {
        viewModelScope.launch {
            _reports = CrashReporter.getCrashReports()
            reports.value = _reports
            setShowExceptions(BuildConfig.DEBUG)
        }
    }

}