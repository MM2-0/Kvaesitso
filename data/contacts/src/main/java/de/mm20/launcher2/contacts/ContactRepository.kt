package de.mm20.launcher2.contacts

import android.content.Context
import de.mm20.launcher2.contacts.providers.AndroidContactProvider
import de.mm20.launcher2.contacts.providers.PluginContactProvider
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.search.ContactSearchSettings
import de.mm20.launcher2.search.Contact
import de.mm20.launcher2.search.SearchableRepository
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

internal class ContactRepository(
    private val context: Context,
    private val permissionsManager: PermissionsManager,
    private val settings: ContactSearchSettings,
) : SearchableRepository<Contact> {

    override fun search(query: String, allowNetwork: Boolean): Flow<List<Contact>> {
        val hasPermission = permissionsManager.hasPermission(PermissionGroup.Contacts)

        if (query.length < 2) {
            return flow {
                emit(persistentListOf())
            }
        }

        return hasPermission.combineTransform(settings.enabledProviders) { perm, providerIds ->
            val providers = providerIds.mapNotNull {
                when (it) {
                    "local" -> if (perm) AndroidContactProvider(context) else null
                    else -> PluginContactProvider(context, it)
                }
            }

            supervisorScope {
                val result = MutableStateFlow(listOf<Contact>())

                for (provider in providers) {
                    launch {
                        val r = provider.search(
                            query,
                            allowNetwork = allowNetwork,
                        )
                        result.update { it + r }
                    }
                }
                emitAll(result)
            }
        }
    }
}