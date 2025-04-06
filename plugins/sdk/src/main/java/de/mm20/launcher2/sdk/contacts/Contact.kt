package de.mm20.launcher2.sdk.contacts

import android.net.Uri
import de.mm20.launcher2.search.contact.CustomContactAction
import de.mm20.launcher2.search.contact.EmailAddress
import de.mm20.launcher2.search.contact.PhoneNumber
import de.mm20.launcher2.search.contact.PostalAddress

data class Contact(
    /**
     * A unique and stable identifier for this contact.
     */
    val id: String,

    /**
     * The URI to view this contact.
     */
    val uri: Uri,
    /**
     * The display name for this contact.
     * First name + last name, if applicable.
     */
    val name: String,
    /**
     * List of phone numbers for this contact.
     */
    val phoneNumbers: List<PhoneNumber> = emptyList(),
    /**
     * List of email addresses for this contact.
     */
    val emailAddresses: List<EmailAddress> = emptyList(),
    /**
     * List of postal addresses for this contact.
     */
    val postalAddresses: List<PostalAddress> = emptyList(),
    /**
     * List of additional actions to contact this person.
     * For example, call on WhatsApp, send a message on Telegram, etc.
     */
    val customActions: List<CustomContactAction> = emptyList(),

    /**
     * The URI of the contact's photo.
     * This is a data URI, and the launcher will use it to display the contact's photo.
     * If null, the launcher will use a default icon.
     */
    val photoUri: Uri? = null,
)
