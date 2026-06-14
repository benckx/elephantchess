package io.elephantchess.db.utils

import io.elephantchess.db.dao.codegen.tables.pojos.*
import io.elephantchess.db.model.RatingUpdateRecord
import io.elephantchess.db.model.TimeControlRecord
import io.elephantchess.model.*
import io.elephantchess.model.GameEventType.*
import io.elephantchess.xiangqi.Color
import io.elephantchess.xiangqi.Variant
import org.apache.commons.lang3.BooleanUtils
import io.elephantchess.sevenkingdoms.Color as Color7k

// When possible, add the record ops here
// Otherwise they can go to the service-layer (if they depend on other modules)

fun OpeningPreCalculationCache.nextMove(): String {
    return moves.split(",").last()
}

fun OpeningPreCalculationCacheReferencePlayer.nextMove(): String {
    return moves.split(",").last()
}

val User.roles: List<UserRole>
    get() {
        val roles = mutableListOf<UserRole>()
        if (BooleanUtils.isTrue(hasRoleAdmin)) {
            roles.add(UserRole.ADMIN)
        }
        if (BooleanUtils.isTrue(hasRoleEditor)) {
            roles.add(UserRole.EDITOR)
        }
        return roles.toList()
    }

fun User.rating(timeControlCategory: TimeControlCategory, variant: Variant = Variant.XIANGQI): Int {
    return when (variant) {
        Variant.XIANGQI -> when (timeControlCategory) {
            TimeControlCategory.BULLET -> gameRatingBullet
            TimeControlCategory.BLITZ -> gameRatingBlitz
            TimeControlCategory.RAPID -> gameRatingRapid
            TimeControlCategory.CLASSICAL -> gameRatingClassical
            TimeControlCategory.SEVERAL_DAYS -> gameRatingSeveralDays
            TimeControlCategory.CORRESPONDENCE -> gameRatingCorrespondence
        }
        Variant.MANCHU -> when (timeControlCategory) {
            TimeControlCategory.BULLET -> gameRatingManchuBullet
            TimeControlCategory.BLITZ -> gameRatingManchuBlitz
            TimeControlCategory.RAPID -> gameRatingManchuRapid
            TimeControlCategory.CLASSICAL -> gameRatingManchuClassical
            TimeControlCategory.SEVERAL_DAYS -> gameRatingManchuSeveralDays
            TimeControlCategory.CORRESPONDENCE -> gameRatingManchuCorrespondence
        }
    }
}

fun Analysis.gameId(): GameId? {
    return if (gameId != null) {
        GameId(GameType.PVP, gameId)
    } else if (botGameId != null) {
        GameId(GameType.PVB, botGameId)
    } else if (referenceGameId != null) {
        GameId(GameType.DB, referenceGameId)
    } else {
        null
    }
}

fun MoveAnalysis.setGameId(gameId: GameId) {
    when (gameId.type) {
        GameType.DB -> this.refGameId = gameId.id
        GameType.PVP -> this.gameId = gameId.id
        GameType.PVB -> this.botGameId = gameId.id
    }
}

fun Color.asWinnerOutcome(): Outcome {
    return if (this == Color.RED) Outcome.RED_WINS else Outcome.BLACK_WINS
}

fun Color.asLoserOutcome(): Outcome {
    return this.reverse().asWinnerOutcome()
}

fun Game.includesUser(userId: String): Boolean =
    userIds().contains(userId)

fun Game.ratingUpdateRecord(): RatingUpdateRecord? {
    return if (isRated && inviterRatingTo != null && inviteeRatingTo != null) {
        RatingUpdateRecord(
            isRated = true,
            inviterRatingFrom = inviterRatingFrom,
            inviterRatingTo = inviterRatingTo,
            inviteeRatingFrom = inviteeRatingFrom,
            inviteeRatingTo = inviteeRatingTo,
        )
    } else {
        null
    }
}

fun Game.opponentOf(userId: String): String? {
    return if (inviter == userId) {
        invitee
    } else if (invitee == userId) {
        inviter
    } else {
        null
    }
}

fun Game.opponentColorOf(userId: String): Color? {
    return opponentOf(userId)?.let { opponentUserId -> userColor(opponentUserId) }
}

fun Game.colorToPlay(): Color {
    return if (this.currentHalfMoveIndex % 2 == 0) Color.RED else Color.BLACK
}

fun Game.userColor(userId: String): Color? {
    val isUserInviter = userId == this.inviter
    return if (isUserInviter) this.inviterColor else this.inviterColor?.reverse()
}

/**
 * @return userId who plays this color in the game
 */
fun Game.colorUser(color: Color): String? {
    return when (inviterColor) {
        null -> null
        color -> inviter
        else -> invitee
    }
}

fun Game.userOutcome(userId: String): UserOutcome? {
    return when (this.gameStatus) {
        CREATED, JOINED, DRAW_PROPOSED, DRAW_DECLINED -> null
        DRAW_ACCEPTED -> UserOutcome.DRAW
        RESIGNED, CHECKMATED, STALEMATED, FLAGGED, PERPETUAL_CHECKING -> {
            when (userColor(userId)) {
                Color.RED -> {
                    when (this.outcome) {
                        Outcome.RED_WINS -> UserOutcome.WIN
                        Outcome.BLACK_WINS -> UserOutcome.LOSS
                        Outcome.DRAW -> UserOutcome.DRAW
                        else -> null
                    }
                }

                Color.BLACK -> {
                    when (this.outcome) {
                        Outcome.RED_WINS -> UserOutcome.LOSS
                        Outcome.BLACK_WINS -> UserOutcome.WIN
                        Outcome.DRAW -> UserOutcome.DRAW
                        else -> null
                    }
                }

                else -> null
            }
        }

        else -> null
    }
}

fun Game.winnerUserId(): String? {
    return userIds().find { userId -> userOutcome(userId) == UserOutcome.WIN }
}

fun Game.timeControl(): TimeControlRecord? {
    return if (this.timeControlBase == null) {
        null
    } else {
        TimeControlRecord(
            mode = timeControlMode,
            base = timeControlBase,
            increment = timeControlIncrement ?: 0
        )
    }
}

fun Game.isTurnToPlay(userId: String): Boolean {
    return colorToPlay() == userColor(userId)
}

private fun Game.userIds(): List<String> =
    listOfNotNull(inviter, invitee)


fun Game.redUserId() = userIdByColor(Color.RED)

fun Game.blackUserId() = userIdByColor(Color.BLACK)

fun Game.redPlayerRating(): Int {
    return if (redUserId() == inviter) {
        inviterRatingFrom!!
    } else {
        inviteeRatingFrom!!
    }
}

fun Game.blackPlayerRating(): Int {
    return if (blackUserId() == inviter) {
        inviterRatingFrom!!
    } else {
        inviteeRatingFrom!!
    }
}

private fun Game.userIdByColor(color: Color): String? {
    return when (inviterColor) {
        color -> inviter
        else -> {
            if (gameStatus.isInProgress() || gameStatus.hasEnded()) {
                invitee
            } else {
                null
            }
        }
    }
}

fun BotGame.prettyEngineName() : String {
    return "$engine (${depth})"
}

fun SevenKingdomsGame.minColorPerPlayer(): Int {
    return when (minPlayers) {
        4 -> 1
        7 -> 1
        else -> (7 / this.minPlayers)
    }
}

fun SevenKingdomsGame.maxColorPerPlayer(): Int {
    return if (minPlayers == 7) {
        1
    } else {
        (7 / this.minPlayers) + 1
    }
}

fun SevenKingdomsGame.userIdOfColor(color: Color7k): String? {
    return when (color) {
        Color7k.WHITE -> playerWhite
        Color7k.RED -> playerRed
        Color7k.ORANGE -> playerOrange
        Color7k.BLUE -> playerBlue
        Color7k.GREEN -> playerGreen
        Color7k.PURPLE -> playerPurple
        Color7k.BLACK -> playerBlack
    }
}

fun SevenKingdomsGame.freeColors(): List<Color7k> {
    val colors = mutableListOf<Color7k>()
    if (playerWhite == null) {
        colors.add(Color7k.WHITE)
    }
    if (playerRed == null) {
        colors.add(Color7k.RED)
    }
    if (playerOrange == null) {
        colors.add(Color7k.ORANGE)
    }
    if (playerBlue == null) {
        colors.add(Color7k.BLUE)
    }
    if (playerGreen == null) {
        colors.add(Color7k.GREEN)
    }
    if (playerPurple == null) {
        colors.add(Color7k.PURPLE)
    }
    if (playerBlack == null) {
        colors.add(Color7k.BLACK)
    }
    return colors
}

fun SevenKingdomsGame.colorsOfUser(userId: String): List<Color7k> {
    val colors = mutableListOf<Color7k>()
    if (playerWhite == userId) colors.add(Color7k.WHITE)
    if (playerRed == userId) colors.add(Color7k.RED)
    if (playerOrange == userId) colors.add(Color7k.ORANGE)
    if (playerBlue == userId) colors.add(Color7k.BLUE)
    if (playerGreen == userId) colors.add(Color7k.GREEN)
    if (playerPurple == userId) colors.add(Color7k.PURPLE)
    if (playerBlack == userId) colors.add(Color7k.BLACK)
    return colors
}

fun SevenKingdomsGame.colorToUserIdMap(): Map<Color7k, String?> {
    return mapOf(
        Color7k.WHITE to playerWhite,
        Color7k.RED to playerRed,
        Color7k.ORANGE to playerOrange,
        Color7k.BLUE to playerBlue,
        Color7k.GREEN to playerGreen,
        Color7k.PURPLE to playerPurple,
        Color7k.BLACK to playerBlack,
    )
}
