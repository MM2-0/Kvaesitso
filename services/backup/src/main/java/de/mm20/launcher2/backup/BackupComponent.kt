package de.mm20.launcher2.backup

enum class BackupComponent(val value: String) {
    Settings("settings"),
    Favorites("favorites"),
    Widgets("widgets2"),
    Customizations("customizations"),
    SearchActions("searchactions"),
    Themes("themes");

    companion object {
        fun fromValue(value: String): BackupComponent? {
            return entries.firstOrNull { it.value == value }
        }
    }
}