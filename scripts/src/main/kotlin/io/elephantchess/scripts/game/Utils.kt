package io.elephantchess.scripts.game

import io.elephantchess.db.dao.codegen.Tables.USER
import io.elephantchess.db.services.UserDaoService
import io.elephantchess.db.utils.awaitExecute
import io.elephantchess.db.utils.fixed
import io.elephantchess.servicelayer.dto.user.SignUpRequest
import io.elephantchess.servicelayer.services.UserService
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.kotlin.coroutines.transactionCoroutine
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.random.Random.Default.nextInt

object Utils : KoinComponent {

    private val userService by inject<UserService>()
    private val userDaoService by inject<UserDaoService>()
    private val dslContext by inject<DSLContext>()

    suspend fun createTestUserIfNotExists(i: Int, randomizeRatings: Boolean = true) {
        val username = "test$i"
        val email = "test$i@gmail.com"
        val password = "password$i"
        val userExists = userDaoService.existsForEmail(email)

        if (!userExists) {
            val response = userService.signUp(SignUpRequest(username, email, password))
            println("added user $username")
            if (randomizeRatings) {
                randomizeRatings(dslContext, response.right().userId)
            }
            println("updated ratings")
        } else {
            println("user $username already exists")
        }
    }

    private suspend fun randomizeRatings(dslContext: DSLContext, userId: String) {
        dslContext.transactionCoroutine { cfg ->
            DSL
                .using(cfg)
                .update(USER).set(USER.GAME_RATING_BULLET.fixed(), nextInt(500, 2500))
                .set(USER.GAME_RATING_BLITZ.fixed(), nextInt(500, 2500))
                .set(USER.GAME_RATING_RAPID.fixed(), nextInt(500, 2500))
                .set(USER.GAME_RATING_CLASSICAL.fixed(), nextInt(500, 2500))
                .set(USER.GAME_RATING_SEVERAL_DAYS.fixed(), nextInt(500, 2500))
                .set(USER.GAME_RATING_CORRESPONDENCE.fixed(), nextInt(500, 2500))
                .where(USER.ID.eq(userId))
                .awaitExecute()
        }
    }

}
