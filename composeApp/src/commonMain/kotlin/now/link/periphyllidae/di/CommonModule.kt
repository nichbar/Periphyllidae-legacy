package now.link.periphyllidae.di

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import com.russhwolf.settings.Settings
import co.touchlab.kermit.Logger
import kotlinx.serialization.json.Json
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import now.link.periphyllidae.repository.*
import now.link.periphyllidae.store.*
import now.link.periphyllidae.platform.MusicPlayer

val commonModule = module {
    // Settings and Configuration
    single<Settings> { Settings() }
    single<Json> {
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
    }
    
    // HTTP Client
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
    
    // Logger
    single<Logger> { Logger.withTag("Periphyllidae") }
    
    // Repositories
    single<SettingsRepository> { SettingsRepositoryImpl(get(), get()) }
    single<SubsonicRepository> { SubsonicRepositoryImpl(get(), get(), get()) }
    
    // Stores/ViewModels
    viewModel { LoginStore(get(), get(), get()) }
    viewModel { HomeStore(get(), get(), get()) }
    viewModel { SearchStore(get(), get(), get()) }
    viewModel { NowPlayingStore(get(), get()) }
    viewModel { SettingsStore(get(), get()) }
}

// Platform-specific modules will be defined in platform-specific files
expect val platformModule: org.koin.core.module.Module
