package io.elephantchess.servicelayer.clients.dto

import kotlinx.serialization.Serializable

@Serializable
data class EmailVerificationResult(
    val email: String?,
    val result: String?,
    val mxServer: String?,
    val mxServerIp: String?,
    val esp: String?,
    val account: String?,
    val tag: String?,
    val isRole: Boolean?,
    val isFree: Boolean?,
    val isNoReply: Boolean?,
    val firstName: String?,
    val lastName: String?,
    val gender: String?,
)
