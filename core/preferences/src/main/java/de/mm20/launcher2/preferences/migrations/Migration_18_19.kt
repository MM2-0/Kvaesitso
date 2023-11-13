package de.mm20.launcher2.preferences.migrations

import de.mm20.launcher2.preferences.Settings

class Migration_18_19  : VersionedMigration(18,19) {
    override suspend fun applyMigrations(builder: Settings.Builder): Settings.Builder {
        return builder
            .setLocationsSearch(
                Settings.LocationsSearchSettings.newBuilder()
                    .setEnabled(false)
                    .setSearchRadius(1500)
                    .setCustomOverpassUrl("https://overpass-api.de/")
                    .setShowMap(false)
                    .setThemeMap(true)
                    .setHideUncategorized(true)
                    .setShowPositionOnMap(false)
                    .setCustomTileServerUrl("https://tile.openstreetmap.org")
            )
    }
}