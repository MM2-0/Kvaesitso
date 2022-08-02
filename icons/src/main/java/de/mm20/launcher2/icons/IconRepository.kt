package de.mm20.launcher2.icons

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.util.LruCache
import de.mm20.launcher2.customattrs.*
import de.mm20.launcher2.icons.providers.*
import de.mm20.launcher2.icons.transformations.ForceThemedIconTransformation
import de.mm20.launcher2.icons.transformations.LauncherIconTransformation
import de.mm20.launcher2.icons.transformations.LegacyToAdaptiveTransformation
import de.mm20.launcher2.icons.transformations.transform
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.search.data.LauncherApp
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
                    providers.add(ThemedIconProvider(iconPackManager))
                }

                if (settings.iconPack.isNotBlank()) {
                    providers.add(
                        IconPackIconProvider(
                            context,
                            settings.iconPack,
                            iconPackManager
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
                if (settings.themedIcons && settings.forceThemed) transformations.add(ForceThemedIconTransformation())

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

                    val provs = getProviders(customIcon) + providers
                    val transforms = getTransformations(customIcon) ?: transformations

                    var icon = cache.get(searchable.key + customIcon.hashCode())
                    if (icon != null) {
                        send(icon)
                        return@collectLatest
                    }

                    val placeholder = placeholderProvider?.getIcon(searchable, size)
                    placeholder?.let { send(it) }

                    icon = provs.getFirstIcon(searchable, size)

                    if (icon != null) {
                        icon = icon.transform(transforms)

                        cache.put(searchable.key + customIcon.hashCode(), icon)
                        send(icon)
                    }
                }
            }
        }
    }

    private fun getProviders(customIcon: CustomIcon?): List<IconProvider> {
        if (customIcon is UnmodifiedSystemDefaultIcon) {
            return listOf(
                SystemIconProvider(context)
            )
        }
        if (customIcon is CustomIconPackIcon) {
            return listOf(
                CustomIconPackIconProvider(
                    customIcon,
                    iconPackManager
                )
            )
        }
        if (customIcon is CustomThemedIcon) {
            return listOf(
                CustomThemedIconProvider(
                    customIcon,
                    iconPackManager
                )
            )
        }
        return emptyList()
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
        if (customIcon is ForceThemedIcon) {
            return listOf(
                ForceThemedIconTransformation()
            )
        }
        if (customIcon is UnmodifiedSystemDefaultIcon) {
            return emptyList()
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
    ): List<CustomIconWithPreview> {
        val suggestions = mutableListOf<CustomIconWithPreview>()

        val rawIcon = iconProviders.first().getFirstIcon(searchable, size) ?: return emptyList()

        val defaultTransformations = transformations.first()

        val transformationOptions = mutableListOf<CustomIcon>(UnmodifiedSystemDefaultIcon)

        if (rawIcon is StaticLauncherIcon && rawIcon.backgroundLayer is TransparentLayer) {
            // Legacy icons that simply fill the entire canvas
            transformationOptions.add(
                AdaptifiedLegacyIcon(
                    fgScale = 1f,
                    bgColor = 1
                )
            )
            // 48x48 with 5px padding used to be the default icon size for icons generated by
            // the Android Studio asset generator. Upscale these icons to remove that padding.

            transformationOptions.add(
                AdaptifiedLegacyIcon(
                    fgScale = 48f / 38f,
                    bgColor = 1
                )
            )
            transformationOptions.add(
                AdaptifiedLegacyIcon(
                    fgScale = 0.7f,
                    bgColor = 0
                )
            )
            transformationOptions.add(
                AdaptifiedLegacyIcon(
                    fgScale = 0.7f,
                    bgColor = Color.WHITE,
                )
            )
        }

        val providerOptions = mutableListOf<CustomIcon>()

        if (searchable is LauncherApp) {
            val iconPackIcons = iconPackManager.getAllIconPackIcons(
                searchable.launcherActivityInfo.componentName
            )

            providerOptions.addAll(
                iconPackIcons.mapNotNull {
                    CustomIconPackIcon(
                        iconPackPackage = it.iconPack,
                        iconComponentName = it.componentName?.flattenToString()
                            ?: return@mapNotNull null
                    )
                }
            )

            val themedIcon = iconPackManager.getGreyscaleIcon(searchable.`package`)
            if (themedIcon != null && themedIcon.componentName?.packageName != null) {
                providerOptions.add(
                    CustomThemedIcon(
                        iconPackageName = themedIcon.componentName.packageName,
                    )
                )
            } else {
                transformationOptions.add(
                    ForceThemedIcon
                )
            }
        } else {
            transformationOptions.add(
                ForceThemedIcon
            )
        }

        suggestions.addAll(
            transformationOptions.map {
                val transformations = getTransformations(it) ?: defaultTransformations
                val providers = getProviders(it)

                val icon = providers.getFirstIcon(searchable, size) ?: rawIcon

                CustomIconWithPreview(
                    preview = icon.transform(transformations),
                    customIcon = it,
                )

            }
        )

        suggestions.addAll(
            providerOptions.mapNotNull {
                val providers = getProviders(it)

                val icon = providers.getFirstIcon(searchable, size) ?: return@mapNotNull null

                CustomIconWithPreview(
                    preview = icon.transform(defaultTransformations),
                    customIcon = it,
                )

            }
        )

        return suggestions

    }

    suspend fun getUncustomizedDefaultIcon(searchable: Searchable, size: Int): CustomIconWithPreview? {
        val icon = iconProviders.first().getFirstIcon(searchable, size)
            ?.transform(transformations.first()) ?: return null
        return CustomIconWithPreview(
            customIcon = null,
            preview = icon
        )
    }

    suspend fun searchCustomIcons(query: String): List<CustomIconWithPreview> {
        val transformations = this.transformations.first()
        val iconPackIcons =  iconPackManager.searchIconPackIcon(query).mapNotNull {
            val componentName = it.componentName ?: return@mapNotNull null

            CustomIconWithPreview(
                customIcon = CustomIconPackIcon(
                    iconPackPackage = it.iconPack,
                    iconComponentName = componentName.flattenToString(),
                ),
                preview = iconPackManager.getIcon(it.iconPack, componentName)?.transform(transformations) ?: return@mapNotNull null
            )
        }

        val themedIcons = iconPackManager.searchThemedIcons(query).mapNotNull {
            val componentName = it.componentName ?: return@mapNotNull null

            CustomIconWithPreview(
                customIcon = CustomThemedIcon(
                    iconPackageName = componentName.packageName,
                ),
                preview = iconPackManager.getThemedIcon(componentName.packageName)?.transform(transformations) ?: return@mapNotNull null
            )
        }

        return iconPackIcons + themedIcons
    }

    fun setCustomIcon(searchable: Searchable, icon: CustomIcon?) {
        customAttributesRepository.setCustomIcon(searchable, icon)
    }

}

data class CustomIconWithPreview(
    val preview: LauncherIcon,
    val customIcon: CustomIcon?,
)