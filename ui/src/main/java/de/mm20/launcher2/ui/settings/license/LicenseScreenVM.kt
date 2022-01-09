package de.mm20.launcher2.ui.settings.license

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.liveData
import de.mm20.launcher2.licenses.OpenSourceLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LicenseScreenVM(private val context: Application) : AndroidViewModel(context) {
    fun getLicenseText(library: OpenSourceLibrary) = liveData<String?> {
        val text = withContext(Dispatchers.IO) {
            context.resources.openRawResource(library.licenseText).reader()
                .readText()
        }
        emit(text)
    }
}