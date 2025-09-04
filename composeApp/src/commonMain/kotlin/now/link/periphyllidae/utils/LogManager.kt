package now.link.periphyllidae.utils

import now.link.periphyllidae.models.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import co.touchlab.kermit.Logger
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Manages in-memory logging for the application
 * - Captures Kermit logs in memory for KMP compatibility
 * - Maintains a circular buffer of logs
 * - Provides real-time updates via StateFlow
 */
object LogManager {

    private const val TAG = "LogManager"
    private const val MAX_LOG_ENTRIES = 10000

    private val logEntries = ConcurrentLinkedQueue<Log>()
    private val _logEntriesFlow = MutableStateFlow<List<Log>>(emptyList())
    val logEntriesFlow: StateFlow<List<Log>> = _logEntriesFlow.asStateFlow()

    private var isLogEnabled = false
    private var settingsRepository: now.link.periphyllidae.repository.SettingsRepository? = null

    /**
     * Initialize the LogManager with settings repository
     */
    fun initialize(settingsRepo: now.link.periphyllidae.repository.SettingsRepository) {
        settingsRepository = settingsRepo
        isLogEnabled = settingsRepo.getLogEnabled()

        // Add initialization log
        if (isLogEnabled) {
            addLogEntry(Log.LogLevel.INFO, TAG, "LogManager initialized, logging enabled")
        }
    }

    /**
     * Enable or disable logging
     */
    fun setLogEnabled(enabled: Boolean) {
        isLogEnabled = enabled
        settingsRepository?.saveLogEnabled(enabled)

        if (enabled) {
            addLogEntry(Log.LogLevel.INFO, TAG, "In-memory logging enabled")
        } else {
            addLogEntry(Log.LogLevel.INFO, TAG, "In-memory logging disabled")
        }
    }

    /**
     * Check if logging is enabled
     */
    fun isLogEnabled(): Boolean = isLogEnabled

    /**
     * Add a log entry to the in-memory store
     */
    private fun addLogEntry(
        level: Log.LogLevel,
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        if (!isLogEnabled) return

        val entry = Log(
            level = level,
            tag = tag,
            message = message,
            throwable = throwable
        )

        synchronized(logEntries) {
            // Add new entry
            logEntries.offer(entry)

            // Remove oldest entries if we exceed the limit
            while (logEntries.size > MAX_LOG_ENTRIES) {
                logEntries.poll()
            }

            // Update StateFlow with current entries
            _logEntriesFlow.value = logEntries.toList()
        }
    }

    /**
     * Get all current log entries
     */
    fun getAllLogEntries(): List<Log> {
        return synchronized(logEntries) {
            logEntries.toList()
        }
    }

    /**
     * Clear all log entries
     */
    fun clearLogs() {
        synchronized(logEntries) {
            logEntries.clear()
            _logEntriesFlow.value = emptyList()
        }
        if (isLogEnabled) {
            addLogEntry(Log.LogLevel.INFO, TAG, "Log entries cleared")
        }
    }

    /**
     * Get log entries count
     */
    fun getLogCount(): Int = logEntries.size

    // Wrapper methods for Kermit Logger calls to capture in memory

    fun v(tag: String, msg: String) {
        Logger.withTag(tag).v(msg)
        addLogEntry(Log.LogLevel.VERBOSE, tag, msg)
    }

    fun v(tag: String, msg: String, tr: Throwable) {
        Logger.withTag(tag).v(tr) { msg }
        addLogEntry(Log.LogLevel.VERBOSE, tag, msg, tr)
    }

    fun d(tag: String, msg: String) {
        Logger.withTag(tag).d(msg)
        addLogEntry(Log.LogLevel.DEBUG, tag, msg)
    }

    fun d(tag: String, msg: String, tr: Throwable) {
        Logger.withTag(tag).d(tr) { msg }
        addLogEntry(Log.LogLevel.DEBUG, tag, msg, tr)
    }

    fun i(tag: String, msg: String) {
        Logger.withTag(tag).i(msg)
        addLogEntry(Log.LogLevel.INFO, tag, msg)
    }

    fun i(tag: String, msg: String, tr: Throwable) {
        Logger.withTag(tag).i(tr) { msg }
        addLogEntry(Log.LogLevel.INFO, tag, msg, tr)
    }

    fun w(tag: String, msg: String) {
        Logger.withTag(tag).w(msg)
        addLogEntry(Log.LogLevel.WARN, tag, msg)
    }

    fun w(tag: String, msg: String, tr: Throwable) {
        Logger.withTag(tag).w(tr) { msg }
        addLogEntry(Log.LogLevel.WARN, tag, msg, tr)
    }

    fun w(tag: String, tr: Throwable) {
        Logger.withTag(tag).w(tr) { tr.message ?: "Exception" }
        addLogEntry(Log.LogLevel.WARN, tag, tr.message ?: "Exception", tr)
    }

    fun e(tag: String, msg: String) {
        Logger.withTag(tag).e(msg)
        addLogEntry(Log.LogLevel.ERROR, tag, msg)
    }

    fun e(tag: String, msg: String, tr: Throwable) {
        Logger.withTag(tag).e(tr) { msg }
        addLogEntry(Log.LogLevel.ERROR, tag, msg, tr)
    }

    fun wtf(tag: String, msg: String) {
        Logger.withTag(tag).e(msg) // Kermit doesn't have wtf, use error
        addLogEntry(Log.LogLevel.ASSERT, tag, msg)
    }

    fun wtf(tag: String, tr: Throwable) {
        Logger.withTag(tag).e(tr) { tr.message ?: "WTF Exception" }
        addLogEntry(Log.LogLevel.ASSERT, tag, tr.message ?: "WTF Exception", tr)
    }

    fun wtf(tag: String, msg: String, tr: Throwable) {
        Logger.withTag(tag).e(tr) { msg }
        addLogEntry(Log.LogLevel.ASSERT, tag, msg, tr)
    }
}
