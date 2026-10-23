package com.uniquelo.app.data

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.cio.CIO

actual fun platformHttpEngine(): HttpClientEngineFactory<*> = CIO
