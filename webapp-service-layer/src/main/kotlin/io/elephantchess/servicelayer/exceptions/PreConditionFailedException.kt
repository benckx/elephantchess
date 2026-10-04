package io.elephantchess.servicelayer.exceptions

import java.net.HttpURLConnection.HTTP_PRECON_FAILED

class PreConditionFailedException(message: String) : HttpErrorException(HTTP_PRECON_FAILED, message)
