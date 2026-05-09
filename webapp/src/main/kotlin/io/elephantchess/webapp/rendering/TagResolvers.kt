package io.elephantchess.webapp.rendering

import io.elephantchess.htmlrenderer.TagResolver
import io.elephantchess.servicelayer.dto.kofi.LatestSupporter
import io.elephantchess.utils.stripHtml
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset.UTC
import java.time.format.DateTimeFormatter.ofPattern
import kotlin.math.floor

private const val COST_IN_EUR_PER_DAY = 4
private val supportedCurrencies = setOf("USD", "EUR", "GBP")

fun formatNewLinesToHtmlParagraphs(str: String): String {
    return str
        .split("\n\n")
        .filter { it.trim().isNotEmpty() }
        .joinToString("") { "<p>${it.trim()}</p>" }
}

fun meta(name: String, content: String) =
    """<meta name="$name" content="${stripHtml(content)}">"""

fun descriptionMeta(description: String): String {
    return meta(
        "description", description
            .replace("\n", " ")
            .replace("\r", "")
    )
}

fun latestSupporterTagResolver(latestSupporter: LatestSupporter?): TagResolver {
    fun mapCurrencyToSymbol(currency: String): String {
        return when (currency.uppercase()) {
            "USD" -> "$"
            "EUR" -> "€"
            "GBP" -> "£"
            else -> currency
        }
    }

    fun formatUserName(dto: LatestSupporter): String {
        return if (dto.userId != null) {
            """<a href="/@/${dto.username}">${dto.username}</a>"""
        } else {
            dto.username
        }
    }

    fun formatAmount(dto: LatestSupporter): String {
        // format amount
        val formattedAmount = if (dto.amount % 1.0 == 0.0) {
            // show no decimals when the amount is a whole number (e.g., 5€ instead of 5.0€)
            dto.amount.toInt().toString()
        } else {
            // show decimals when there's a fractional part (e.g., 5.50€)
            dto.amount.toString()
        }
        return "$formattedAmount${mapCurrencyToSymbol(dto.currency)}"
    }

    fun formatDate(dto: LatestSupporter): String {
        val date = Instant.ofEpochMilli(dto.timestamp).atZone(UTC).toLocalDate()
        val year = date.year
        val currentYear = LocalDate.now(UTC).year
        return if (year == currentYear) {
            date.format(ofPattern("MMM. d"))
        } else {
            date.format(ofPattern("MMM. d yyyy"))
        }
    }

    fun formatDescription(dto: LatestSupporter): String {
        val formattedAmount = formatAmount(dto)
        val formattedDate = formatDate(dto)

        val typeOfEventStr =
            if (dto.recurring) "$formattedAmount pledge" else "$formattedAmount tip"

        val recurringStr =
            if (dto.recurring) " every month" else ""

        val allowUsToTenseStr =
            if (dto.recurring) "allows us to finance" else "allowed us to finance"

        // only compute the number-of-days message for currencies whose value is
        // close enough to the EUR-based COST_IN_EUR_PER_DAY for the estimate to make sense
        val isCurrencySupported = dto.currency.uppercase() in supportedCurrencies

        return if (!isCurrencySupported) {
            "Their $typeOfEventStr on $formattedDate $allowUsToTenseStr the platform${recurringStr} for a little bit!"
        } else {
            // format amount
            val numberOfDays = floor(dto.amount / COST_IN_EUR_PER_DAY).toInt()
            if (numberOfDays < 1) {
                "Their $typeOfEventStr on $formattedDate $allowUsToTenseStr the platform for a few hours${recurringStr}!"
            } else if (dto.recurring && numberOfDays >= 30) {
                // recurring pledges large enough to cover an entire month (or more):
                // avoid the misleading "43 entire days every month" phrasing.
                "Their incredibly generous $typeOfEventStr on $formattedDate $allowUsToTenseStr <b>the entire platform every month!</b>"
            } else {
                val nbrOfDaysStr =
                    if (numberOfDays == 1) "an entire day!" else "$numberOfDays entire days${recurringStr}!"
                "Their generous $typeOfEventStr on $formattedDate $allowUsToTenseStr the platform for <b>$nbrOfDaysStr</b>"
            }
        }
    }

    return CallbackTagResolver("latest_supporter_banner_content") {
        if (latestSupporter != null) {
            val username = formatUserName(latestSupporter)
            val description = formatDescription(latestSupporter)

            """<p>Our latest donation was offered to us by <b>$username</b>. $description Many thanks to them!</p>""" +
                    """<p>Do you want to see <b>your name</b> featured as our latest supporter? <b><a href="https://ko-fi.com/elephantchess" target="_blank">You, too, can help us</a></b> and with just 4€ finance the platform for an entire day!</p>"""
        } else {
            ""
        }
    }
}
