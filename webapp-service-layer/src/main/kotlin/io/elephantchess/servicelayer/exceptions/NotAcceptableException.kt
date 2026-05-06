package io.elephantchess.servicelayer.exceptions

import java.net.HttpURLConnection.HTTP_NOT_ACCEPTABLE

class NotAcceptableException(message: String) : HttpErrorException(HTTP_NOT_ACCEPTABLE, message)
