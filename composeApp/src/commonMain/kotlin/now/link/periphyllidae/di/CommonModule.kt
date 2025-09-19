package now.link.periphyllidae.di

import org.koin.dsl.module
import com.russhwolf.settings.Settings
import kotlinx.serialization.json.Json
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import now.link.periphyllidae.repository.*
import now.link.periphyllidae.store.*
import now.link.periphyllidae.platform.MusicPlayer
import now.link.periphyllidae.viewmodels.*

val commonModule = module {
    // Settings and Configuration
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
    
    // Repositories
    single<SettingsRepository> { SettingsRepositoryImpl(get(), get()) }
    single<SubsonicRepository> { SubsonicRepositoryImpl(get(), get(), get()) }
    
    // Media Player (MediaMP) - Uses factory pattern to avoid expect/actual issues
    single<MusicPlayer> { MusicPlayer(get()) }
    
    // Global Stores (App-scoped state containers)
    single<AuthStore> { AuthStore(get(), get(), get()) }
    single<MusicStore> { MusicStore(get(), get(), get()) }
    single<AppStore> { AppStore(get()) }
    single<SearchStore> { SearchStore(get(), get()) } // SearchStore as singleton for now
    
    // Page-scoped ViewModels (Singleton pattern for state preservation across tab navigation)
    single<HomeViewModel> { HomeViewModel(get()) }
    factory<LoginViewModel> { LoginViewModel(get()) }
    factory<AddServerViewModel> { AddServerViewModel(get()) }
    single<SearchViewModel> { SearchViewModel(get(), get()) }
    factory<NowPlayingViewModel> { NowPlayingViewModel(get()) }
    single<SettingsViewModel> { SettingsViewModel(get(), get(), get()) }
    factory<ArtistDetailViewModel> { ArtistDetailViewModel(get(), get()) }
}

// Platform-specific modules will be defined in platform-specific files
expect val platformModule: org.koin.core.module.Module