package com.uniquelo.app.data

import android.content.Context

/** Set once in [com.uniquelo.app.MainActivity.onCreate] so common code can resolve private storage. */
object AppContextHolder {
    lateinit var context: Context
}

internal actual fun platformAppFilesDir(): String? =
    runCatching { AppContextHolder.context.filesDir.absolutePath }.getOrNull()
