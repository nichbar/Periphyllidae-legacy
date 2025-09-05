package now.link.periphyllidae.utils

import io.ktor.client.*
import io.ktor.client.request.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock

object NetworkUtils {
    
    /**
     * Measures latency to a server by making a simple HTTP HEAD request
     * @param serverUrl The server URL (e.g., "https://demo.navidrome.org")
     * @return Latency in milliseconds, or -1 if connection failed
     */
    suspend fun measureLatency(serverUrl: String): Long = withContext(Dispatchers.IO) {
        val httpClient = HttpClient()
        try {
            val startTime = Clock.System.now()
            
            val result = withTimeoutOrNull(5000) { // 5 second timeout
                httpClient.head(serverUrl)
            }
            
            val endTime = Clock.System.now()
            
            if (result != null) {
                endTime.toEpochMilliseconds() - startTime.toEpochMilliseconds()
            } else {
                -1L // Timeout
            }
        } catch (e: Exception) {
            LogManager.w("NetworkUtils", "Failed to measure latency to $serverUrl: ${e.message}")
            -1L
        } finally {
            httpClient.close()
        }
    }
    
    /**
     * Formats latency for display
     * @param latency Latency in milliseconds
     * @return Formatted string
     */
    fun formatLatency(latency: Long): String {
        return when {
            latency < 0 -> "Unavailable"
            latency < 100 -> "${latency}ms"
            latency < 1000 -> "${latency}ms"
            else -> "${latency / 1000}.${(latency % 1000) / 100}s"
        }
    }
}
