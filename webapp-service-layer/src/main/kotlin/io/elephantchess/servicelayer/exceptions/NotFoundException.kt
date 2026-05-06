package io.elephantchess.servicelayer.exceptions

import java.net.HttpURLConnection.HTTP_NOT_FOUND

class NotFoundException(message: String) : HttpErrorException(HTTP_NOT_FOUND, message)
