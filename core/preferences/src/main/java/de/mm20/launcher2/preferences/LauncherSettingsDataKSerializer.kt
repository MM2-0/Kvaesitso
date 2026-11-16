package de.mm20.launcher2.preferences

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject

/**
 * Keeps [LauncherSettingsData]'s on-disk JSON shape flat (every setting at the top level, exactly
 * as it was before the class was split into nested group properties - see the class kdoc), by
 * merging all groups' fields into one flat object on write, and handing that same flat object to
 * every group's own deserializer on read (each one just picks out the keys it recognizes, thanks
 * to `ignoreUnknownKeys`). This is what lets existing users' settings files keep working with zero
 * migration and zero data loss despite the Kotlin-side restructuring.
 */
internal object LauncherSettingsDataKSerializer : KSerializer<LauncherSettingsData> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("de.mm20.launcher2.preferences.LauncherSettingsData")

    override fun serialize(encoder: Encoder, value: LauncherSettingsData) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: error("LauncherSettingsData can only be serialized to JSON")
        val json = jsonEncoder.json
        val merged = buildJsonObject {
            put("schemaVersion", JsonPrimitive(value.schemaVersion))
            fun <T> mergeGroup(group: T, serializer: kotlinx.serialization.SerializationStrategy<T>) {
                val obj = json.encodeToJsonElement(serializer, group).jsonObject
                for ((key, element) in obj) put(key, element)
            }
            mergeGroup(value.ui, UiGroup.serializer())
            mergeGroup(value.wallpaper, WallpaperGroup.serializer())
            mergeGroup(value.media, MediaGroup.serializer())
            mergeGroup(value.clock, ClockGroup.serializer())
            mergeGroup(value.home, HomeGroup.serializer())
            mergeGroup(value.favorites, FavoritesGroup.serializer())
            mergeGroup(value.appSearch, AppSearchGroup.serializer())
            mergeGroup(value.fileSearch, FileSearchGroup.serializer())
            mergeGroup(value.contactSearch, ContactSearchGroup.serializer())
            mergeGroup(value.calendarSearch, CalendarSearchGroup.serializer())
            mergeGroup(value.shortcutSearch, ShortcutSearchGroup.serializer())
            mergeGroup(value.calculator, CalculatorGroup.serializer())
            mergeGroup(value.unitConverter, UnitConverterGroup.serializer())
            mergeGroup(value.wikipedia, WikipediaGroup.serializer())
            mergeGroup(value.website, WebsiteGroup.serializer())
            mergeGroup(value.badges, BadgesGroup.serializer())
            mergeGroup(value.grid, GridGroup.serializer())
            mergeGroup(value.searchBar, SearchBarGroup.serializer())
            mergeGroup(value.searchResults, SearchResultsGroup.serializer())
            mergeGroup(value.icons, IconsGroup.serializer())
            mergeGroup(value.misc, MiscGroup.serializer())
            mergeGroup(value.systemBars, SystemBarsGroup.serializer())
            mergeGroup(value.surfaces, SurfacesGroup.serializer())
            mergeGroup(value.widgets, WidgetsGroup.serializer())
            mergeGroup(value.gestures, GesturesGroup.serializer())
            mergeGroup(value.videoWallpaper, VideoWallpaperGroup.serializer())
            mergeGroup(value.performance, PerformanceGroup.serializer())
            mergeGroup(value.shutters, ShuttersGroup.serializer())
            mergeGroup(value.animations, AnimationsGroup.serializer())
            mergeGroup(value.stateTags, StateTagsGroup.serializer())
            mergeGroup(value.weather, WeatherGroup.serializer())
            mergeGroup(value.locationSearch, LocationSearchGroup.serializer())
            mergeGroup(value.searchFilterGroup, SearchFilterGroup.serializer())
            mergeGroup(value.locale, LocaleGroup.serializer())
            mergeGroup(value.feed, FeedGroup.serializer())
            mergeGroup(value.freeze, FreezeGroup.serializer())
            mergeGroup(value.protection, ProtectionGroup.serializer())
            mergeGroup(value.appLock, AppLockGroup.serializer())
            mergeGroup(value.floatingLauncher, FloatingLauncherGroup.serializer())
            mergeGroup(value.dynamicIsland, DynamicIslandGroup.serializer())
            mergeGroup(value.webAppsPanel, WebAppsPanelGroup.serializer())
            mergeGroup(value.webAppBrowsing, WebAppBrowsingGroup.serializer())
            mergeGroup(value.contextProfiles, ContextProfilesGroup.serializer())
            mergeGroup(value.desktopMode, DesktopModeGroup.serializer())
            mergeGroup(value.agents, AgentsGroup.serializer())
            mergeGroup(value.tweaks, TweaksGroup.serializer())
            mergeGroup(value.battery, BatteryGroup.serializer())
            mergeGroup(value.comms, CommsGroup.serializer())
        }
        jsonEncoder.encodeJsonElement(merged)
    }

    override fun deserialize(decoder: Decoder): LauncherSettingsData {
        val jsonDecoder = decoder as? JsonDecoder
            ?: error("LauncherSettingsData can only be deserialized from JSON")
        val json = jsonDecoder.json
        val obj: JsonObject = jsonDecoder.decodeJsonElement().jsonObject
        val schemaVersion = (obj["schemaVersion"] as? JsonPrimitive)?.int ?: 10
        return LauncherSettingsData(
            schemaVersion = schemaVersion,
            ui = json.decodeFromJsonElement(UiGroup.serializer(), obj),
            wallpaper = json.decodeFromJsonElement(WallpaperGroup.serializer(), obj),
            media = json.decodeFromJsonElement(MediaGroup.serializer(), obj),
            clock = json.decodeFromJsonElement(ClockGroup.serializer(), obj),
            home = json.decodeFromJsonElement(HomeGroup.serializer(), obj),
            favorites = json.decodeFromJsonElement(FavoritesGroup.serializer(), obj),
            appSearch = json.decodeFromJsonElement(AppSearchGroup.serializer(), obj),
            fileSearch = json.decodeFromJsonElement(FileSearchGroup.serializer(), obj),
            contactSearch = json.decodeFromJsonElement(ContactSearchGroup.serializer(), obj),
            calendarSearch = json.decodeFromJsonElement(CalendarSearchGroup.serializer(), obj),
            shortcutSearch = json.decodeFromJsonElement(ShortcutSearchGroup.serializer(), obj),
            calculator = json.decodeFromJsonElement(CalculatorGroup.serializer(), obj),
            unitConverter = json.decodeFromJsonElement(UnitConverterGroup.serializer(), obj),
            wikipedia = json.decodeFromJsonElement(WikipediaGroup.serializer(), obj),
            website = json.decodeFromJsonElement(WebsiteGroup.serializer(), obj),
            badges = json.decodeFromJsonElement(BadgesGroup.serializer(), obj),
            grid = json.decodeFromJsonElement(GridGroup.serializer(), obj),
            searchBar = json.decodeFromJsonElement(SearchBarGroup.serializer(), obj),
            searchResults = json.decodeFromJsonElement(SearchResultsGroup.serializer(), obj),
            icons = json.decodeFromJsonElement(IconsGroup.serializer(), obj),
            misc = json.decodeFromJsonElement(MiscGroup.serializer(), obj),
            systemBars = json.decodeFromJsonElement(SystemBarsGroup.serializer(), obj),
            surfaces = json.decodeFromJsonElement(SurfacesGroup.serializer(), obj),
            widgets = json.decodeFromJsonElement(WidgetsGroup.serializer(), obj),
            gestures = json.decodeFromJsonElement(GesturesGroup.serializer(), obj),
            videoWallpaper = json.decodeFromJsonElement(VideoWallpaperGroup.serializer(), obj),
            performance = json.decodeFromJsonElement(PerformanceGroup.serializer(), obj),
            shutters = json.decodeFromJsonElement(ShuttersGroup.serializer(), obj),
            animations = json.decodeFromJsonElement(AnimationsGroup.serializer(), obj),
            stateTags = json.decodeFromJsonElement(StateTagsGroup.serializer(), obj),
            weather = json.decodeFromJsonElement(WeatherGroup.serializer(), obj),
            locationSearch = json.decodeFromJsonElement(LocationSearchGroup.serializer(), obj),
            searchFilterGroup = json.decodeFromJsonElement(SearchFilterGroup.serializer(), obj),
            locale = json.decodeFromJsonElement(LocaleGroup.serializer(), obj),
            feed = json.decodeFromJsonElement(FeedGroup.serializer(), obj),
            freeze = json.decodeFromJsonElement(FreezeGroup.serializer(), obj),
            protection = json.decodeFromJsonElement(ProtectionGroup.serializer(), obj),
            appLock = json.decodeFromJsonElement(AppLockGroup.serializer(), obj),
            floatingLauncher = json.decodeFromJsonElement(FloatingLauncherGroup.serializer(), obj),
            dynamicIsland = json.decodeFromJsonElement(DynamicIslandGroup.serializer(), obj),
            webAppsPanel = json.decodeFromJsonElement(WebAppsPanelGroup.serializer(), obj),
            webAppBrowsing = json.decodeFromJsonElement(WebAppBrowsingGroup.serializer(), obj),
            contextProfiles = json.decodeFromJsonElement(ContextProfilesGroup.serializer(), obj),
            desktopMode = json.decodeFromJsonElement(DesktopModeGroup.serializer(), obj),
            agents = json.decodeFromJsonElement(AgentsGroup.serializer(), obj),
            tweaks = json.decodeFromJsonElement(TweaksGroup.serializer(), obj),
            battery = json.decodeFromJsonElement(BatteryGroup.serializer(), obj),
            comms = json.decodeFromJsonElement(CommsGroup.serializer(), obj),
        )
    }
}
