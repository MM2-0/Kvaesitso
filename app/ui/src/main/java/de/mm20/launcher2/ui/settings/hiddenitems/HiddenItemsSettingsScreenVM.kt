package de.mm20.launcher2.ui.settings.hiddenitems

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Bundle
import androidx.core.content.getSystemService
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.liveData
import de.mm20.launcher2.applications.AppRepository
import de.mm20.launcher2.searchable.SearchableRepository
import de.mm20.launcher2.icons.IconRepository
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.data.LauncherApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class HiddenItemsSettingsScreenVM : ViewModel(), KoinComponent {
    private val appRepository: AppRepository by inject()
    private val searchableRepository: SearchableRepository by inject()
    private val iconRepository: IconRepository by inject()

    val allApps = appRepository.getAllInstalledApps().map {
        withContext(Dispatchers.Default) { it.sorted() }
    }.asLiveData()
    val hiddenItems: LiveData<List<SavableSearchable>> = liveData {
        val hidden = searchableRepository.get(hidden = true).first().filter { it !is LauncherApp }.sorted()
        emit(hidden)
    }

    fun isHidden(searchable: SavableSearchable): Flow<Boolean> {
        return searchableRepository.isHidden(searchable)
    }

    fun setHidden(searchable: SavableSearchable, hidden: Boolean) {
        if(hidden) {
            searchableRepository.upsert(searchable, hidden = true, pinned = false)
        } else {
            searchableRepository.update(searchable, hidden = false)
        }
    }

    fun getIcon(searchable: SavableSearchable, size: Int): Flow<LauncherIcon> {
        return iconRepository.getIcon(searchable, size)
    }

    fun launch(context: Context, searchable: SavableSearchable) {
        val bundle = Bundle()
        if (isAtLeastApiLevel(31)) {
            bundle.putInt("android.activity.splashScreenStyle", 1)
        }
        searchable.launch(context, bundle)
    }

    fun openAppInfo(context: Context, app: LauncherApp) {
        val launcherApps = context.getSystemService<LauncherApps>()!!

        launcherApps.startAppDetailsActivity(
            ComponentName(app.`package`, app.activity),
            app.getUser(),
            null,
            null
        )
    }
}