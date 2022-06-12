package de.mm20.launcher2.ui.settings.hiddenitems

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.core.content.getSystemService
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.liveData
import de.mm20.launcher2.applications.AppRepository
import de.mm20.launcher2.favorites.FavoritesRepository
import de.mm20.launcher2.icons.IconRepository
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.search.data.Application
import de.mm20.launcher2.search.data.LauncherApp
import de.mm20.launcher2.search.data.Searchable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class HiddenItemsSettingsScreenVM : ViewModel(), KoinComponent {
    private val appRepository: AppRepository by inject()
    private val favoritesRepository: FavoritesRepository by inject()
    private val iconRepository: IconRepository by inject()

    val allApps = appRepository.getAllInstalledApps().map {
        withContext(Dispatchers.Default) { it.sorted() }
    }.asLiveData()
    val hiddenItems: LiveData<List<Searchable>> = liveData {
        val hidden = withContext(Dispatchers.Default) {
            favoritesRepository.getHiddenItems().first().filter { it !is Application }.sorted()
        }
        emit(hidden)
    }

    fun isHidden(searchable: Searchable): Flow<Boolean> {
        return favoritesRepository.isHidden(searchable)
    }

    fun setHidden(searchable: Searchable, hidden: Boolean) {
        if(hidden) {
            favoritesRepository.hideItem(searchable)
        } else {
            favoritesRepository.unhideItem(searchable)
        }
    }

    fun getIcon(searchable: Searchable, size: Int): Flow<LauncherIcon> {
        return iconRepository.getIcon(searchable, size)
    }

    fun launch(context: Context, searchable: Searchable) {
        val bundle = Bundle()
        if (isAtLeastApiLevel(31)) {
            bundle.putInt("android.activity.splashScreenStyle", 1)
        }
        searchable.launch(context, bundle)
    }

    fun openAppInfo(context: Context, app: Application) {
        val launcherApps = context.getSystemService<LauncherApps>()!!

        if (app is LauncherApp) {
            launcherApps.startAppDetailsActivity(
                ComponentName(app.`package`, app.activity),
                app.getUser(),
                null,
                null
            )
        } else {
            context.tryStartActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${app.`package`}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }
}