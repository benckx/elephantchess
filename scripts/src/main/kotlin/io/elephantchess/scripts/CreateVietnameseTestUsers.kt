package io.elephantchess.scripts

import io.elephantchess.db.services.UserDaoService
import io.elephantchess.servicelayer.dto.user.SignUpRequest
import io.elephantchess.servicelayer.services.UserService
import kotlinx.coroutines.runBlocking
import org.koin.core.component.inject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object CreateVietnameseTestUsers : KoinScriptInit() {

    private const val LOCALHOST_BASE_URL = "http://localhost:8080"
    private const val DEFAULT_PASSWORD = "password"

    private val userService by inject<UserService>()
    private val userDaoService by inject<UserDaoService>()

    private data class UserSeed(
        val username: String,
        val email: String,
    )

    private val usersToCreate = listOf(
        UserSeed(username = "nguyễn", email = "vietuser1@example.com"),
        UserSeed(username = "trần", email = "vietuser2@example.com"),
        UserSeed(username = "Đặng_123", email = "vietuser3@example.com"),
        UserSeed(username = "phạm-văn", email = "vietuser4@example.com"),
    )

    @JvmStatic
    fun main(args: Array<String>) {
        runBlocking {
            usersToCreate.forEach { user ->
                val exists = userDaoService.existsForUsername(user.username)
                if (!exists) {
                    val either = userService.signUp(SignUpRequest(user.username, user.email, DEFAULT_PASSWORD))
                    if (either.isRight()) {
                        println("Created user ${user.username}")
                        println(profileUrl(user.username))
                    } else {
                        val errorMessage = either.left().errors
                            .ifEmpty { listOf("User creation failed with no validation errors provided") }
                            .joinToString("; ")
                        println("ERROR: Failed to create user ${user.username}: $errorMessage")
                    }
                } else {
                    println("User ${user.username} already exists")
                    println(profileUrl(user.username))
                }
            }
        }
    }

    private fun profileUrl(username: String): String {
//        val encodedUsername = URLEncoder.encode(username, StandardCharsets.UTF_8).replace("+", "%20")
        return "$LOCALHOST_BASE_URL/@/$username"
    }

}
