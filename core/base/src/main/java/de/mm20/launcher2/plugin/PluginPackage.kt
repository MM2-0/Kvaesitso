package de.mm20.launcher2.plugin

import android.content.Intent


data class PluginPackage(
    val packageName: String,
    val label: String,
    val description: String? = null,
    val author: String? = null,
    val settings: Intent? = null,
    val plugins: List<Plugin>,
    val isVerified: Boolean = false,
) {
    val enabled: Boolean = plugins.all { it.enabled }
}