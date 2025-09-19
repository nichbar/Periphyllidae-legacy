package now.link.periphyllidae.repository

import io.ktor.client.*
import kotlinx.serialization.json.Json
import now.link.periphyllidae.models.ServerConfig

/**
 * Factory for creating server-specific SubsonicRepository instances
 * Each server gets its own immutable repository instance with pre-computed auth params
 */
class SubsonicRepositoryFactory(
    private val httpClient: HttpClient,
    private val json: Json,
    private val settingsRepository: SettingsRepository
) {
    
    /**
     * Creates a new repository instance configured for the specific server
     * Each instance is immutable and thread-safe
     */
    fun createRepository(serverConfig: ServerConfig): SubsonicRepository {
        return ServerSpecificSubsonicRepository(
            serverConfig = serverConfig,
            httpClient = httpClient,
            json = json,
            settingsRepository = settingsRepository
        )
    }
}
