package io.elephantchess.servicelayer.exceptions

import java.net.HttpURLConnection.HTTP_PRECON_FAILED

class PreConditionFailedException(message: String, cause: Throwable? = null) : HttpErrorException(HTTP_PRECON_FAILED, message, cause)
