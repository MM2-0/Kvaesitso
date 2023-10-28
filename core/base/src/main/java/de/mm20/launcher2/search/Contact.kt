package de.mm20.launcher2.search

import android.content.Intent

interface Contact: SavableSearchable {
    val firstName: String
    val lastName: String
    val displayName: String
    val summary: String
    val contactInfos: Iterable<ContactInfo>

    override val preferDetailsOverLaunch: Boolean
        get() = true
}

/**
 * Type of the contact info
 * Acts as a hint for the UI, so that it can display the correct icon and group them accordingly
 */
enum class ContactInfoType {
    Phone,
    Message,
    Email,
    Postal,
    Telegram,
    Whatsapp,
    Signal,
    Other
}

interface ContactInfo {
    val type: ContactInfoType
    val label: String
    val intent: Intent
}