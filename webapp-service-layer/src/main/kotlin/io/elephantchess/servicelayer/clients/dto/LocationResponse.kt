package io.elephantchess.servicelayer.clients.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LocationResponse(
    val ip: String,
    val type: String,
    @SerialName("country_code") val countryCode: String,
    @SerialName("country_name") val countryName: String,
    @SerialName("region_name") val regionName: String,
    val city: String,
)
