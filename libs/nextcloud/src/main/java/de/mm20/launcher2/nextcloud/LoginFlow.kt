package de.mm20.launcher2.nextcloud

import kotlinx.serialization.Serializable

@Serializable
internal data class LoginFlowResponse(
    val poll: LoginFlowResponsePoll,
    val login: String,
)

@Serializable
internal data class LoginFlowResponsePoll(
    val token: String,
    val endpoint: String,
)

@Serializable
internal data class LoginPollResponse(
    val server: String,
    val loginName: String,
    val appPassword: String,
)