package de.mm20.launcher2.plugin.contracts

abstract class SearchPluginContract {
    object Paths {
        const val Search = "search"
        const val Root = "root"
        const val Refresh = "refresh"
        @Deprecated("Use Paths.Query instead")
        const val QueryParam = Params.Query
        @Deprecated("Use Params.AllowNetwork instead")
        const val AllowNetworkParam = Params.AllowNetwork
        @Deprecated("Use Params.Lang instead")
        const val LangParam = Params.Lang
    }
    object Params {
        const val AllowNetwork = "network"
        const val Lang = "lang"
        const val UpdatedAt = "updatedAt"
        const val Query = "query"
    }
    object Extras {
        const val NotUpdated = "notUpdated"
    }
}