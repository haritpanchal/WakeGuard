package com.haritpanchal.wakeguard

import android.content.Context
import android.util.Log
import java.time.Instant

object AppLog {
    private const val FILE_NAME = "wakeguard-poc.log"

    fun append(context: Context, message: String) {
        val line = "${Instant.now()} $message"
        Log.i("WakeGuard", message)
        synchronized(this) {
            context.applicationContext
                .openFileOutput(FILE_NAME, Context.MODE_APPEND)
                .bufferedWriter()
                .use { writer ->
                    writer.appendLine(line)
                }
        }
    }
}
