package io.elephantchess.servicelayer.dto.user

/**
 * Describes how the email address of a user has been (or has not been) validated.
 *
 * The check is layered:
 * 1. A bounce with a suppressing delivery status takes precedence: [AUTOMATED_BOUNCED].
 * 2. Otherwise, confirmation gives [MANUALLY_CONFIRMED], or external verification gives
 *    [AUTOMATED_VALID] / [AUTOMATED_INVALID].
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
     * A stored bounce suppresses delivery under the current status policy.
     */
    AUTOMATED_BOUNCED,

    /**
     * The external automated verification reported the address as invalid (other than a bounce).
     */
    AUTOMATED_INVALID,

    /**
     * No information available yet.
     */
    UNKNOWN,

    ;

    val isValid: Boolean
        get() = this == MANUALLY_CONFIRMED || this == AUTOMATED_VALID
}
