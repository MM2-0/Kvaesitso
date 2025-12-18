package de.mm20.launcher2.nextcloud

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class NcUser(
    val displayName: String,
    val username: String
)

