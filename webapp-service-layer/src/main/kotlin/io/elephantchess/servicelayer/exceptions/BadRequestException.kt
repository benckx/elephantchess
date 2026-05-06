package io.elephantchess.servicelayer.exceptions

import java.net.HttpURLConnection.HTTP_BAD_REQUEST

class BadRequestException(message: String) : HttpErrorException(HTTP_BAD_REQUEST, message)
