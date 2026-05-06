package io.elephantchess.scripts

abstract class KoinScriptInit(
    appProfile: String? = null,
    configurationLocation: String? = null
) : KoinScript {

    init {
        initKoin(
            appProfile = appProfile,
            configurationLocation = configurationLocation
        )
    }

}
