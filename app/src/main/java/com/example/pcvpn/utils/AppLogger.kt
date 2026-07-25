package com.example.pcvpn.utils

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel {
    INFO, SUCCESS, WARN, ERROR
}

data class LogEntry(
    val timestamp: String,
    val level: LogLevel,
    val tag: String,
    val message: String
)

object AppLogger {
    private const val MAX_LOGS = 200
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs

    private fun addLog(level: LogLevel, tag: String, message: String) {
        val timestamp = timeFormat.format(Date())
        val entry = LogEntry(timestamp, level, tag, message)

        // Вывод в системный Logcat Android
        when (level) {
            LogLevel.INFO, LogLevel.SUCCESS -> Log.i(tag, message)
            LogLevel.WARN -> Log.w(tag, message)
            LogLevel.ERROR -> Log.e(tag, message)
        }

        synchronized(this) {
            val currentList = _logs.value.toMutableList()
            if (currentList.size >= MAX_LOGS) {
                currentList.removeAt(0)
            }
            currentList.add(entry)
            _logs.value = currentList
        }
    }

    fun i(tag: String, message: String) = addLog(LogLevel.INFO, tag, message)
    fun s(tag: String, message: String) = addLog(LogLevel.SUCCESS, tag, message)
    fun w(tag: String, message: String) = addLog(LogLevel.WARN, tag, message)
    fun e(tag: String, message: String) = addLog(LogLevel.ERROR, tag, message)

    fun clear() {
        synchronized(this) {
            _logs.value = emptyList()
        }
    }

    fun getAllLogsText(): String {
        return _logs.value.joinToString("\n") { "[${it.timestamp}] [${it.level}] [${it.tag}]: ${it.message}" }
    }
}
