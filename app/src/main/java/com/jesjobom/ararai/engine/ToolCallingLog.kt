package com.jesjobom.ararai.engine

import android.util.Log

internal object ToolCallingLog {
    const val TAG = "ArarAI.ToolCalling"

    fun debug(message: String) {
        runCatching { Log.d(TAG, message) }
    }

    fun info(message: String) {
        runCatching { Log.i(TAG, message) }
    }

    fun warning(message: String) {
        runCatching { Log.w(TAG, message) }
    }

    fun error(message: String, throwable: Throwable) {
        runCatching { Log.e(TAG, message, throwable) }
    }
}
