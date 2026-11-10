package io.elephantchess.model

/**
 * Enhanced delivery-status codes that suppress further mail, not just permanent (5.x.x) failures.
 * Other codes, including unknown future codes, remain stored verbatim for diagnosis.
 */
enum class SuppressingEmailStatus(val code: String) {

    BAD_DESTINATION_MAILBOX("5.1.1"),
    BAD_DESTINATION_ADDRESS_SYNTAX("5.1.3"),
    OTHER_ADDRESS_STATUS("5.1.0"),
    MAILBOX_DISABLED("5.2.1"),
    MAILBOX_FULL("5.2.2"),
    DIRECTORY_SERVER_FAILURE("4.4.3"),
    OTHER_UNDEFINED_STATUS("5.0.0"),
    ;

    companion object {

        val codes: Set<String> = entries.map { it.code }.toSet()

        // Legacy rows predate status collection and must not silently lose their suppression.
        fun suppresses(status: String?): Boolean =
            status == null || status in codes
    }
}
