package de.mm20.launcher2.plugin.contracts

abstract class SearchPluginContract {
    object Paths {
        const val Search = "search"
        const val Root = "root"
        const val QueryParam = "query"
        const val AllowNetworkParam = "network"
    }
}