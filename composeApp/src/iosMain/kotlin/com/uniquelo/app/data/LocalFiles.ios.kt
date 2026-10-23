package com.uniquelo.app.data

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSHomeDirectory

@OptIn(ExperimentalForeignApi::class)
internal actual fun platformAppFilesDir(): String? = runCatching {
    val dir = NSHomeDirectory() + "/Library/Application Support/UniqueLo"
    NSFileManager.defaultManager.createDirectoryAtPath(
        path = dir,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
    )
    dir
}.getOrNull()
