package now.link.periphyllidae.models

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Represents a single log entry with timestamp, level, tag, and message
 */
data class Log(
    val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    val level: LogLevel,
    val tag: String,
    val message: String,
    val throwable: Throwable? = null
) {
    
    enum class LogLevel(val priority: Int, val shortName: String) {
        VERBOSE(2, "V"),
        DEBUG(3, "D"),
        INFO(4, "I"),
        WARN(5, "W"),
        ERROR(6, "E"),
        ASSERT(7, "A")
    }
    
    /**
     * Format timestamp to readable string
     */
    fun getFormattedTime(): String {
        val instant = Instant.fromEpochMilliseconds(timestamp)
        val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        
        val hour = localDateTime.hour.toString().padStart(2, '0')
        val minute = localDateTime.minute.toString().padStart(2, '0')
        val second = localDateTime.second.toString().padStart(2, '0')
        val millis = (timestamp % 1000).toString().padStart(3, '0')
        
        return "$hour:$minute:$second.$millis"
    }
    
    /**
     * Format the complete log message for display
     */
    fun getFormattedMessage(): String {
        val throwableMsg = if (throwable != null) {
            "\n${throwable.stackTraceToString()}"
        } else {
            ""
        }
        return "${getFormattedTime()} ${level.shortName}/$tag: $message$throwableMsg"
    }
}
