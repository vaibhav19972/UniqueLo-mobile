package com.uniquelo.app.data

import android.annotation.SuppressLint
import android.content.Context

/**
 * Set once in [com.uniquelo.app.MainActivity.onCreate] so common code can
 * resolve private storage. Only the application context is stored, so this
 * static reference cannot leak an Activity.
 */
@SuppressLint("StaticFieldLeak")
object AppContextHolder {
    @SuppressLint("StaticFieldLeak")
    lateinit var context: Context
}

internal actual fun platformAppFilesDir(): String? =
    runCatching { AppContextHolder.context.filesDir.absolutePath }.getOrNull()
