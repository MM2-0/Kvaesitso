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
 * Custom contact channel, for example, WhatsApp message, Telegram video call, etc.
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
    val mimeType: String,
    /**
     * Package name of the app that handles this channel.
     * Used to get the app icon, and label, and to group channels by app.
     * If the app is not installed, the channel will be ignored.
     */
    val packageName: String,
)

enum class ContactInfoType {
    Home,
    Mobile,
    Work,
    Other,
}
