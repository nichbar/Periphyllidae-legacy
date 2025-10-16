package now.link.periphyllidae.di

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.dsl.module

/**
 * Network module providing HTTP client and JSON serialization configuration.
 *
 * This module handles all network-related dependencies including:
 * - JSON serialization configuration
 * - HTTP client with proper plugins
 * - Logging configuration for network requests
 */
val networkModule = module {

    /**
     * JSON serialization configuration with common settings
     * for API communication with Subsonic-compatible servers.
     */
    single<Json> {
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
    }

    /**
     * Configured HTTP client with content negotiation and logging.
     * Uses the configured JSON serializer for API communication.
     */
    single<HttpClient> {
        HttpClient {
            install(ContentNegotiation) {
                json(get<Json>())
            }
            install(Logging) {
                level = LogLevel.INFO
            }
        }
    }
}