package de.mm20.launcher2.search.contact

import android.net.Uri
import de.mm20.launcher2.serialization.UriSerializer
import kotlinx.serialization.Serializable

@Serializable
data class PhoneNumber(
    val number: String,
    val type: ContactInfoType = ContactInfoType.Other,
)

@Serializable
data class EmailAddress(
    val address: String,
    val type: ContactInfoType = ContactInfoType.Other,
)

@Serializable
data class PostalAddress(
    val address: String,
    val type: ContactInfoType = ContactInfoType.Other,
)

/**
 * Custom contact action, for example, WhatsApp message, Telegram video call, etc.
 */
@Serializable
data class CustomContactAction(
    val label: String,
    /**
     * The data URI that is passed to the Intent.
     */
    @Serializable(with = UriSerializer::class) val uri: Uri,
    /**
     * Type that is passed to the Intent.
     */
    val mimeType: String? = null,
    /**
     * Package name of the app that handles this channel.
     * Used to get the app icon, and label, and to group actions by app.
     * If the app is not installed, the action will be ignored.
     */
    val packageName: String,
)

enum class ContactInfoType {
    /**
     * Home or private number/address.
     */
    Home,

    /**
     * Cell phone number, only applicable for phone numbers.
     */
    Mobile,

    /**
     * Work or business number/address.
     */
    Work,

    /**
     * Other
     */
    Other,
}
