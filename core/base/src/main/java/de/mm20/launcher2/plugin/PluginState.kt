package de.mm20.launcher2.plugin

import android.app.PendingIntent
import android.os.Bundle


sealed class PluginState {
    data class Ready(
        /**
         * Status text, providing additional info what this plugin is currently configured to do.
         * For example "Search %user's files on %service"
         */
        val text: String? = null,
    ) : PluginState()
    data class SetupRequired(
        val setupActivity: PendingIntent,
        val message: String? = null,
    ) : PluginState()

    data object Error: PluginState()

    data object NoPermission: PluginState()

    companion object {
        fun fromBundle(bundle: Bundle): PluginState? {
            val type = bundle.getString("type") ?: return null
            return when(type) {
                "Ready" -> Ready(
                    text = bundle.getString("text"),
                )
                "SetupRequired" -> SetupRequired(
                    setupActivity = bundle.getParcelable("setupActivity") ?: return null,
                    message = bundle.getString("message"),
                )
                else -> null
            }
        }
    }
}