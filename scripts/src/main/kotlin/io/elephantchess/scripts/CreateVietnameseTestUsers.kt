package io.elephantchess.scripts

import io.elephantchess.db.services.UserDaoService
import io.elephantchess.servicelayer.dto.user.SignUpRequest
import io.elephantchess.servicelayer.services.UserService
import kotlinx.coroutines.runBlocking
import org.koin.core.component.inject
import kotlin.system.exitProcess

object CreateVietnameseTestUsers : KoinScriptInit() {

    private val userService by inject<UserService>()
    private val userDaoService by inject<UserDaoService>()

    private data class UserSeed(
        val username: String,
        val email: String,
        val password: String,
    )

    private val usersToCreate = listOf(
        UserSeed(username = "nguyễn", email = "vietuser1@gmail.com", password = "password1"),
        UserSeed(username = "trần", email = "vietuser2@gmail.com", password = "password2"),
        UserSeed(username = "Đặng_123", email = "vietuser3@gmail.com", password = "password3"),
        UserSeed(username = "phạm-văn", email = "vietuser4@gmail.com", password = "password4"),
    )

    @JvmStatic
    fun main(args: Array<String>) {
        runBlocking {
            usersToCreate.forEach { user ->
                val exists = userDaoService.existsForUsername(user.username)
                if (!exists) {
                    val either = userService.signUp(SignUpRequest(user.username, user.email, user.password))
                    println(either)
                } else {
                    println("user ${user.username} already exists")
                }
                println("http://localhost:8080/@/${user.username}")
            }
            exitProcess(0)
        }
    }

}
