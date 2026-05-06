package io.elephantchess.servicelayer.exceptions

/**
 * Exception that can be mapped to a http error code
 */
abstract class HttpErrorException(val code: Int, message: String) : Exception(message)
