package io.elephantchess.servicelayer.services

import io.elephantchess.config.AppConfig
import io.elephantchess.db.dao.codegen.tables.pojos.KofiEvent
import io.elephantchess.db.services.KofiEventDaoService
import io.elephantchess.db.services.KofiEventDaoService.Companion.EXAMPLE_EMAIL
import io.elephantchess.db.services.UserDaoService
import io.elephantchess.db.utils.toUtcInstant
import io.elephantchess.servicelayer.dto.kofi.KofiEventDto
import io.elephantchess.servicelayer.dto.kofi.LatestSupporter
import io.elephantchess.servicelayer.dto.lobby.GetSupportersResponse
import io.elephantchess.servicelayer.exceptions.ForbiddenException
import io.github.oshai.kotlinlogging.KLogger
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter.ISO_DATE_TIME
import java.util.*
import kotlin.time.Instant

class KofiService(
    private val kofiEventDaoService: KofiEventDaoService,
    private val userDaoService: UserDaoService,
    private val userCache: UserCache,
    appConfig: AppConfig,
    private val logger: KLogger
) {

    private val verificationToken by lazy { appConfig.kofiVerificationToken }

    suspend fun processEvent(kofiEvent: KofiEventDto) {
        logger.info { "processing Ko-fi event: transactionId=${kofiEvent.kofiTransactionId}, messageId=${kofiEvent.messageId}" }

        if (kofiEvent.verificationToken != verificationToken) {
            logger.warn { "invalid verification token for Ko-fi event" }
            throw ForbiddenException("invalid verification token for Ko-fi event")
        }

        if (kofiEvent.email == EXAMPLE_EMAIL) {
            logger.info { "this is a test event with email $EXAMPLE_EMAIL" }
        }

        val record = mapDtoToRecord(kofiEvent)

        val matchedByEmailUserId = kofiEvent.email
            ?.let { email -> userDaoService.findByEmail(email)?.id }

        if (matchedByEmailUserId != null) {
            logger.info { "matched Ko-fi event to user $matchedByEmailUserId by email" }
            record.matchedUserId = matchedByEmailUserId
            record.automaticallyMatched = true
        } else {
            val searchUsernames = listOfNotNull(kofiEvent.fromName, kofiEvent.discordUsername)
            val searchUsernamesStr = searchUsernames.joinToString(", ")
            val matchedByUsernames = userDaoService.searchForUsernames(searchUsernames)
            if (matchedByUsernames.size == 1) {
                val matchedUserId = matchedByUsernames.first()
                logger.info { "matched Ko-fi event to user $matchedUserId for search $searchUsernamesStr" }
                record.matchedUserId = matchedUserId
                record.automaticallyMatched = true
            } else if (matchedByUsernames.size > 1) {
                logger.info { "multiple user matches found for Ko-fi event: $matchedByUsernames for search $searchUsernamesStr" }
            } else {
                logger.info { "no user match found for Ko-fi event transactionId=${kofiEvent.kofiTransactionId}" }
            }
        }

        kofiEventDaoService.insert(record)
        logger.info { "successfully stored Ko-fi event with transactionId ${kofiEvent.kofiTransactionId}" }
    }

    suspend fun listLatestTippers(): GetSupportersResponse {
        return kofiEventDaoService
            .listLatestTippers(20)
            .map { record -> mapRecordToLatestSupporterDto(record) }
            .let { entries -> GetSupportersResponse(entries) }
    }

    suspend fun listLatestRecurrentSupporters(): GetSupportersResponse {
        return kofiEventDaoService
            .listLatestRecurrentSupporters(20)
            .map { record -> mapRecordToLatestSupporterDto(record) }
            .let { entries -> entries + hardcodedMonthlySupportToLatestSupporterDto() }
            .let { entries -> GetSupportersResponse(entries) }
    }

    suspend fun fetchLatestSupporter(): LatestSupporter? {
        val latestTipRecord = kofiEventDaoService.fetchLatestEvent()
            ?: return null

        return mapRecordToLatestSupporterDto(latestTipRecord)
    }

    private suspend fun mapRecordToLatestSupporterDto(record: KofiEvent): LatestSupporter {
        suspend fun displayNameForRecord(latestTipRecord: KofiEvent): String {
            return if (latestTipRecord.matchedUserId != null) {
                userCache.fetchUsernameOrDefault(latestTipRecord.matchedUserId!!)
            } else {
                latestTipRecord.fromName
            }
        }

        return LatestSupporter(
            userId = record.matchedUserId,
            username = displayNameForRecord(record),
            timestamp = record.timestamp.toEpochMilliseconds(),
            amount = record.amount.toDouble(),
            currency = record.currency,
            recurring = (record.transactionType == "Subscription")
        )
    }

    private suspend fun hardcodedMonthlySupportToLatestSupporterDto(): List<LatestSupporter> {
        return hardcodedMonthlySupporters.map { userId ->
            LatestSupporter(
                userId = userId,
                username = userCache.fetchUsernameOrDefault(userId),
                timestamp = hardcodedSubscriptionTime.toEpochMilliseconds(),
                amount = 100.0,
                currency = "EUR",
                recurring = true
            )
        }
    }

    private companion object {

        // benckx
        val hardcodedMonthlySupporters = listOf("LrCWS1Hs")
        val hardcodedSubscriptionTime: Instant = LocalDate.of(2023, 2, 11).atStartOfDay().toUtcInstant()

        fun mapDtoToRecord(dto: KofiEventDto): KofiEvent {
            val record = KofiEvent()
            record.kofiTransactionId = UUID.fromString(dto.kofiTransactionId)
            record.messageId = UUID.fromString(dto.messageId)
            record.timestamp = LocalDateTime.parse(dto.timestamp, ISO_DATE_TIME).toUtcInstant()
            record.transactionType = dto.type
            record.isPublic = dto.isPublic
            record.fromName = dto.fromName
            record.message = dto.message
            record.amount = BigDecimal(dto.amount)
            record.url = dto.url
            record.email = dto.email
            record.currency = dto.currency
            record.isSubscriptionPayment = dto.isSubscriptionPayment
            record.isFirstSubscriptionPayment = dto.isFirstSubscriptionPayment
            record.tierName = dto.tierName
            record.discordUsername = dto.discordUsername
            record.discordUserid = dto.discordUserid
            return record
        }

    }

}
