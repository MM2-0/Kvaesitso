package de.mm20.launcher2.nextcloud

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class UserReponse(
    val ocs: UserReponseOcs
)


@Serializable
internal data class UserReponseOcs(
    val data: UserReponseOcsData
)

@Serializable
internal data class UserReponseOcsData(
    @SerialName("display-name") val displayName: String?,
)