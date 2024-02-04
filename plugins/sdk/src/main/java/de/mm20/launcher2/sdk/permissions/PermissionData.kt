package de.mm20.launcher2.sdk.permissions

import kotlinx.serialization.Serializable

@Serializable
internal data class PermissionData(
    val granted: Set<String> = emptySet(),
)