package io.elephantchess.servicelayer.exceptions

import java.net.HttpURLConnection.HTTP_INTERNAL_ERROR

class InternalErrorException(message: String) : HttpErrorException(HTTP_INTERNAL_ERROR, message)
