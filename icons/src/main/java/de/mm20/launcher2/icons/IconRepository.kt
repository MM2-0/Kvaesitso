package de.mm20.launcher2.icons

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.LruCache
import de.mm20.launcher2.icons.providers.*
import de.mm20.launcher2.icons.transformations.LauncherIconTransformation
import de.mm20.launcher2.icons.transformations.LegacyToAdaptiveTransformation
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.search.data.Searchable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class IconRepository(
    val context: Context,
    private val iconPackManager: IconPackManager,
    private val dataStore: LauncherDataStore
) {

    private val appReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            requestIconPackListUpdate()
        }
    }

    private val scope = CoroutineScope(Job() + Dispatchers.Default)

    private val cache = LruCache<String, LauncherIcon>(200)

    private var iconProviders: MutableStateFlow<List<IconProvider>> = MutableStateFlow(listOf())
    private var placeholderProvider: IconProvider? = null

    private var transformations: MutableStateFlow<List<LauncherIconTransformation>> =
        MutableStateFlow(
            listOf()
        )

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

        scope.launch {
            dataStore.data.map { it.icons }.distinctUntilChanged().collectLatest { settings ->
                val placeholderProvider = if (settings.themedIcons) {
                    ThemedPlaceholderIconProvider(context)
                } else {
                    PlaceholderIconProvider(context)
                }
                val providers = mutableListOf<IconProvider>()

                if (settings.themedIcons) {
                    providers.add(ThemedIconProvider(context))
                }

                if (settings.iconPack.isNotBlank()) {
                    providers.add(
                        IconPackIconProvider(
                            context,
                            settings.iconPack
                        )
                    )
                }
                providers.add(GoogleClockIconProvider(context))
                providers.add(CalendarIconProvider(context))
                providers.add(SystemIconProvider(context))
                providers.add(placeholderProvider)
                cache.evictAll()

                val transformations = mutableListOf<LauncherIconTransformation>()

                if (settings.adaptify) transformations.add(LegacyToAdaptiveTransformation())

                this@IconRepository.placeholderProvider = placeholderProvider
                iconProviders.value = providers
                this@IconRepository.transformations.value = transformations
            }
        }
    }


    fun getIcon(searchable: Searchable, size: Int): Flow<LauncherIcon> = channelFlow {
        iconProviders.collectLatest { providers ->
            transformations.collectLatest { transformations ->
                var icon = cache.get(searchable.key)
                if (icon != null) {
                    send(icon)
                    return@collectLatest
                }

                val placeholder = placeholderProvider?.getIcon(searchable, size)
                placeholder?.let { send(it) }

                for (provider in providers) {
                    val ic = provider.getIcon(searchable, size)
                    if (ic != null) {
                        icon = ic
                        break
                    }
                }
                if (icon != null) {
                    if (icon is StaticLauncherIcon) {
                        for (transformation in transformations) {
                            icon = transformation.transform(icon as StaticLauncherIcon)
                        }
                    }

                    cache.put(searchable.key, icon)
                    send(icon)
                }
            }
        }
    }


    fun requestIconPackListUpdate() {
        scope.launch {
            iconPackManager.updateIconPacks()
        }
    }

    suspend fun getInstalledIconPacks(): List<IconPack> {
        return iconPackManager.getInstalledIconPacks()
    }

}