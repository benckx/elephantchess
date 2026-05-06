package io.elephantchess.servicelayer.dto

import io.elephantchess.utils.GenericEither

open class ValidatedResponse<T>(errors: ValidationErrorsResponse?, t: T?) :
    GenericEither<ValidationErrorsResponse, T>(errors, t) {

    class Invalid<T>(errors: ValidationErrorsResponse) : ValidatedResponse<T>(errors, null) {

        constructor(errors: List<String>) : this(ValidationErrorsResponse(errors))
        constructor(error: String) : this(ValidationErrorsResponse(listOf(error)))

    }

    class Valid<B>(b: B) : ValidatedResponse<B>(null, b)

}
