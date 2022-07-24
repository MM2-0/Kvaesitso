package de.mm20.launcher2.icons

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.util.LruCache
import de.mm20.launcher2.customattrs.AdaptifiedLegacyIcon
import de.mm20.launcher2.customattrs.CustomAttributesRepository
import de.mm20.launcher2.customattrs.CustomIcon
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
    private val dataStore: LauncherDataStore,
    private val customAttributesRepository: CustomAttributesRepository,
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
                customAttributesRepository.getCustomIcon(searchable).collectLatest { customIcon ->

                    val transforms = getTransformations(customIcon) ?: transformations

                    var icon = cache.get(searchable.key + customIcon.hashCode())
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
                        icon = applyTransformations(icon, transforms)

                        cache.put(searchable.key + customIcon.hashCode(), icon)
                        send(icon)
                    }
                }
            }
        }
    }

    private fun getTransformations(customIcon: CustomIcon?): List<LauncherIconTransformation>? {
        customIcon ?: return null
        if (customIcon is AdaptifiedLegacyIcon) {
            return listOf(
                LegacyToAdaptiveTransformation(
                    foregroundScale = customIcon.fgScale,
                    backgroundColor = customIcon.bgColor
                )
            )
        }
        return null
    }


    fun requestIconPackListUpdate() {
        scope.launch {
            iconPackManager.updateIconPacks()
        }
    }

    suspend fun getInstalledIconPacks(): List<IconPack> {
        return iconPackManager.getInstalledIconPacks()
    }

    suspend fun getCustomIconSuggestions(
        searchable: Searchable,
        size: Int
    ): List<CustomIconSuggestion> {
        val suggestions = mutableListOf<CustomIconSuggestion>()

        var rawIcon = iconProviders.first().firstNotNullOfOrNull {
            it.getIcon(searchable, size)
        }

        if (rawIcon == null) {
            rawIcon = placeholderProvider?.getIcon(searchable, size)
        }

        if (rawIcon == null) {
            return emptyList()
        }

        val defaultTransformations = transformations.first()

        val defaultTransformedIcon = applyTransformations(rawIcon, defaultTransformations)

        suggestions.add(CustomIconSuggestion(
            defaultTransformedIcon,
            null,
        ))

        if (rawIcon is StaticLauncherIcon && rawIcon.backgroundLayer is TransparentLayer) {
            val adaptifyOptions = listOf(
                AdaptifiedLegacyIcon(
                    fgScale = 1.25f,
                    bgColor = 1
                ),
                AdaptifiedLegacyIcon(
                    fgScale = 0.7f,
                    bgColor = 0
                ),
                AdaptifiedLegacyIcon(
                    fgScale = 0.7f,
                    bgColor = Color.WHITE,
                )
            )
            suggestions.addAll(
                adaptifyOptions.mapNotNull {
                    val transformation = getTransformations(it)?.firstOrNull() ?: return@mapNotNull null
                    CustomIconSuggestion(
                        icon = transformation.transform(rawIcon),
                        data = it,
                    )

                }
            )
        }

        return suggestions

    }

    private suspend fun applyTransformations(icon: LauncherIcon, transformations: List<LauncherIconTransformation>): LauncherIcon {
        var icon = icon
        if (icon is StaticLauncherIcon) {
            for (transformation in transformations) {
                icon = transformation.transform(icon as StaticLauncherIcon)
            }
        }
        return icon
    }

}

data class CustomIconSuggestion(
    val icon: LauncherIcon,
    val data: CustomIcon?,
)