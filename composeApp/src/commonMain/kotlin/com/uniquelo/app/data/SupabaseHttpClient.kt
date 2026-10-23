package com.uniquelo.app.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** Each platform supplies its own engine: OkHttp on Android, Darwin on iOS. */
expect fun platformHttpEngine(): HttpClientEngineFactory<*>

internal val SupabaseJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    coerceInputValues = true
}

fun createSupabaseHttpClient(): HttpClient = HttpClient(platformHttpEngine()) {
    expectSuccess = false

    install(ContentNegotiation) {
        json(SupabaseJson)
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 20_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 20_000
    }
    defaultRequest {
        header("apikey", SupabaseConfig.PUBLISHABLE_KEY)
        header(HttpHeaders.Authorization, "Bearer ${SupabaseConfig.PUBLISHABLE_KEY}")
        accept(ContentType.Application.Json)
    }
}
