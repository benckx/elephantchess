package io.elephantchess.servicelayer.exceptions

import java.net.HttpURLConnection.HTTP_FORBIDDEN

class ForbiddenException(message: String) : HttpErrorException(HTTP_FORBIDDEN, message)
