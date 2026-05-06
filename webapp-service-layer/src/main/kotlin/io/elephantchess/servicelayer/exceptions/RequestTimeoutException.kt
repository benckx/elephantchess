package io.elephantchess.servicelayer.exceptions

import java.net.HttpURLConnection.HTTP_CLIENT_TIMEOUT

class RequestTimeoutException(message: String) : HttpErrorException(HTTP_CLIENT_TIMEOUT, message)
