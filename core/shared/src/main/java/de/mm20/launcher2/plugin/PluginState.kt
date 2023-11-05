package de.mm20.launcher2.plugin


sealed class PluginState {
    data object Ready : PluginState()
    data class SetupRequired(
        val setupActivity: String,
        val message: String? = null,
    ) : PluginState()
}