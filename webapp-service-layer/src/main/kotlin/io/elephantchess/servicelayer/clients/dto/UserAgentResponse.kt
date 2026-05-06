package io.elephantchess.servicelayer.clients.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserAgentResponse(
    val browser: Browser,
    val device: Device,
    val os: Os,
    val type: Type,
    val ua: String
) {

    @Serializable
    data class Browser(
        val name: String?,
        val version: String?,
        @SerialName("version_major") val versionMajor: Int?,
    )

    @Serializable
    data class Device(
        val brand: String?,
        val model: String?,
        val name: String?,
    )

    @Serializable
    data class Os(
        val name: String?,
        val version: String?,
        @SerialName("version_major") val versionMajor: Int?,
    )

    @Serializable
    data class Type(
        val bot: Boolean?,
        val mobile: Boolean?,
        val pc: Boolean?,
        val tablet: Boolean?,
        @SerialName("touch_capable") val touchCapable: Boolean?,
    )

}
