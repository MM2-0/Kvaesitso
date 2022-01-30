package de.mm20.launcher2.search.data

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import androidx.core.database.getStringOrNull
import androidx.core.graphics.drawable.toDrawable
import de.mm20.launcher2.contacts.R
import de.mm20.launcher2.graphics.TextDrawable
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.ktx.asBitmap
import de.mm20.launcher2.ktx.sp
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.Settings
import de.mm20.launcher2.preferences.Settings.IconSettings.LegacyIconBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class Contact(
    val id: Long,
    val firstName: String,
    val lastName: String,
    val displayName: String,
    val lookupKey: String,
    val phones: Set<String>,
    val emails: Set<String>,
    val telegram: Set<String>,
    val whatsapp: Set<String>,
    val postals: Set<String>
) : Searchable() {
    override val key: String
        get() = "contact://$id"
    override val label: String
        get() = "$firstName $lastName"

    val summary: String
        get() {
            return phones.union(emails).joinToString(separator = ", ")
        }

    override fun getPlaceholderIcon(context: Context): LauncherIcon {
        val iconText =
            if (firstName.isNotEmpty()) firstName[0].toString() else "" + if (lastName.isNotEmpty()) lastName[0].toString() else ""
        return LauncherIcon(
            foreground = TextDrawable(
                iconText,
                Color.WHITE,
                fontSize = 40 * context.sp,
                typeface = Typeface.DEFAULT_BOLD
            ),
            background = ColorDrawable(ContextCompat.getColor(context, R.color.blue))
        )
    }

    override suspend fun loadIcon(context: Context, size: Int, legacyIconBackground: LegacyIconBackground): LauncherIcon? {
        val contentResolver = context.contentResolver
        val bmp = withContext(Dispatchers.IO) {
            val uri = ContactsContract.Contacts.getLookupUri(id, lookupKey) ?: return@withContext null
            ContactsContract.Contacts.openContactPhotoInputStream(contentResolver, uri, false)
                ?.asBitmap()
        } ?: return null
        return LauncherIcon(
            foreground = bmp.toDrawable(context.resources),
            background = null,
            autoGenerateBackgroundMode = legacyIconBackground.number
        )
    }

    override fun getLaunchIntent(context: Context): Intent? {
        return null
    }

    companion object {
        internal fun contactById(context: Context, id: Long, rawIds: Set<Long>): Contact? {
            val s = "(" + rawIds.joinToString(separator = " OR ",
                transform = { "${ContactsContract.Data.RAW_CONTACT_ID} = $it" }) + ")" +
                    " AND (${ContactsContract.Data.MIMETYPE} = \"${ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE}\"" +
                    " OR ${ContactsContract.Data.MIMETYPE} = \"${ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE}\"" +
                    " OR ${ContactsContract.Data.MIMETYPE} = \"${ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE}\"" +
                    " OR ${ContactsContract.Data.MIMETYPE} = \"${ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE}\"" +
                    " OR ${ContactsContract.Data.MIMETYPE} = \"vnd.android.cursor.item/vnd.org.telegram.messenger.android.profile\"" +
                    " OR ${ContactsContract.Data.MIMETYPE} = \"vnd.android.cursor.item/vnd.com.whatsapp.profile\"" +
                    ")"
            val dataCursor = context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                null, s, null, null
            ) ?: return null
            val phones = mutableSetOf<String>()
            val emails = mutableSetOf<String>()
            val telegram = mutableSetOf<String>()
            val whatsapp = mutableSetOf<String>()
            val postals = mutableSetOf<String>()
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
                        dataCursor.getStringOrNull(emailAddressColumn)?.let { emails.add(it) }
                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE ->
                        dataCursor.getStringOrNull(numberColumn)?.let {
                            phones.add(it.replace(Regex("[^+0-9]"), ""))
                        }
                    ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE ->
                        dataCursor.getStringOrNull(addressColumn)?.let { postals.add(it) }
                    ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> {
                        firstName = dataCursor.getStringOrNull(givenNameColumn) ?: ""
                        lastName = dataCursor.getStringOrNull(familyNameColumn) ?: ""
                        displayName = dataCursor.getStringOrNull(displayNameColumn) ?: ""
                    }
                    "vnd.android.cursor.item/vnd.org.telegram.messenger.android.profile" -> {
                        val data1 = dataCursor.getStringOrNull(data1Column)
                            ?: continue@loop
                        val data3 = dataCursor.getStringOrNull(data3Column)
                            ?: continue@loop
                        telegram.add("$data1$$data3")
                    }
                    "vnd.android.cursor.item/vnd.com.whatsapp.profile" -> {
                        val data1 = dataCursor.getStringOrNull(data1Column)
                            ?: continue@loop
                        val dataId = dataCursor.getLong(idColumn)
                        whatsapp.add("$dataId$+${data1.substringBefore('@')}")
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
            ) ?: return null
            var lookUpKey = ""
            if (lookupKeyCursor.moveToNext()) {
                lookUpKey = lookupKeyCursor.getString(0)
            }
            lookupKeyCursor.close()

            return Contact(
                id = id,
                emails = emails,
                phones = phones,
                firstName = firstName,
                lastName = lastName,
                displayName = displayName,
                postals = postals,
                telegram = telegram,
                whatsapp = whatsapp,
                lookupKey = lookUpKey
            )
        }

    }
}