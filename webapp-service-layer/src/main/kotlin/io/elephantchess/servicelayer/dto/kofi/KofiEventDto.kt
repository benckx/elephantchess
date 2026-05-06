package io.elephantchess.servicelayer.dto.kofi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KofiEventDto(
    @SerialName("verification_token")
    val verificationToken: String,
    @SerialName("message_id")
    val messageId: String,
    val timestamp: String,
    val type: String,
    @SerialName("is_public")
    val isPublic: Boolean,
    @SerialName("from_name")
    val fromName: String,
    val message: String? = null,
    val amount: String,
    val url: String?,
    val email: String? = null,
    val currency: String,
    @SerialName("is_subscription_payment")
    val isSubscriptionPayment: Boolean,
    @SerialName("is_first_subscription_payment")
    val isFirstSubscriptionPayment: Boolean,
    @SerialName("kofi_transaction_id")
    val kofiTransactionId: String,
    @SerialName("tier_name")
    val tierName: String? = null,
    @SerialName("discord_username")
    val discordUsername: String? = null,
    @SerialName("discord_userid")
    val discordUserid: String? = null
)
