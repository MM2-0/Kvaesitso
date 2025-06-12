package de.mm20.launcher2.contacts.providers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import coil.imageLoader
import coil.request.ImageRequest
import de.mm20.launcher2.contacts.PluginContactSerializer
import de.mm20.launcher2.icons.ColorLayer
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.icons.StaticIconLayer
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.plugin.config.StorageStrategy
import de.mm20.launcher2.search.Contact
import de.mm20.launcher2.search.File
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchableSerializer
import de.mm20.launcher2.search.UpdatableSearchable
import de.mm20.launcher2.search.UpdateResult
import de.mm20.launcher2.search.contact.CustomContactAction
import de.mm20.launcher2.search.contact.EmailAddress
import de.mm20.launcher2.search.contact.PhoneNumber
import de.mm20.launcher2.search.contact.PostalAddress

internal data class PluginContact(
    val id: String,
    val uri: Uri,
    override val name: String,
    override val phoneNumbers: List<PhoneNumber>,
    override val emailAddresses: List<EmailAddress>,
    override val postalAddresses: List<PostalAddress>,
    override val customActions: List<CustomContactAction>,
    val photoUri: Uri?,
    override val labelOverride: String? = null,
    val authority: String,
    val storageStrategy: StorageStrategy,
    override val timestamp: Long,
    override val updatedSelf: (suspend (SavableSearchable) -> UpdateResult<Contact>)?
) : Contact, UpdatableSearchable<Contact> {
    override val domain: String = Domain
    override fun getSerializer(): SearchableSerializer {
        return PluginContactSerializer()
    }

    override val key: String = "$domain://$authority:$id"
    override fun overrideLabel(label: String): SavableSearchable {
        return copy(labelOverride = label)
    }

    override fun launch(
        context: Context,
        options: Bundle?
    ): Boolean {
        return context.tryStartActivity(
            Intent(
                Intent.ACTION_VIEW
            ).apply {
                data = uri
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }, options
        )
    }

    override suspend fun loadIcon(context: Context, size: Int, themed: Boolean): LauncherIcon? {
        if (photoUri != null) {
            val request = ImageRequest.Builder(context)
                .data(photoUri)
                .size(size)
                .build()
            val result = context.imageLoader.execute(request)
            val drawable = result.drawable ?: return null
            return StaticLauncherIcon(
                foregroundLayer = StaticIconLayer(icon = drawable),
                backgroundLayer = ColorLayer(),
            )
        }
        return super.loadIcon(context, size, themed)
    }

    companion object {
        const val Domain = "plugin.contact"
    }
}