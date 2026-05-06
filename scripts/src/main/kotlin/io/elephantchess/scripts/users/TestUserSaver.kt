package io.elephantchess.scripts.users

import io.elephantchess.servicelayer.dto.user.SignUpRequest
import io.elephantchess.servicelayer.services.UserService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class TestUserSaver(private val nbr: Int = 10) : KoinComponent {

    private val userService by inject<UserService>()

    suspend fun execute() {
        (1..nbr).forEach { i ->
            val signUpRequest = SignUpRequest("test$i", "test$i@gmail.com", "password$i")
            val either = userService.signUp(signUpRequest)
            println(either)
        }
    }

}
