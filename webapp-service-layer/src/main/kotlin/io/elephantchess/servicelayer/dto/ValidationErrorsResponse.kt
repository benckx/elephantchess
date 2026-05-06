package io.elephantchess.servicelayer.dto

// TODO: move as inner class of ValidatedResponse
data class ValidationErrorsResponse(val errors: List<String>) {

    constructor(error: String) : this(listOf(error))

    constructor(exception: Exception) : this(exception.message ?: "unknown error")

}
