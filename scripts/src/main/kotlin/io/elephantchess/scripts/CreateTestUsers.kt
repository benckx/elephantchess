package io.elephantchess.scripts

import io.elephantchess.scripts.users.TestUserSaver
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

object CreateTestUsers : KoinScriptInit() {

    @JvmStatic
    fun main(args: Array<String>) {
        runBlocking {
            val userSaver = TestUserSaver(nbr = 10)
            userSaver.execute()
            exitProcess(0)
        }
    }

}
