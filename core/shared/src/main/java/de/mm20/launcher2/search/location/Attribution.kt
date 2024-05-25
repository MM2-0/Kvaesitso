package de.mm20.launcher2.search.location

import android.net.Uri
import de.mm20.launcher2.serialization.UriSerializer
import kotlinx.serialization.Serializable

@Serializable
data class Attribution(
    val text: String?,
    @Serializable(with = UriSerializer::class)
    val iconUrl: Uri?,
    val url: String?,
)