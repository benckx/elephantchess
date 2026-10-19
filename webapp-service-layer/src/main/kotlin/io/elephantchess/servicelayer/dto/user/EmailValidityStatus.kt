package io.elephantchess.servicelayer.dto.user

/**
 * Describes how the email address of a user has been (or has not been) validated.
 *
 * The check is layered:
 * 1. If the user clicked the confirmation link sent at signup, the address is considered
 *    [MANUALLY_CONFIRMED] (the strongest signal).
 * 2. Otherwise, we fall back to the automated verification (external service / bounce list)
 *    and report one of [AUTOMATED_VALID], [AUTOMATED_BOUNCED] or [AUTOMATED_INVALID].
 * 3. If we have no information at all, the status is [UNKNOWN].
 */
enum class EmailValidityStatus {

    /**
     * The user clicked the confirmation link we sent them at signup.
     */
    MANUALLY_CONFIRMED,

    /**
     * The external automated verification reported the address as valid.
     */
    AUTOMATED_VALID,

    /**
     * An email previously sent to this address bounced.
     */
    AUTOMATED_BOUNCED,

    /**
     * The external automated verification reported the address as invalid (other than a bounce).
     */
    AUTOMATED_INVALID,

    /**
     * No information available yet.
     */
    UNKNOWN;

    val isValid: Boolean
        get() = this == MANUALLY_CONFIRMED || this == AUTOMATED_VALID

}
