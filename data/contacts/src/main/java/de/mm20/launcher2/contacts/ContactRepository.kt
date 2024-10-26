package de.mm20.launcher2.contacts

import android.content.ContentUris
import android.content.Context
import android.provider.ContactsContract
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.search.ContactSearchSettings
import de.mm20.launcher2.search.Contact
import de.mm20.launcher2.search.ContactApp
import de.mm20.launcher2.search.ContactInfoType
import de.mm20.launcher2.search.EmailAddress
import de.mm20.launcher2.search.PhoneNumber
import de.mm20.launcher2.search.PostalAddress
import de.mm20.launcher2.search.SearchableRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal class ContactRepository(
    private val context: Context,
    private val permissionsManager: PermissionsManager,
    private val settings: ContactSearchSettings,
) : SearchableRepository<Contact> {

    fun get(id: Long): Flow<Contact?> = flow {
        val rawContactsCursor = context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID),
            "${ContactsContract.RawContacts.CONTACT_ID} = ?",
            arrayOf(id.toString()),
            null
        )
        if (rawContactsCursor == null) {
            emit(null)
            return@flow
        }
        val rawContacts = mutableSetOf<Long>()
        while (rawContactsCursor.moveToNext()) {
            rawContacts.add(rawContactsCursor.getLong(0))
        }
        rawContactsCursor.close()
        if (rawContacts.isEmpty()) {
            emit(null)
            return@flow
        }
        emit(getWithRawIds(id, rawContacts))
    }

    private suspend fun getWithRawIds(id: Long, rawIds: Set<Long>): Contact? =
        withContext(Dispatchers.IO) {
            val s = "${ContactsContract.Data.RAW_CONTACT_ID} IN (${rawIds.joinToString(", ")})"
            val dataCursor = context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                null, s, null, null
            ) ?: return@withContext null
            var firstName = ""
            var lastName = ""
            var displayName = ""
            val phoneNumbers = mutableListOf<PhoneNumber>()
            val emailAddresses = mutableListOf<EmailAddress>()
            val postalAddresses = mutableListOf<PostalAddress>()
            val contactApps = mutableListOf<ContactApp>()

            val mimeTypeColumn = dataCursor.getColumnIndex(ContactsContract.Data.MIMETYPE)
            val typeColumn =
                dataCursor.getColumnIndex(ContactsContract.CommonDataKinds.Contactables.TYPE)
            val emailAddressColumn =
                dataCursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
            val numberColumn =
                dataCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val addressColumn =
                dataCursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS)
            val displayNameColumn =
                dataCursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME)
            val givenNameColumn =
                dataCursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME)
            val familyNameColumn =
                dataCursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME)
            val accountTypeColumn = dataCursor.getColumnIndex(ContactsContract.Data.ACCOUNT_TYPE_AND_DATA_SET)

            val data3Column = dataCursor.getColumnIndex(ContactsContract.Data.DATA3)
            val idColumn = dataCursor.getColumnIndex(ContactsContract.Data._ID)
            loop@ while (dataCursor.moveToNext()) {
                when (dataCursor.getStringOrNull(mimeTypeColumn)) {
                    ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE ->
                        dataCursor.getStringOrNull(emailAddressColumn)?.let {
                            emailAddresses += EmailAddress(
                                it,
                                when (dataCursor.getInt(typeColumn)) {
                                    ContactsContract.CommonDataKinds.Email.TYPE_HOME -> ContactInfoType.Home
                                    ContactsContract.CommonDataKinds.Email.TYPE_WORK -> ContactInfoType.Work
                                    ContactsContract.CommonDataKinds.Email.TYPE_MOBILE -> ContactInfoType.Mobile
                                    else -> ContactInfoType.Other
                                }
                            )
                        }

                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE ->
                        dataCursor.getStringOrNull(numberColumn)?.let { phone ->
                            phoneNumbers += PhoneNumber(
                                phone,
                                when (dataCursor.getInt(typeColumn)) {
                                    ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> ContactInfoType.Home
                                    ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> ContactInfoType.Work
                                    ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> ContactInfoType.Mobile
                                    else -> ContactInfoType.Other
                                }
                            )
                        }

                    ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE ->
                        dataCursor.getStringOrNull(addressColumn)?.let {
                            postalAddresses += PostalAddress(
                                it,
                                when (dataCursor.getInt(typeColumn)) {
                                    ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME -> ContactInfoType.Home
                                    ContactsContract.CommonDataKinds.StructuredPostal.TYPE_WORK -> ContactInfoType.Work
                                    else -> ContactInfoType.Other
                                }
                            )
                        }

                    ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> {
                        firstName = dataCursor.getStringOrNull(givenNameColumn) ?: ""
                        lastName = dataCursor.getStringOrNull(familyNameColumn) ?: ""
                        displayName = dataCursor.getStringOrNull(displayNameColumn) ?: ""
                    }

                    else -> {
                        val mimeType = dataCursor.getStringOrNull(mimeTypeColumn) ?: continue
                        contactApps += ContactApp(
                            label = dataCursor.getStringOrNull(data3Column) ?: continue,
                            packageName = dataCursor.getStringOrNull(accountTypeColumn) ?: continue,
                            mimeType = mimeType,
                            uri = ContentUris.withAppendedId(
                                ContactsContract.Data.CONTENT_URI,
                                dataCursor.getLongOrNull(idColumn) ?: continue
                            ),
                        )
                    }
                }
            }
            dataCursor.close()

            val lookupKeyCursor = context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts.LOOKUP_KEY),
                "${ContactsContract.Contacts._ID} = ?",
                arrayOf(id.toString()),
                null
            ) ?: return@withContext null
            var lookUpKey = ""
            if (lookupKeyCursor.moveToNext()) {
                lookUpKey = lookupKeyCursor.getString(0)
            }
            lookupKeyCursor.close()

            return@withContext AndroidContact(
                id = id,
                firstName = firstName,
                lastName = lastName,
                displayName = displayName,
                phoneNumbers = phoneNumbers.distinct(),
                emailAddresses = emailAddresses.distinct(),
                postalAddresses = postalAddresses.distinct(),
                contactApps = contactApps.distinct(),
                lookupKey = lookUpKey
            )
        }

    override fun search(query: String, allowNetwork: Boolean): Flow<ImmutableList<Contact>> {
        val hasPermission = permissionsManager.hasPermission(PermissionGroup.Contacts)

        if (query.length < 2) {
            return flow {
                emit(persistentListOf())
            }
        }

        return hasPermission.combine(settings.enabled) { perm, en -> perm && en }.map {
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
            val sel =
                "${ContactsContract.RawContacts.DISPLAY_NAME_PRIMARY} LIKE ? OR ${ContactsContract.RawContacts.DISPLAY_NAME_ALTERNATIVE} LIKE ? OR ${ContactsContract.RawContacts.PHONETIC_NAME} LIKE ? OR ${ContactsContract.RawContacts.SORT_KEY_PRIMARY} LIKE ?"
            val selArgs = arrayOf("%$query%", "%$query%", "%$query%", "%$query%")
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
                getWithRawIds(id, rawIds)?.let { results.add(it) }
                if (results.size > 15) break
            }
            results
        }
        return results.toImmutableList()
    }
}