package io.elephantchess.webapp.koin

import com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.elephantchess.servicelayer.utils.ops.registerInjectables
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun webAppKoinModule(eagerAllowed: Boolean): Module =
    module {
        includes(htmlRenderingModule())
        single(named("wsJsonMapper")) {
            JsonMapper
                .builder()
                .addModule(KotlinModule.Builder().build())
                .configure(INDENT_OUTPUT, false)
                .build()
        }

        // page renderers annotated with @KoinSingleton
        registerInjectables(eagerAllowed, "io.elephantchess.webapp")
    }
