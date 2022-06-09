package de.mm20.launcher2.backup

enum class BackupComponent(val value: String) {
    Settings("settings"),
    Favorites("favorites"),
    Widgets("widgets"),
    Websearches("websearches");

    companion object {
        fun fromValue(value: String): BackupComponent? {
            return values().firstOrNull { it.value == value }
        }
    }
}