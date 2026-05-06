package io.elephantchess.servicelayer.clients.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreditResponse(
    val onDemand: OnDemand?
) {

    @Serializable
    data class OnDemand(
        val available: Int
    )

}
