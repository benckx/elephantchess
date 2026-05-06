package io.elephantchess.servicelayer.model

/**
 * Email address matching a UUID unsubscription code (from a newsletter)
 */
data class MatchingEmail(
    val emailAddress: String,
    val isUnsubscribeFromNewsletter: Boolean,
    val isUnsubscribeFromAll: Boolean,
)
