package de.mm20.launcher2.contacts

import android.content.Context
import android.provider.ContactsContract
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.search.SearchableRepository
import de.mm20.launcher2.search.data.Contact
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

interface ContactRepository: SearchableRepository<Contact>

internal class ContactRepositoryImpl(
    private val context: Context,
) : ContactRepository, KoinComponent {

    private val permissionsManager: PermissionsManager by inject()
    private val dataStore: LauncherDataStore by inject()

    override fun search(query: String): Flow<ImmutableList<Contact>> {
        val searchContacts = dataStore.data.map { it.contactsSearch.enabled }
        val hasPermission = permissionsManager.hasPermission(PermissionGroup.Contacts)

        if (query.length < 3) {
            return flow {
                emit(persistentListOf())
            }
        }

        return combine(searchContacts, hasPermission) { search, permission ->
            search && permission
        }.map {
            if (it) {
                queryContacts(query)
            } else {
                persistentListOf()
            }
        }
    }

    private suspend fun queryContacts(query: String): ImmutableList<Contact> {
        val results = withContext(Dispatchers.IO) {
            val proj = arrayOf(
                ContactsContract.RawContacts.CONTACT_ID,
                ContactsContract.RawContacts._ID
            )
            val sel = "${ContactsContract.RawContacts.DISPLAY_NAME_PRIMARY} LIKE ? OR ${ContactsContract.RawContacts.DISPLAY_NAME_ALTERNATIVE} LIKE ? OR ${ContactsContract.RawContacts.PHONETIC_NAME} LIKE ?"
            val selArgs = arrayOf("%$query%", "%$query%", "%$query%")
            val cursor = context.contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI, proj, sel, selArgs, null
            ) ?: return@withContext mutableListOf()
            //Maps raw contact ids to contact ids
            val contactMap = mutableMapOf<Long, MutableSet<Long>>()
            while (cursor.moveToNext()) {
                contactMap.getOrPut(cursor.getLong(0)) { mutableSetOf() }.add(cursor.getLong(1))
            }
            cursor.close()
            val results = mutableListOf<Contact>()
            for ((id, rawIds) in contactMap) {
                Contact.contactById(context, id, rawIds)?.let { results.add(it) }
            }
            results
        }
        return results.toImmutableList()
    }
}