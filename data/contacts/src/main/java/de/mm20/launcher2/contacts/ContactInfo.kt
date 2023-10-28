package de.mm20.launcher2.contacts

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import de.mm20.launcher2.search.ContactInfo
import de.mm20.launcher2.search.ContactInfoType
import java.net.URLEncoder


internal data class PhoneContactInfo(
    val number: String,
) : ContactInfo {
    override val label: String
        get() = number

    override val type: ContactInfoType = ContactInfoType.Phone

    override val intent: Intent
        get() = Intent(Intent.ACTION_VIEW)
            .setData(Uri.parse("tel:$number"))
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

internal data class MailContactInfo(
    val address: String,
) : ContactInfo {
    override val label: String
        get() = address

    override val type: ContactInfoType = ContactInfoType.Email

    override val intent: Intent
        get() = Intent(Intent.ACTION_VIEW)
            .setData(Uri.parse("mailto:$address"))
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

internal data class PostalContactInfo(
    val address: String,
) : ContactInfo {
    override val label: String
        get() = address.replace("\n", ", ")

    override val type: ContactInfoType = ContactInfoType.Postal

    override val intent: Intent
        get() = Intent(Intent.ACTION_VIEW)
            .setData(Uri.parse("geo:0,0?q=${URLEncoder.encode(address, "utf8")}"))
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

internal data class TelegramContactInfo(
    override val label: String,
    val userId: String,
) : ContactInfo {

    override val type: ContactInfoType = ContactInfoType.Telegram

    override val intent: Intent
        get() = Intent(Intent.ACTION_VIEW)
            .setData(Uri.parse("tg:openmessage?user_id=$userId"))
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    internal companion object {
        const val ItemType = "vnd.android.cursor.item/vnd.org.telegram.messenger.android.profile"
    }
}

internal data class SignalContactInfo(
    override val label: String,
    val dataId: Long,
) : ContactInfo {

    override val type: ContactInfoType = ContactInfoType.Signal

    override val intent: Intent
        get() = Intent(Intent.ACTION_VIEW)
            .setData(
                Uri.withAppendedPath(
                    ContactsContract.Data.CONTENT_URI,
                    dataId.toString()
                )
            )
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    internal companion object {
        const val ItemType = "vnd.android.cursor.item/vnd.org.thoughtcrime.securesms.contact"
    }
}

internal data class WhatsAppContactInfo(
    override val label: String,
    val dataId: Long,
) : ContactInfo {

    override val type: ContactInfoType = ContactInfoType.Whatsapp

    override val intent: Intent
        get() = Intent(Intent.ACTION_VIEW)
            .setData(
                Uri.withAppendedPath(
                    ContactsContract.Data.CONTENT_URI,
                    dataId.toString()
                )
            )
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    internal companion object {
        const val ItemType = "vnd.android.cursor.item/vnd.com.whatsapp.profile"
    }
}