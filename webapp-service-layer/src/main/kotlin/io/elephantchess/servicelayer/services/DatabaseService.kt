package io.elephantchess.servicelayer.services

import io.elephantchess.db.dao.codegen.tables.pojos.ReferencePlayer
import io.elephantchess.db.dao.codegen.tables.pojos.ReferencePlayerProfileEditSource
import io.elephantchess.db.model.EntityIdAndNameRecord
import io.elephantchess.db.services.ReferenceEventDaoService
import io.elephantchess.db.services.ReferenceGameDaoService
import io.elephantchess.db.services.ReferencePlayerDaoService
import io.elephantchess.db.utils.toUtcInstant
import io.elephantchess.db.utils.toUtcLocalDate
import io.elephantchess.model.GameId
import io.elephantchess.model.GameType
import io.elephantchess.model.Outcome.*
import io.elephantchess.servicelayer.dto.AutocompleteResponse
import io.elephantchess.servicelayer.dto.ValidatedResponse
import io.elephantchess.servicelayer.dto.database.*
import io.elephantchess.servicelayer.dto.gamedata.GameMetadataDto
import io.elephantchess.servicelayer.exceptions.BadRequestException
import io.elephantchess.servicelayer.exceptions.NotFoundException
import io.elephantchess.utils.formatWithChineseName
import io.elephantchess.utils.generateNameVariations
import io.elephantchess.utils.isChineseText
import io.elephantchess.xiangqi.Board.Companion.validateFen
import io.elephantchess.xiangqi.Color
import io.github.oshai.kotlinlogging.KLogger
import io.github.reactivecircus.cache4k.Cache
import org.apache.commons.lang3.StringUtils
import org.apache.commons.validator.routines.UrlValidator
import java.time.LocalDate
import kotlin.time.Duration.Companion.hours

/**
 * What we call "database" in this context is a repository of games played in tournaments,
 * and not specifically the SQL database.
 */
class DatabaseService(
    private val referenceEventDaoService: ReferenceEventDaoService,
    private val referenceGameDaoService: ReferenceGameDaoService,
    private val referencePlayerDaoService: ReferencePlayerDaoService,
    private val userCache: UserCache,
    private val logger: KLogger
) {

    private val cacheDuration = 12.hours
    private val minOrDefaultLastPlayerUpdateDate = LocalDate.of(2026, 1, 15)

    private val playerIdToCanonicalNameCache =
        Cache
            .Builder<String, String>()
            .expireAfterWrite(cacheDuration)
            .build()

    private val playerIdToDisplayNameCache =
        Cache
            .Builder<String, String>()
            .expireAfterWrite(cacheDuration)
            .build()

    private val idToEventNameCache =
        Cache
            .Builder<String, String>()
            .expireAfterWrite(cacheDuration)
            .build()

    suspend fun listPlayersSuggestions(contains: String): AutocompleteResponse {
        suspend fun fetchSuggestions(): List<EntityIdAndNameRecord> {
            val players = referencePlayerDaoService.listPlayersStartingWith(contains, SUGGESTIONS_LIMIT)
            return players.ifEmpty {
                referencePlayerDaoService.listPlayersContaining(contains, SUGGESTIONS_LIMIT)
            }
        }

        return mapToAutocompleteResponse(entries = fetchSuggestions())
    }

    suspend fun listEventsSuggestions(contains: String): AutocompleteResponse {
        return mapToAutocompleteResponse(
            entries = referenceGameDaoService.listEvents(contains, SUGGESTIONS_LIMIT)
        )
    }

    suspend fun resolvePlayerByName(playerName: String): DatabasePlayer? {
        // try to find by current name first,
        // then fallback to historical names (case-sensitive, then case-insensitive)
        val referencePlayer = referencePlayerDaoService.findPlayerByAnyName(playerName)
            ?: referencePlayerDaoService.findPlayerByHistoricalName(playerName, ignoreCase = false)
            ?: referencePlayerDaoService.findPlayerByHistoricalName(playerName, ignoreCase = true)

        return referencePlayer?.let { record ->
            DatabasePlayer(
                id = record.id,
                canonicalName = record.canonicalName,
                chineseName = record.chineseName,
                gender = record.gender,
            )
        }
    }


    suspend fun search(
        dateStart: String?,
        dateEnd: String?,
        playerName: String?,
        playerIds: List<String>,
        playerColor: Color?,
        eventName: String?,
        eventIds: List<String>,
        fen: String?,
        offset: Int?,
        userId: String,
    ): ReferenceGameSearchResult {
        if (offset != null && offset < 0) {
            throw BadRequestException("Offset cannot be negative")
        }

        if (offset != null && offset > MAX_OFFSET) {
            throw BadRequestException(
                "Results cannot be greater than $MAX_OFFSET. " +
                        "You can make your query more specific. " +
                        "Or feel free to contact us if you would like a sample of game data."
            )
        }

        val dateStartParsed = dateStart?.let { LocalDate.parse(it) }
        val dateEndParsed = dateEnd?.let { LocalDate.parse(it) }

        validateFenParameter(fen)
        val abridgedFen = fen?.split(" ").orEmpty().firstOrNull()?.trim()

        val resolvedPlayerIds =
            if (playerIds.isEmpty() && !playerName.isNullOrBlank()) {
                listPlayersSuggestions(playerName).entries.map { entry -> entry.id }
            } else {
                playerIds
            }

        val resolvedEventIds =
            if (eventIds.isEmpty() && !eventName.isNullOrBlank()) {
                listEventsSuggestions(eventName).entries.map { entry -> entry.id }
            } else {
                eventIds
            }

        logger.debug {
            val toLog = mutableMapOf<String, String>()
            if (dateStartParsed != null) toLog["dateStart"] = dateStartParsed.toString()
            if (dateEndParsed != null) toLog["dateEnd"] = dateEndParsed.toString()
            if (!playerName.isNullOrBlank()) toLog["playerName"] = playerName
            if (resolvedPlayerIds.isNotEmpty()) toLog["playerIds"] = resolvedPlayerIds.joinToString(",")
            if (playerColor != null) toLog["playerColor"] = playerColor.toString()
            if (!eventName.isNullOrBlank()) toLog["eventName"] = eventName
            if (resolvedEventIds.isNotEmpty()) toLog["eventIds"] = resolvedEventIds.joinToString(",")
            if (!abridgedFen.isNullOrBlank()) toLog["fen"] = abridgedFen
            if (offset != null) toLog["offset"] = offset.toString()
            "searching reference games with ${toLog.entries.joinToString { "${it.key}=${it.value}" }}"
        }

        val entries =
            referenceGameDaoService
                .search(
                    dateStart = dateStartParsed,
                    dateEnd = dateEndParsed,
                    playerIds = resolvedPlayerIds,
                    playerColor = playerColor,
                    eventIds = resolvedEventIds,
                    fen = abridgedFen,
                    limit = SEARCH_RESULT_LIMIT,
                    offset = offset
                )
                .mapIndexed { i, record ->
                    GameMetadataDto(
                        gameId = GameId(GameType.DB, record.id),
                        lastUpdated = record.date?.atStartOfDay()?.toUtcInstant()?.toEpochMilliseconds(),
                        redPlayerId = record.redPlayer,
                        redPlayerName = playerIdToCanonicalName(record.redPlayer),
                        blackPlayerId = record.blackPlayer,
                        blackPlayerName = playerIdToCanonicalName(record.blackPlayer),
                        eventName = idToEventName(record.event),
                        finalFen = record.finalFen,
                        outcome = record.outcome,
                        analysisStatus = record.analysisStatus,
                        paginationOffset = (offset ?: 1) + i
                    )
                }

        referenceGameDaoService.persistSearch(
            userId = userId,
            searchStart = dateStartParsed,
            searchEnd = dateEndParsed,
            playerName = playerName,
            playerId = playerIds.firstOrNull(),
            playerColor = playerColor,
            eventName = eventName,
            eventId = eventIds.firstOrNull(),
            fen = fen,
            offset = offset,
            limit = SEARCH_RESULT_LIMIT,
            numberOfResults = entries.size
        )

        return ReferenceGameSearchResult(entries)
    }

    suspend fun playerIdToCanonicalName(playerId: String?): String? {
        if (playerId != null) {
            val result = playerIdToCanonicalNameCache.get(playerId) {
                referencePlayerDaoService.findPlayer(playerId)?.canonicalName ?: "<unknown>"
            }

            if (result.isNotBlank()) {
                return result
            }
        }

        return null
    }

    private suspend fun playerIdToDisplayName(playerId: String?): String? {
        if (playerId != null) {
            val result = playerIdToDisplayNameCache.get(playerId) {
                referencePlayerDaoService.findPlayer(playerId)?.let {
                    if (it.isVisible) {
                        formatWithChineseName(it.canonicalName, it.chineseName)
                    } else {
                        ""
                    }
                } ?: ""
            }

            if (result.isNotBlank()) {
                return result
            }
        }

        return null
    }

    private suspend fun idToEventName(eventId: String?): String? {
        if (eventId != null) {
            val result = idToEventNameCache.get(eventId) {
                referenceEventDaoService.findEventName(eventId).orEmpty()
            }

            if (result.isNotBlank()) {
                return result
            }
        }

        return null
    }

    suspend fun countAllGames(): CountAllGamesResponse {
        return CountAllGamesResponse(referenceGameDaoService.countAllGames())
    }

    suspend fun listAllEventsWithStats(
        limit: Int? = null,
        offset: Int? = null
    ): EventsListResponse {
        return referenceEventDaoService
            .listAllEventsWithStats(limit = limit, offset = offset)
            .map { record ->
                EventsListResponse.Entry(
                    id = record.id,
                    name = record.name,
                    date = record.date?.toString(),
                    maxRound = record.maxRound,
                    gameCount = record.gameCount
                )
            }
            .let { entries ->
                EventsListResponse(entries)
            }
    }

    suspend fun listAllPlayersWithStats(
        limit: Int? = null,
        offset: Int? = null
    ): PlayersListResponse {
        return referencePlayerDaoService
            .listPlayersByWins(limit = limit, offset = offset)
            .map { record ->
                PlayersListResponse.Entry(
                    playerId = record.playerId,
                    name = formatWithChineseName(record.canonicalName, record.chineseName),
                    slug = record.slug,
                    wins = record.wins,
                    draws = record.draws,
                    losses = record.losses,
                    totalGames = record.totalGames
                )
            }
            .let { entries ->
                PlayersListResponse(entries)
            }
    }

    suspend fun listPlayersForSiteMap(limit: Int): List<Pair<String, LocalDate>> {
        val records = mutableSetOf<EntityIdAndNameRecord>()
        records += referencePlayerDaoService.findMostEditedPlayers(limit)
        // allRecordIds contains current canonical names and past canonical names
        val allRecordIds = records.map { it.id }.distinct()
        val playersByCanonicalName =
            referencePlayerDaoService
                .findPlayersByCanonicalNames(allRecordIds)
                .associateBy { it.canonicalName }

        val playerIds = playersByCanonicalName.values.map { playerRecord -> playerRecord.id }
        val latestEditTimes = referencePlayerDaoService.findLatestEditTimes(playerIds)
        val latestGameDates = referenceGameDaoService.findLatestGameDates(playerIds)

        return records
            .toList()
            .filter { record ->
                // we only want current canonical names to avoid duplicates and redirections in the sitemap
                record.id in playersByCanonicalName.keys
            }
            .sortedBy { record -> record.id }
            .map { record ->
                val player = playersByCanonicalName[record.id]
                val lastModified = player
                    ?.let { player ->
                        val latestEdit = latestEditTimes[player.id]?.toUtcLocalDate()
                        val latestGame = latestGameDates[player.id]
                        listOfNotNull(latestEdit, latestGame).maxOrNull()
                    }
                    ?.coerceAtLeast(minOrDefaultLastPlayerUpdateDate)

                (player?.canonicalName ?: record.id) to
                        (lastModified ?: minOrDefaultLastPlayerUpdateDate)
            }
    }

    suspend fun listRandomFeaturedPlayers(): ListPlayersResponse {
        val limit = 10
        val records = mutableListOf<EntityIdAndNameRecord>()
        records += referencePlayerDaoService.findMostEditedPlayers(limit)
        records += referencePlayerDaoService.listByMostNumberOfGames(limit).map { record ->
            EntityIdAndNameRecord(
                id = record.canonicalName,
                name = formatWithChineseName(record.canonicalName, record.chineseName)
            )
        }

        return records
            .distinctBy { it.id }
            .shuffled()
            .take(limit)
            .map { record ->
                ListPlayersResponse.Entry(
                    slug = record.id.replace(" ", "_"),
                    displayName = record.name
                )
            }
            .let { entries ->
                ListPlayersResponse(entries)
            }
    }

    suspend fun listAllEditorsUsername(playerId: String): List<String> {
        return referencePlayerDaoService
            .listAllEditorsUserId(playerId)
            .map { editorId -> userCache.fetchUsernameOrDefault(editorId) }
    }

    suspend fun fetchPlayerEditHistory(playerId: String): DatabasePlayerVersionHistory {
        return referencePlayerDaoService
            .fetchEditHistory(playerId)
            .map { versionRecord ->
                val editorUsername = userCache.fetchUsernameOrDefault(versionRecord.editorId)
                DatabasePlayerProfileVersionHistoryEntry(
                    versionIndex = versionRecord.version,
                    editorUserId = versionRecord.editorId,
                    editorUsername = editorUsername,
                    versionTime = versionRecord.versionTime.toEpochMilliseconds(),
                    comment = versionRecord.comment,
                    canonicalName = versionRecord.canonicalName,
                    chineseName = versionRecord.chineseName,
                    gender = versionRecord.gender,
                    enabled = versionRecord.enabled
                )
            }.let { entries ->
                DatabasePlayerVersionHistory(entries)
            }
    }

    /**
     * If version is specified, fetch that version even if it's disabled
     * If version is null, fetch the latest enabled version
     */
    suspend fun fetchPlayerEdit(playerId: String, version: Int?): DatabasePlayerProfileEdit {
        val profileVersion =
            if (version != null) {
                referencePlayerDaoService.findProfileEdit(playerId, version)
            } else {
                referencePlayerDaoService.findLatestEnabledProfileEdit(playerId)
            }

        return if (profileVersion != null) {
            DatabasePlayerProfileEdit(
                playerId = profileVersion.playerId,
                canonicalName = profileVersion.canonicalName,
                chineseName = profileVersion.chineseName,
                gender = profileVersion.gender,
                profileText = profileVersion.profile,
                sources = fetchPlayerProfileSources(playerId, profileVersion.version),
                editComment = profileVersion.comment,
                enabled = profileVersion.enabled
            )
        } else {
            val player = referencePlayerDaoService.findPlayer(playerId)
                ?: throw NotFoundException("player $playerId not found")

            DatabasePlayerProfileEdit(
                playerId = player.id,
                canonicalName = player.canonicalName,
                chineseName = player.chineseName,
                gender = player.gender,
                profileText = null,
                sources = emptyList(),
                editComment = null,
                enabled = true
            )
        }
    }

    suspend fun findPossibleDuplicatedPlayers(playerId: String): ListPlayersResponse {
        return findPossibleDuplicates(playerId)
            .map { player ->
                ListPlayersResponse.Entry(
                    slug = player.canonicalName.replace(" ", "_"),
                    displayName = formatWithChineseName(player.canonicalName, player.chineseName)
                )
            }
            .let { entries ->
                ListPlayersResponse(entries)
            }
    }

    suspend fun fetchPlayerGameStats(playerId: String): PlayerGameStatsResponse {
        // fetch stats for player alone
        val playerStats = referencePlayerDaoService.fetchGameStats(playerId)

        // find possible duplicates
        val duplicates = findPossibleDuplicates(playerId)

        // fetch stats including duplicates if any exist
        val statsWithDuplicates =
            if (duplicates.isNotEmpty()) {
                val allPlayerIds = listOf(playerId) + duplicates.map { it.id }
                referencePlayerDaoService.fetchGameStats(allPlayerIds)
            } else {
                null
            }

        return PlayerGameStatsResponse(
            player = PlayerGameStatsResponse.PlayerStats(
                redWins = playerStats.redWins,
                redLosses = playerStats.redLosses,
                redDraws = playerStats.redDraws,
                blackWins = playerStats.blackWins,
                blackLosses = playerStats.blackLosses,
                blackDraws = playerStats.blackDraws
            ),
            withDuplicates = statsWithDuplicates?.let { stats ->
                PlayerGameStatsResponse.PlayerStats(
                    redWins = stats.redWins,
                    redLosses = stats.redLosses,
                    redDraws = stats.redDraws,
                    blackWins = stats.blackWins,
                    blackLosses = stats.blackLosses,
                    blackDraws = stats.blackDraws
                )
            }
        )
    }

    private suspend fun findPossibleDuplicates(playerId: String): List<ReferencePlayer> {
        val player = referencePlayerDaoService.findPlayer(playerId)
            ?: throw NotFoundException("player $playerId not found")

        val nameVariations = generateNameVariations(player.sourceName, player.canonicalName)
        return referencePlayerDaoService.searchByNames(names = nameVariations, excludedPlayerId = player.id)
    }

    suspend fun fetchEventName(eventId: String): String? {
        return referenceEventDaoService.findEventName(eventId)
    }

    suspend fun fetchEvent(eventId: String): Event {
        val event = referenceEventDaoService.fetchEventById(eventId)
            ?: throw NotFoundException("event $eventId not found")

        val gameRecords = referenceGameDaoService.findByEventId(eventId)
        val allPlayerIds = gameRecords.flatMap { listOf(it.redPlayer, it.blackPlayer) }.distinct()
        val scores = mutableMapOf<String, Int>()
        allPlayerIds.forEach { playerId ->
            scores[playerId] = 0
        }

        gameRecords.forEach { game ->
            when (game.outcome) {
                RED_WINS -> scores[game.redPlayer] = scores[game.redPlayer]!! + 2
                BLACK_WINS -> scores[game.blackPlayer] = scores[game.blackPlayer]!! + 2
                DRAW -> {
                    scores[game.redPlayer] = scores[game.redPlayer]!! + 1
                    scores[game.blackPlayer] = scores[game.blackPlayer]!! + 1
                }
            }
        }

        val gameDtos = gameRecords.map { game ->
            Event.Game(
                id = game.id,
                redPlayerId = game.redPlayer,
                redPlayerName = playerIdToDisplayName(game.redPlayer),
                redPlayerSlug = playerIdToCanonicalName(game.redPlayer)?.replace(" ", "_"),
                blackPlayerId = game.blackPlayer,
                blackPlayerName = playerIdToDisplayName(game.blackPlayer),
                blackPlayerSlug = playerIdToCanonicalName(game.blackPlayer)?.replace(" ", "_"),
                outcome = game.outcome,
                round = game.round,
                date = game.date,
                finalFen = game.finalFen
            )
        }

        return Event(
            id = event.id,
            name = event.name,
            scores = scores,
            games = gameDtos
        )
    }

    suspend fun updatePlayerProfile(
        request: DatabasePlayerUpdateRequest,
        userId: String
    ): ValidatedResponse<Unit> {
        val errors = mutableListOf<String>()

        if (StringUtils.isBlank(request.canonicalName)) {
            errors += "Canonical Name cannot be empty"
        }

        if (request.chineseName != null && !isChineseText(request.chineseName)) {
            errors += "Chinese Name must contain only Chinese characters"
        }

        if (request.gender != null) {
            if (!(request.gender == "M" || request.gender == "F")) {
                errors += "Gender must be either 'M' or 'F'"
            }
        }

        if (StringUtils.isBlank(request.profileText)) {
            errors += "Profile Text cannot be empty"
        }

        if (request.sources.isEmpty()) {
            errors += "At least one source must be provided"
        } else {
            val urlValidator = UrlValidator(arrayOf("http", "https"))
            request.sources.forEachIndexed { index, source ->
                if (StringUtils.isBlank(source.url)) {
                    errors += "Source ${index + 1}: URL cannot be empty"
                } else if (!urlValidator.isValid(source.url)) {
                    errors += "Source ${index + 1}: URL is not valid"
                }
                if (StringUtils.isBlank(source.title)) {
                    errors += "Source ${index + 1}: Title cannot be empty"
                }
            }
        }

        if (StringUtils.isBlank(request.editComment)) {
            errors += "Edit Comment cannot be empty"
        }

        if (request.canonicalName != null &&
            referencePlayerDaoService.existsByCanonicalName(
                request.canonicalName.trim(),
                excludedPlayerId = request.playerId
            )
        ) {
            errors += "Canonical Name '${request.canonicalName.trim()}' is already used"
        }

        val latestVersion = referencePlayerDaoService.findLatestEnabledProfileEdit(request.playerId)
        val sources = fetchPlayerProfileSources(request.playerId, null)

        // check if all fields are exactly the same as current version
        val canonicalNameUnchanged = request.canonicalName?.trim() == latestVersion?.canonicalName
        val profileTextUnchanged = request.profileText == latestVersion?.profile
        val sourcesUnchanged = areSourcesEqual(request.sources, sources)
        val chineseNameUnchanged = request.chineseName == latestVersion?.chineseName
        val genderUnchanged = request.gender == latestVersion?.gender

        if (canonicalNameUnchanged && profileTextUnchanged && sourcesUnchanged && chineseNameUnchanged && genderUnchanged) {
            errors += "No changes detected"
        }

        if (errors.isNotEmpty()) {
            return ValidatedResponse.Invalid(errors)
        }

        // convert the DTO sources to POJO sources for the DAO
        val sourceRecords = request.sources.map { source ->
            ReferencePlayerProfileEditSource().apply {
                sourceIndex = source.index
                url = source.url
                title = source.title
            }
        }

        // create the new profile version in a transaction
        referencePlayerDaoService.createNewProfileVersion(
            playerId = request.playerId,
            canonicalName = request.canonicalName!!.trim(),
            chineseName = request.chineseName?.trim(),
            gender = request.gender,
            profileText = request.profileText!!,
            sources = sourceRecords,
            editorId = userId,
            comment = request.editComment.trim()
        )

        return ValidatedResponse.Valid(Unit)
    }

    suspend fun listUserSearches(userId: String, beforeTime: Long?): MyDbSearchesResponse {
        val beforeInstant = beforeTime?.let { kotlin.time.Instant.fromEpochMilliseconds(it) }
        val records = referenceGameDaoService.listUserSearches(userId, beforeInstant, 30)
        val resolvedPlayers = records
            .mapNotNull { entry -> entry.playerName }
            .distinct()
            .mapNotNull { name -> resolvePlayerByName(name)?.let { name to it } }
            .toMap()

        return records
            .map { record ->
                val prettyName =
                    record.playerName?.let { name ->
                        resolvedPlayers[name]
                            ?.let { player -> formatWithChineseName(player.canonicalName, player.chineseName) }
                            ?: name
                    }

                MyDbSearchesResponse.Entry(
                    queryId = record.queryId,
                    updateTime = record.updateTime.toEpochMilliseconds(),
                    playerName = prettyName,
                    playerColor = record.playerColor,
                    eventName = record.eventName,
                    searchStart = record.searchStart?.toString(),
                    searchEnd = record.searchEnd?.toString(),
                    fen = record.fen,
                    numberOfResults = record.numberOfResults ?: 0,
                )
            }
            .let { mapped -> MyDbSearchesResponse(mapped) }
    }

    suspend fun findEditedPlayersLatestVersions(userId: String): ListUserEdits {
        return referencePlayerDaoService
            .findEditedPlayersByEditorId(userId)
            .map { entry ->
                ListUserEdits.Entry(
                    playerId = entry.playerId,
                    playerCanonicalName = entry.canonicalName,
                    playerChineseName = entry.chineseName,
                    version = entry.version,
                    latestEditorId = entry.editorId,
                    latestEditorUsername = userCache.fetchUsernameOrDefault(entry.editorId),
                    latestComment = entry.comment,
                    latestEditTimestamp = entry.versionTime.toEpochMilliseconds(),
                    enabled = entry.enabled
                )
            }
            .let { entries ->
                ListUserEdits(entries)
            }
    }

    private suspend fun fetchPlayerProfileSources(playerId: String, version: Int?): List<DatabasePlayerProfileSource> {
        val sources = if (version == null) {
            referencePlayerDaoService.findLatestEnabledProfileSources(playerId)
        } else {
            referencePlayerDaoService.findProfileSourcesByVersion(playerId, version)
        }

        return sources.map { source ->
            DatabasePlayerProfileSource(
                index = source.sourceIndex,
                url = source.url,
                title = source.title
            )
        }
    }

    private companion object {

        const val SUGGESTIONS_LIMIT = 50
        const val SEARCH_RESULT_LIMIT = 20
        const val MAX_OFFSET = 500

        fun mapToAutocompleteResponse(entries: List<EntityIdAndNameRecord>): AutocompleteResponse {
            return AutocompleteResponse(entries.map { entry ->
                AutocompleteResponse.Entry(
                    id = entry.id,
                    name = entry.name
                )
            })
        }

        fun validateFenParameter(fen: String?) {
            try {
                fen?.let { fen ->
                    val trimmed = fen.trim()
                    val toValidate = if (!trimmed.contains(" ")) "$trimmed w - - 0 1" else trimmed
                    validateFen(toValidate)
                }
            } catch (e: IllegalArgumentException) {
                throw BadRequestException(e.message ?: "Invalid FEN")
            }
        }

        fun areSourcesEqual(
            requestSources: List<DatabasePlayerProfileSource>,
            currentSources: List<DatabasePlayerProfileSource>
        ): Boolean {
            if (requestSources.size != currentSources.size) {
                return false
            }

            // Sort by index to ensure consistent comparison
            val sortedRequest = requestSources.sortedBy { it.index }
            val sortedCurrent = currentSources.sortedBy { it.index }

            return sortedRequest.zip(sortedCurrent).all { (req, curr) ->
                req.url == curr.url && req.title == curr.title
            }
        }

    }

}
