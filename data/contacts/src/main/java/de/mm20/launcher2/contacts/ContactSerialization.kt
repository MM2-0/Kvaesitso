package de.mm20.launcher2.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import de.mm20.launcher2.ktx.jsonObjectOf
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchableDeserializer
import de.mm20.launcher2.search.SearchableSerializer
import de.mm20.launcher2.search.data.Contact
import org.json.JSONObject

class ContactSerializer : SearchableSerializer {
    override fun serialize(searchable: SavableSearchable): String {
        searchable as Contact
        return jsonObjectOf(
            "id" to searchable.id
        ).toString()
    }

    override val typePrefix: String
        get() = "contact"
}

class ContactDeserializer(val context: Context) : SearchableDeserializer {
    override fun deserialize(serialized: String): SavableSearchable? {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) return null
        val id = JSONObject(serialized).getLong("id")
        val rawContactsCursor = context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID),
            "${ContactsContract.RawContacts.CONTACT_ID} = ?",
            arrayOf(id.toString()),
            null
        ) ?: return null
        val rawContacts = mutableSetOf<Long>()
        while (rawContactsCursor.moveToNext()) {
            rawContacts.add(rawContactsCursor.getLong(0))
        }
        rawContactsCursor.close()
        if (rawContacts.isEmpty()) return null

        return Contact.contactById(context, id, rawContacts)
    }
}