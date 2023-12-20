package de.mm20.launcher2.sdk.permissions

import kotlinx.serialization.Serializable

@Serializable
data class PermissionData(
    val granted: Set<String> = emptySet(),
)