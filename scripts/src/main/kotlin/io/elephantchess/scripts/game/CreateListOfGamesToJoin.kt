package io.elephantchess.scripts.game

import io.elephantchess.db.services.UserDaoService
import io.elephantchess.model.TimeControlMode
import io.elephantchess.model.UserType.AUTHENTICATED
import io.elephantchess.scripts.KoinScriptInit
import io.elephantchess.scripts.game.Utils.createTestUserIfNotExists
import io.elephantchess.servicelayer.dto.game.CreateGameRequest
import io.elephantchess.servicelayer.exceptions.BadRequestException
import io.elephantchess.servicelayer.model.UserId
import io.elephantchess.servicelayer.services.PlayerVsPlayerGameService
import io.elephantchess.xiangqi.Color
import io.elephantchess.xiangqi.Variant
import kotlinx.coroutines.runBlocking
import org.koin.core.component.inject
import kotlin.random.Random
import kotlin.system.exitProcess
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val testUsers = 10
private const val weirdTimeControlsCount = 20

private val standardTimeControls: List<Pair<Duration, Duration?>> =
    listOf(
        1.minutes to null,
        1.minutes to 1.seconds,
        2.minutes to 1.seconds,
        3.minutes to null,
        3.minutes to 2.seconds,
        5.minutes to 2.seconds,
        5.minutes to 5.seconds,
        5.minutes to null,
        10.minutes to null,
        10.minutes to 5.seconds,
        15.minutes to 10.seconds,
        20.minutes to 10.seconds,
        30.minutes to null,
        30.minutes to 20.seconds,
        45.minutes to 15.seconds,
        1.hours to null,
        1.hours to 30.seconds,
        1.hours to 1.minutes,
        2.hours to null,
        1.days to null,
        2.days to null,
        3.days to null,
        7.days to null,
    )

private fun Random.generateWeirdTimeControls(): List<Pair<Duration, Duration?>> =
    List(weirdTimeControlsCount) {
        if (nextBoolean()) {
            randomWeirdLiveTimeControl()
        } else {
            randomWeirdCorrespondenceTimeControl()
        }
    }

private fun Random.randomWeirdLiveTimeControl(): Pair<Duration, Duration?> {
    val base =
        when (nextInt(4)) {
            0 -> nextInt(45, 180).seconds
            1 -> nextInt(3, 25).minutes + nextInt(60).seconds
            2 -> nextInt(25, 90).minutes + nextInt(60).seconds
            else -> nextInt(1, 6).hours + nextInt(60).minutes + nextInt(60).seconds
        }

    val increment =
        when (nextInt(6)) {
            0 -> null
            1 -> nextInt(1, 4).seconds
            2 -> nextInt(4, 11).seconds
            3 -> nextInt(11, 31).seconds
            4 -> nextInt(31, 90).seconds
            else -> nextInt(1, 4).minutes
        }

    return base to increment
}

private fun Random.randomWeirdCorrespondenceTimeControl(): Pair<Duration, Duration?> {
    val base = nextInt(1, 15).days + nextInt(24).hours + nextInt(60).minutes
    return base to null
}

object CreateListOfGamesToJoin : KoinScriptInit() {

    private val userDaoService by inject<UserDaoService>()
    private val pvpGameService by inject<PlayerVsPlayerGameService>()

    @JvmStatic
    fun main(args: Array<String>): Unit = runBlocking {
        // set up users
        (1..testUsers).forEach { i -> createTestUserIfNotExists(i) }

        // create games
        val rnd = Random.Default
        val weirdTimeControls = rnd.generateWeirdTimeControls()
        val timeControls = standardTimeControls + weirdTimeControls

        repeat(50) {
            val i = rnd.nextInt(testUsers) + 1
            val user = userDaoService.findByEmail("test$i@protonmail.com")!!
            val hasColor = rnd.nextBoolean()
            val color = if (hasColor) null else Color.random()
            val timeControl = timeControls.random(rnd)
            val timeControlMode =
                if (timeControl.first.inWholeSeconds < 1.days.inWholeSeconds) {
                    TimeControlMode.GAME_TIME
                } else {
                    TimeControlMode.MOVE_TIME
                }
            val variant = if (rnd.nextInt(100) < 20) Variant.MANCHU else Variant.XIANGQI

            val request =
                CreateGameRequest(
                    inviterColor = color,
                    isRated = rnd.nextBoolean(),
                    timeControlBase = timeControl.first.inWholeSeconds.toInt(),
                    timeControlIncrement = timeControl.second?.inWholeSeconds?.toInt(),
                    timeControlMode = timeControlMode,
                    allowGuests = true,
                    alwaysVisibleInLobby = true,
                    privateInvite = false,
                    variant = variant
                )

            try {
                val createGameResponse = pvpGameService.createGame(UserId(AUTHENTICATED, user.id), request)
                val gameId = createGameResponse.gameId
                println("created game $gameId by ${user.handle}")
            } catch (e: BadRequestException) {
                if (e.message == "You already have 3 pending games with the same settings") {
                    println("skipped creating game by ${user.handle} with time control ${timeControl.first} + ${timeControl.second ?: "0"} because of too many pending games with the same settings")
                } else {
                    println("failed to create game by ${user.handle} with time control ${timeControl.first} + ${timeControl.second ?: "0"}: ${e.message}")
                }
            }
        }

        exitProcess(0)
    }
}
