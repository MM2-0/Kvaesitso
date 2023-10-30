package de.mm20.launcher2.contacts

import android.content.Context
import android.provider.ContactsContract
import androidx.core.database.getStringOrNull
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.search.Contact
import de.mm20.launcher2.search.ContactInfo
import de.mm20.launcher2.search.SearchableRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext

internal class ContactRepository(
    private val context: Context,
    private val permissionsManager: PermissionsManager
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

    private suspend fun getWithRawIds(id: Long, rawIds: Set<Long>): Contact? = withContext(Dispatchers.IO) {
        val s = "(" + rawIds.joinToString(separator = " OR ",
            transform = { "${ContactsContract.Data.RAW_CONTACT_ID} = $it" }) + ")" +
                " AND (${ContactsContract.Data.MIMETYPE} = \"${ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE}\"" +
                " OR ${ContactsContract.Data.MIMETYPE} = \"${ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE}\"" +
                " OR ${ContactsContract.Data.MIMETYPE} = \"${ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE}\"" +
                " OR ${ContactsContract.Data.MIMETYPE} = \"${ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE}\"" +
                " OR ${ContactsContract.Data.MIMETYPE} = \"${TelegramContactInfo.ItemType}\"" +
                " OR ${ContactsContract.Data.MIMETYPE} = \"${WhatsAppContactInfo.ItemType}\"" +
                " OR ${ContactsContract.Data.MIMETYPE} = \"${SignalContactInfo.ItemType}\"" +
                ")"
        val dataCursor = context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            null, s, null, null
        ) ?: return@withContext null
        val contactInfos = mutableSetOf<ContactInfo>()
        var firstName = ""
        var lastName = ""
        var displayName = ""
        val mimeTypeColumn = dataCursor.getColumnIndex(ContactsContract.Data.MIMETYPE)
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
        val data1Column = dataCursor.getColumnIndex(ContactsContract.Data.DATA1)
        val data3Column = dataCursor.getColumnIndex(ContactsContract.Data.DATA3)
        val idColumn = dataCursor.getColumnIndex(ContactsContract.Data._ID)
        loop@ while (dataCursor.moveToNext()) {
            when (dataCursor.getStringOrNull(mimeTypeColumn)) {
                ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE ->
                    dataCursor.getStringOrNull(emailAddressColumn)?.let {
                        contactInfos.add(MailContactInfo(it))
                    }

                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE ->
                    dataCursor.getStringOrNull(numberColumn)?.let {
                        val phone = it.replace(Regex("[^+0-9]"), "")
                        contactInfos.add(PhoneContactInfo(phone))
                    }

                ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE ->
                    dataCursor.getStringOrNull(addressColumn)?.let {
                        contactInfos.add(PostalContactInfo(it))
                    }

                ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> {
                    firstName = dataCursor.getStringOrNull(givenNameColumn) ?: ""
                    lastName = dataCursor.getStringOrNull(familyNameColumn) ?: ""
                    displayName = dataCursor.getStringOrNull(displayNameColumn) ?: ""
                }

                TelegramContactInfo.ItemType -> {
                    val data1 = dataCursor.getStringOrNull(data1Column)
                        ?: continue@loop
                    val data3 = dataCursor.getStringOrNull(data3Column)
                        ?: continue@loop
                    contactInfos.add(
                        TelegramContactInfo(data3.substringAfterLast(" "), data1)
                    )
                }

                WhatsAppContactInfo.ItemType -> {
                    val data1 = dataCursor.getStringOrNull(data1Column)
                        ?: continue@loop
                    val dataId = dataCursor.getLong(idColumn)
                    contactInfos.add(WhatsAppContactInfo("+${data1.substringBefore('@')}", dataId))
                }

                SignalContactInfo.ItemType -> {
                    val data1 = dataCursor.getStringOrNull(data1Column)
                        ?: continue@loop
                    val dataId = dataCursor.getLong(idColumn)
                    contactInfos.add(SignalContactInfo(data1, dataId))
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
            contactInfos = contactInfos,
            lookupKey = lookUpKey
        )
    }

    override fun search(query: String): Flow<ImmutableList<Contact>> {
        val hasPermission = permissionsManager.hasPermission(PermissionGroup.Contacts)

        if (query.length < 2) {
            return flow {
                emit(persistentListOf())
            }
        }

        return hasPermission.map {
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