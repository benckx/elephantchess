package io.elephantchess.servicelayer.exceptions

import java.net.HttpURLConnection.HTTP_UNAUTHORIZED

class UnauthorizedException(message: String) : HttpErrorException(HTTP_UNAUTHORIZED, message)
