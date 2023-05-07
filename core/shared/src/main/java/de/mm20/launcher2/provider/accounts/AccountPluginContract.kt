package de.mm20.launcher2.provider.accounts

object AccountPluginContract {
    object Accounts {
        const val Id = "id"
        const val DisplayName = "display_name"
        const val Type = "type"
    }
    object AccountTypes {
        const val Id = "id"
        const val DisplayName = "display_name"
        const val SupportsMultipleAccounts = "supports_multiple_accounts"
    }
}