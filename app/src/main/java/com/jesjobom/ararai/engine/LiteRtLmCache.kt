package com.jesjobom.ararai.engine

import java.io.File

internal fun prepareLiteRtLmCacheDir(
    appCacheRoot: File,
    onFailure: (Throwable) -> Unit = {},
): String? {
    val directory = appCacheRoot.resolve("litert_lm")
    return runCatching {
        check(directory.isDirectory || directory.mkdirs()) {
            "Unable to create LiteRT-LM cache directory: ${directory.absolutePath}"
        }
        directory.absolutePath
    }.onFailure(onFailure).getOrNull()
}
