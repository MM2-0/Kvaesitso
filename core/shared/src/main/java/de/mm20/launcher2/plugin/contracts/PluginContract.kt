package de.mm20.launcher2.plugin.contracts

object PluginContract {

    const val Permission = "de.mm20.launcher2.permission.USE_PLUGINS"
    object Methods {
        const val GetType = "getType"
        const val GetState = "getState"
        const val GetConfig = "getConfig"
    }
}