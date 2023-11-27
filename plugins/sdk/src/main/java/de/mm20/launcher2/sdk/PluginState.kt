package de.mm20.launcher2.sdk

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import de.mm20.launcher2.sdk.base.BasePluginProvider


sealed class PluginState {
    /**
     * Plugin is ready to be used.
     */
    data class Ready(
        /**
         * Status text, providing additional info what this plugin is currently configured to do.
         * For example "Search %user's files on %service"
         */
        val text: String? = null,
    ) : PluginState()

    /**
     * Plugin requires some setup, e.g. user needs to login to a service.
     */
    data class SetupRequired(
        /**
         * Activity to start to setup the plugin.
         */
        val setupActivity: Intent,
        /**
         * Optional message to display to the user, describing what needs to be done to setup the plugin.
         */
        val message: String? = null,
    ) : PluginState()
}