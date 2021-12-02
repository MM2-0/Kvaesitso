package de.mm20.launcher2.icons

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.LruCache
import de.mm20.launcher2.icons.providers.*
import de.mm20.launcher2.preferences.LauncherPreferences
import de.mm20.launcher2.search.data.Searchable
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class IconRepository(
    val context: Context,
    private val iconPackManager: IconPackManager,
    private val dynamicIconController: DynamicIconController,
) {

    private val appReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            requestIconPackListUpdate()
        }
    }

    private val scope = CoroutineScope(Job() + Dispatchers.Main)

    private val cache = LruCache<String, LauncherIcon>(200)

    private var iconProviders: List<IconProvider> = listOf()
    private lateinit var placeholderProvider: IconProvider

    init {
        requestIconPackListUpdate()
        context.registerReceiver(appReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_MY_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        })
        recreate()
    }


    fun getIcon(searchable: Searchable, size: Int): Flow<LauncherIcon> = flow {
        var icon = cache.get(searchable.key)
        if (icon != null) {
            emit(icon)
            return@flow
        }

        icon = placeholderProvider.getIcon(searchable, size)
        emit(icon)

        for (provider in iconProviders) {
            val ic = provider.getIcon(searchable, size)
            if (ic != null) {
                icon = ic
                if (icon is DynamicLauncherIcon) {
                    dynamicIconController.registerIcon(icon)
                }
                break
            }
        }
        if (icon != null) {
            cache.put(searchable.key, icon)
            emit(icon)
        }
    }

    /**
     * Returns the icon for the given Searchable if it was requested earlier and is still in cache.
     * Returns `null` otherwise.
     */
    fun getIconIfCached(searchable: Searchable): LauncherIcon? {
        return cache[searchable.key]
    }

    fun requestIconPackListUpdate() {
        scope.launch {
            iconPackManager.updateIconPacks()
        }
    }

    fun recreate() {
        placeholderProvider = PlaceholderIconProvider(context)
        val providers = mutableListOf<IconProvider>()

        if (iconPackManager.selectedIconPack.isNotBlank()) {
            providers.add(IconPackIconProvider(context, iconPackManager.selectedIconPack))
        }

        providers.add(GoogleClockIconProvider(context))
        providers.add(CalendarIconProvider(context))
        providers.add(SystemIconProvider(context))
        providers.add(placeholderProvider)
        cache.evictAll()

        iconProviders = providers
    }
}