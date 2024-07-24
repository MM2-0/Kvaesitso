package de.mm20.launcher2.search

import android.net.Uri

interface Contact : SavableSearchable {
    val firstName: String
    val lastName: String
    val displayName: String
    val summary: String
    val phoneNumbers: List<PhoneNumber>
    val emailAddresses: List<EmailAddress>
    val postalAddresses: List<PostalAddress>
    val contactApps: List<ContactApp>

    override val preferDetailsOverLaunch: Boolean
        get() = true
}

data class PhoneNumber(
    val number: String,
    val type: ContactInfoType,
)

data class EmailAddress(
    val address: String,
    val type: ContactInfoType,
)

data class PostalAddress(
    val address: String,
    val type: ContactInfoType,
)

data class ContactApp(
    val label: String,
    val uri: Uri,
    val mimeType: String,
    val packageName: String,
)

enum class ContactInfoType {
    Home,
    Mobile,
    Work,
    Other,
}
