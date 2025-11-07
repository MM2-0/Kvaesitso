package de.mm20.launcher2.search

import android.content.Context
import de.mm20.launcher2.base.R
import de.mm20.launcher2.icons.ColorLayer
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.icons.TextLayer
import de.mm20.launcher2.icons.VectorLayer
import de.mm20.launcher2.search.contact.CustomContactAction
import de.mm20.launcher2.search.contact.EmailAddress
import de.mm20.launcher2.search.contact.PhoneNumber
import de.mm20.launcher2.search.contact.PostalAddress

interface Contact : SavableSearchable {
    val name: String
    val phoneNumbers: List<PhoneNumber>
    val emailAddresses: List<EmailAddress>
    val postalAddresses: List<PostalAddress>
    val customActions: List<CustomContactAction>

    override val label: String
        get() = name

    val summary: String
        get() {
            return (phoneNumbers.map { it.number } + emailAddresses.map { it.address })
                .joinToString(", ")
        }

    override fun getPlaceholderIcon(context: Context): StaticLauncherIcon {
        val letter = (labelOverride ?: label).firstOrNull()?.toString()

        if (letter != null) {
            return StaticLauncherIcon(
                foregroundLayer = TextLayer(text = letter, color = 0xFF2364AA.toInt()),
                backgroundLayer = ColorLayer(0xFF2364AA.toInt())
            )
        }

        return StaticLauncherIcon(
            foregroundLayer = VectorLayer(R.drawable.person_24px),
            backgroundLayer = ColorLayer(0xFF2364AA.toInt())
        )
    }

    override val preferDetailsOverLaunch: Boolean
        get() = true
}
