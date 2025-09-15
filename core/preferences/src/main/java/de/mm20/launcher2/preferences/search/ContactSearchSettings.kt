package de.mm20.launcher2.preferences.search

import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class ContactSearchSettings internal constructor(private val dataStore: LauncherDataStore) {

    val enabledProviders: Flow<Set<String>>
        get() = dataStore.data.map { it.contactSearchProviders }.distinctUntilChanged()

    fun isProviderEnabled(provider: String) = dataStore.data.map { it.contactSearchProviders.contains(provider) }

    fun setProviderEnabled(provider: String, enabled: Boolean) {
        dataStore.update {
            if (enabled) {
                it.copy(contactSearchProviders = it.contactSearchProviders + provider)
            } else {
                it.copy(contactSearchProviders = it.contactSearchProviders - provider)
            }
        }
    }

    val enabledPlugins: Flow<Set<String>>
        get() = dataStore.data.map { it.contactSearchProviders - "local" }

    fun setPluginEnabled(authority: String, enabled: Boolean) {
        setProviderEnabled(authority, enabled)
    }

    val callOnTap: Flow<Boolean>
        get() = dataStore.data.map { it.contactSearchCallOnTap }.distinctUntilChanged()

    fun setCallOnTap(callOnTap: Boolean) {
        dataStore.update { it.copy(contactSearchCallOnTap = callOnTap) }
    }
}