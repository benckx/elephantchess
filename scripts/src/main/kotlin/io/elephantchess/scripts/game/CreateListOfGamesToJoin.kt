package io.elephantchess.scripts.game

import io.elephantchess.db.services.UserDaoService
import io.elephantchess.model.TimeControlMode
import io.elephantchess.model.UserType.AUTHENTICATED
import io.elephantchess.scripts.KoinScriptInit
import io.elephantchess.scripts.game.Utils.createTestUserIfNotExists
import io.elephantchess.servicelayer.dto.game.CreateGameRequest
import io.elephantchess.servicelayer.model.UserId
import io.elephantchess.servicelayer.services.PlayerVsPlayerGameService
import io.elephantchess.xiangqi.Color
import kotlinx.coroutines.runBlocking
import org.koin.core.component.inject
import java.util.*
import kotlin.system.exitProcess
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val testUsers = 10

object CreateListOfGamesToJoin : KoinScriptInit() {

    private val userDaoService by inject<UserDaoService>()
    private val pvpGameService by inject<PlayerVsPlayerGameService>()

    @JvmStatic
    fun main(args: Array<String>): Unit = runBlocking {

        // set up users
        (1..testUsers).forEach { i -> createTestUserIfNotExists(i) }

        val timeControls: List<Pair<Duration, Duration?>> =
            listOf(
                1.minutes to null,
                1.minutes to 1.seconds,
                2.minutes to 1.seconds,
                3.minutes to null,
                5.minutes to 2.seconds,
                5.minutes to null,
                10.minutes to null,
                15.minutes to 10.seconds,
                30.minutes to null,
                1.hours to null,
                1.hours to 1.minutes,
                2.hours to null,
                1.days to null,
                3.days to null,
                7.days to null,
            )

        // create games
        val rnd = Random()
        repeat(30) {
            val i = Random().nextInt(testUsers) + 1
            val user = userDaoService.findByUserName("test$i")!!
            val hasColor = rnd.nextBoolean()
            val color = if (hasColor) null else Color.random()
            val timeControl = timeControls.random()
            val timeControlMode =
                if (timeControl.first.inWholeSeconds < 1.days.inWholeSeconds) {
                    TimeControlMode.GAME_TIME
                } else {
                    TimeControlMode.MOVE_TIME
                }

            val request =
                CreateGameRequest(
                    inviterColor = color,
                    isRated = rnd.nextBoolean(),
                    timeControlBase = timeControl.first.inWholeSeconds.toInt(),
                    timeControlIncrement = timeControl.second?.inWholeSeconds?.toInt(),
                    timeControlMode = timeControlMode,
                    allowGuests = true,
                    alwaysVisibleInLobby = true,
                    privateInvite = false
                )

            val createGameResponse = pvpGameService.createGame(UserId(AUTHENTICATED, user.id), request)
            val gameId = createGameResponse.gameId
            println("created game $gameId by ${user.handle}")
        }

        exitProcess(0)
    }
}
