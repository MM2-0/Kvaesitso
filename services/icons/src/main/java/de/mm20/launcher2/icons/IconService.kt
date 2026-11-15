package de.mm20.launcher2.icons

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.util.Log
import android.util.LruCache
import de.mm20.launcher2.data.customattrs.AdaptifiedLegacyIcon
import de.mm20.launcher2.data.customattrs.CustomAttributesRepository
import de.mm20.launcher2.data.customattrs.CustomIcon
import de.mm20.launcher2.data.customattrs.CustomIconPackIcon
import de.mm20.launcher2.data.customattrs.CustomTextIcon
import de.mm20.launcher2.data.customattrs.LegacyCustomIconPackIcon
import de.mm20.launcher2.data.customattrs.CustomThemedIcon
import de.mm20.launcher2.data.customattrs.DefaultPlaceholderIcon
import de.mm20.launcher2.data.customattrs.ForceThemedIcon
import de.mm20.launcher2.data.customattrs.UnmodifiedSystemDefaultIcon
import de.mm20.launcher2.icons.providers.CalendarIconProvider
import de.mm20.launcher2.icons.providers.CompatIconProvider
import de.mm20.launcher2.icons.providers.CustomIconPackIconProvider
import de.mm20.launcher2.icons.providers.CustomTextIconProvider
import de.mm20.launcher2.icons.providers.LegacyCustomIconPackIconProvider
import de.mm20.launcher2.icons.providers.CustomThemedIconProvider
import de.mm20.launcher2.icons.providers.DynamicClockIconProvider
import de.mm20.launcher2.icons.providers.FolderIconProvider
import de.mm20.launcher2.icons.providers.IconPackIconProvider
import de.mm20.launcher2.icons.providers.IconProvider
import de.mm20.launcher2.icons.providers.PlaceholderIconProvider
import de.mm20.launcher2.icons.providers.SystemIconProvider
import de.mm20.launcher2.icons.providers.ThemedPlaceholderIconProvider
import de.mm20.launcher2.icons.providers.getFirstIcon
import de.mm20.launcher2.icons.transformations.ForceThemedIconTransformation
import de.mm20.launcher2.icons.transformations.LauncherIconTransformation
import de.mm20.launcher2.icons.transformations.LegacyToAdaptiveTransformation
import de.mm20.launcher2.icons.transformations.transform
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.preferences.ui.IconSettings
import de.mm20.launcher2.preferences.ui.PerformanceSettings
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.Searchable
import de.mm20.launcher2.search.Tag
import de.mm20.launcher2.coroutines.TelosDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMap
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class IconService(
    val context: Context,
    private val iconPackManager: IconPackManager,
    private val settings: IconSettings,
    private val customAttributesRepository: CustomAttributesRepository,
    private val performanceSettings: PerformanceSettings,
    private val searchableRepository: de.mm20.launcher2.searchable.SavableSearchableRepository,
) {

    private val appReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            requestIconPackListUpdate()
        }
    }

    private val scope = CoroutineScope(Job() + TelosDispatchers.Icon)

    private val cache = LruCache<String, LauncherIcon>(200)

    private val iconProviders: MutableStateFlow<List<IconProvider>> = MutableStateFlow(listOf())

    /**
     * Signal that installed icon packs have been updated. Force a reload of all icons.
     */
    private val iconPacksUpdated = MutableSharedFlow<Unit>(1)

    private val transformations: MutableStateFlow<List<LauncherIconTransformation>> =
        MutableStateFlow(
            listOf()
        )

    init {
        scope.launch {
            performanceSettings.iconCacheSize.collectLatest { size ->
                cache.resize(size.coerceAtLeast(1))
            }
        }
        requestIconPackListUpdate()
        context.registerReceiver(appReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_MY_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        })

        iconPacksUpdated.tryEmit(Unit)

        scope.launch {
            settings.distinctUntilChanged().collectLatest { settings ->
                iconPacksUpdated.collectLatest {
                    val fallbackProvider = if (settings.themedIcons) {
                        ThemedPlaceholderIconProvider(context)
                    } else {
                        PlaceholderIconProvider(context)
                    }
                    val providers = mutableListOf<IconProvider>()

                    if (!settings.iconPack.isNullOrBlank()) {
                        val pack = iconPackManager.getIconPack(settings.iconPack!!)
                        if (pack != null) {
                            providers.add(
                                IconPackIconProvider(
                                    context,
                                    pack,
                                    iconPackManager,
                                    settings.themedIcons,
                                )
                            )
                        } else {
                            Log.w("MM20", "Icon pack ${settings.iconPack} not found")
                        }
                    }

                    // === TELOS_PENDING_REVIEW_START: ui_i18n_and_features_batch ===
                    for (fallbackPkg in settings.fallbackIconPacks) {
                        if (fallbackPkg.isNotBlank()) {
                            val pack = iconPackManager.getIconPack(fallbackPkg)
                            if (pack != null) {
                                providers.add(
                                    IconPackIconProvider(
                                        context,
                                        pack,
                                        iconPackManager,
                                        settings.themedIcons,
                                    )
                                )
                            }
                        }
                    }
                    // === TELOS_PENDING_REVIEW_END: ui_i18n_and_features_batch ===

                    providers.add(DynamicClockIconProvider(context, settings.themedIcons))
                    providers.add(FolderIconProvider(context, searchableRepository, this@IconService, settings.themedIcons))
                    providers.add(CalendarIconProvider(context, settings.themedIcons))
                    if (!isAtLeastApiLevel(33)) {
                        providers.add(CompatIconProvider(context, settings.themedIcons))
                    }
                    providers.add(SystemIconProvider(context, settings.themedIcons))
                    providers.add(fallbackProvider)
                    cache.evictAll()

                    val transformations = mutableListOf<LauncherIconTransformation>()

                    if (settings.adaptify) transformations.add(LegacyToAdaptiveTransformation())
                    if (settings.themedIcons && settings.forceThemed) transformations.add(
                        ForceThemedIconTransformation()
                    )

                    iconProviders.value = providers
                    this@IconService.transformations.value = transformations
                }
            }
        }
    }

    fun getCustomIcon(searchable: SavableSearchable) : Flow<CustomIcon?> {
        return customAttributesRepository.getCustomIcon(searchable)
    }


    fun getIcon(searchable: SavableSearchable, size: Int): Flow<LauncherIcon?> {
        if (searchable is Application && searchable.isPrivate) {
            return transformations.map {
                searchable.getPlaceholderIcon(context).transform(it)
            }
        }

        val customIcon = getCustomIcon(searchable)

        return customIcon.flatMapLatest {
            resolveCustomIcon(searchable, size, it)
        }
    }

    fun resolveCustomIcon(searchable: SavableSearchable, size: Int, customIcon: CustomIcon?): Flow<LauncherIcon?> {
        return combine(iconProviders, transformations) { providers, transformations ->
            // Includes searchable.hashCode(), not just searchable.key: every SavableSearchable
            // implementation in this codebase is a data class, so this reflects the searchable's
            // own mutable icon-affecting fields (e.g. WebAppShortcut.iconUri/faviconUrl/iconSource)
            // - keying on the key alone meant editing a web app shortcut's icon kept serving the
            // pre-edit LauncherIcon from cache indefinitely, since the key never changes.
            val cacheKey = searchable.key + searchable.hashCode() + customIcon.hashCode() + providers.hashCode() + transformations.hashCode()
            var icon: LauncherIcon? = cache.get(cacheKey)
            if (icon != null) {
                return@combine icon
            }

            val defaults = if (searchable.getSerializer() is de.mm20.launcher2.search.VirtualAppSerializer)
                listOf(SystemIconProvider(context, false)) + providers else providers
            val provs = if (customIcon != null) getProviders(customIcon) + defaults else defaults
            val transforms = getTransformations(customIcon) ?: transformations

            icon = provs.getFirstIcon(searchable, size)

            if (icon != null) {
                icon = icon.transform(transforms)

                cache.put(cacheKey, icon)
            }
            return@combine icon
        }
    }

    private fun getProviders(customIcon: CustomIcon?): List<IconProvider> {
        if (customIcon is UnmodifiedSystemDefaultIcon) {
            return listOf(
                SystemIconProvider(context, false)
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
        if (customIcon is LegacyCustomIconPackIcon) {
            return listOf(
                LegacyCustomIconPackIconProvider(
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
        if (customIcon is DefaultPlaceholderIcon) {
            return iconProviders.value.lastOrNull()?.let { listOf(it) } ?: emptyList()
        }
        if (customIcon is CustomTextIcon) {
            return listOf(CustomTextIconProvider(customIcon))
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
            iconPackManager.updateIconPacks().also {
                if (it)iconPacksUpdated.tryEmit(Unit)
            }
        }
    }

    fun reinstallAllIconPacks() {
        scope.launch {
            iconPackManager.updateIconPacks(forceReinstall = true)
            iconPacksUpdated.tryEmit(Unit)
        }
    }

    fun getInstalledIconPacks(): Flow<List<IconPack>> {
        return iconPackManager.getInstalledIconPacks()
    }

    suspend fun getCustomIconSuggestions(
        searchable: SavableSearchable,
        size: Int
    ): List<CustomIconWithPreview> {
        val suggestions = mutableListOf<CustomIconWithPreview>()

        val rawIcon = iconProviders.first().getFirstIcon(searchable, size) ?: return emptyList()

        val defaultTransformations = transformations.first()

        val transformationOptions = mutableListOf<CustomIcon>()

        if (searchable is Application) {
            transformationOptions.add(UnmodifiedSystemDefaultIcon)
        }

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

            // Android 7.1 round icons (48x48 circle with 1px padding)
            transformationOptions.add(
                AdaptifiedLegacyIcon(
                    fgScale = 48f / 44f,
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

        if (searchable is Application) {
            val iconPackIcons = iconPackManager.getAllIconPackIcons(
                searchable.componentName
            )

            providerOptions.addAll(
                iconPackIcons.map {
                    val ent = it.toDatabaseEntity()
                    CustomIconPackIcon(
                        iconPackPackage = it.iconPack,
                        type = ent.type,
                        drawable = ent.drawable,
                        extras = ent.extras,
                        allowThemed = it.themed,
                    )
                }
            )
            transformationOptions.add(
                ForceThemedIcon
            )
        }

        if (searchable !is Tag) {
            providerOptions.add(DefaultPlaceholderIcon)
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

                val transformed = icon.transform(defaultTransformations)
                CustomIconWithPreview(
                    preview = transformed,
                    customIcon = it,
                )

            }
        )

        return suggestions

    }

    suspend fun getUncustomizedDefaultIcon(
        searchable: SavableSearchable,
        size: Int
    ): CustomIconWithPreview? {
        val icon = iconProviders.first().getFirstIcon(searchable, size)
            ?.transform(transformations.first()) ?: return null
        // The picker displays through ShapedLauncherIcon's software composition pipeline.
        // Keep its software source instead of uploading to the GPU and immediately reading back.
        return CustomIconWithPreview(
            customIcon = null,
            preview = icon
        )
    }

    suspend fun searchCustomIcons(query: String, iconPack: IconPack?): List<CustomIconWithPreview> {
        val transformations = this.transformations.first()
        val iconPackIcons = iconPackManager.searchIconPackIcon(query, iconPack).flatMap {
            val themedIcon = if (it.themed) {
                iconPackManager.getIcon(it.iconPack, it, true)
                    ?.transform(transformations)
            } else null
            val unthemedIcon = iconPackManager.getIcon(it.iconPack, it, false)
                ?.transform(transformations)

            buildList {
                val ent = it.toDatabaseEntity()
                if (unthemedIcon != null) {
                    add(CustomIconWithPreview(
                        customIcon = CustomIconPackIcon(
                            iconPackPackage = it.iconPack,
                            type = ent.type,
                            drawable = ent.drawable,
                            extras = ent.extras,
                            allowThemed = false,
                        ),
                        preview = unthemedIcon
                    ))
                }
                if (themedIcon != null) {
                    add(CustomIconWithPreview(
                        customIcon = CustomIconPackIcon(
                            iconPackPackage = it.iconPack,
                            type = ent.type,
                            drawable = ent.drawable,
                            extras = ent.extras,
                            allowThemed = true,
                        ),
                        preview = themedIcon
                    ))
                }
            }
        }

        return iconPackIcons
    }

    fun setCustomIcon(searchable: SavableSearchable, icon: CustomIcon?) {
        customAttributesRepository.setCustomIcon(searchable, icon)
    }

}

data class CustomIconWithPreview(
    val preview: LauncherIcon,
    val customIcon: CustomIcon?,
)