package com.dvait.base.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileLogger {
    private var logFile: File? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private const val MAX_FILE_SIZE = 5 * 1024 * 1024 // 5MB

    fun init(context: Context) {
        val dir = File(context.getExternalFilesDir(null), "logs")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        logFile = File(dir, "app_logs.txt")
        i("FileLogger", "Logger initialized at ${logFile?.absolutePath}")
    }

    private fun appendLog(level: String, tag: String, message: String, t: Throwable? = null) {
        val file = logFile ?: return
        
        val time = dateFormat.format(Date())
        val exceptionStr = t?.let { "\n" + Log.getStackTraceString(it) } ?: ""
        val logLine = "$time [$level] $tag: $message$exceptionStr\n"

        scope.launch {
            try {
                if (file.exists() && file.length() > MAX_FILE_SIZE) {
                    val oldFile = File(file.parent, "app_logs_old.txt")
                    if (oldFile.exists()) oldFile.delete()
                    file.renameTo(oldFile)
                }
                
                FileWriter(file, true).use {
                    it.append(logLine)
                }
            } catch (e: Exception) {
                Log.e("FileLogger", "Failed to write log to file", e)
            }
        }
    }

    fun d(tag: String, message: String) {
        Log.d(tag, message)
        appendLog("D", tag, message)
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        appendLog("I", tag, message)
    }

    fun w(tag: String, message: String, t: Throwable? = null) {
        Log.w(tag, message, t)
        appendLog("W", tag, message, t)
    }

    fun e(tag: String, message: String, t: Throwable? = null) {
        Log.e(tag, message, t)
        appendLog("E", tag, message, t)
    }
}
