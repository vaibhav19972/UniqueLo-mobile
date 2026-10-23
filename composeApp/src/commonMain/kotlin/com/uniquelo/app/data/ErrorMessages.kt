package com.uniquelo.app.data

/**
 * Single home for turning repository/IO failures into customer-readable copy
 * (previously duplicated in the catalog, checkout and newsletter paths).
 * Keep the exact strings: tests assert them.
 */
fun readableMessage(cause: String?): String {
    val raw = cause.orEmpty()
    return when {
        raw.contains("Failed to connect", ignoreCase = true) ||
            raw.contains("timeout", ignoreCase = true) ||
            raw.contains("Unable to resolve host", ignoreCase = true) ||
            raw.contains("ConnectException", ignoreCase = true) ->
            "Couldn't reach the atelier. Check your connection and try again."

        raw.contains("row-level security", ignoreCase = true) ->
            "The order book is not accepting reservations right now."

        raw.isBlank() -> "Something went wrong. Please try again."
        else -> raw.take(180)
    }
}

fun Throwable.readableMessage(): String = readableMessage(message)
