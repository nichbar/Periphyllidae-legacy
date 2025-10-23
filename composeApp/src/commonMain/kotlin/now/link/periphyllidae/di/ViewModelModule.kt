package now.link.periphyllidae.di

import now.link.periphyllidae.viewmodels.*
import org.koin.dsl.module

/**
 * ViewModel module providing all screen-specific ViewModels.
 *
 * This module handles all ViewModel dependencies using factory scope
 * for proper lifecycle management. Each ViewModel is created on demand
 * and properly disposed when no longer needed.
 *
 * ViewModels follow MVI pattern with proper state management and
 * dependency injection of required stores and repositories.
 */
val viewModelModule = module {

    /**
     * Home screen ViewModel managing main content display and navigation.
     * Depends on AuthStore for authentication state and MusicStore for content.
     */
    factory<HomeViewModel> {
        HomeViewModel(get(), get())
    }

    /**
     * Login screen ViewModel handling user authentication and server connection.
     * Depends on AuthStore for authentication state management.
     */
    factory<LoginViewModel> {
        LoginViewModel(get())
    }

    /**
     * Add Server screen ViewModel for configuring new Subsonic servers.
     * Depends on AuthStore for server configuration management.
     */
    factory<AddServerViewModel> {
        AddServerViewModel(get())
    }

    /**
     * Search screen ViewModel managing search functionality and results.
     * Now manages its own search state directly (merged from SearchStore).
     * Depends on AuthStore for authentication and SubsonicRepositoryManager for data access.
     */
    factory<SearchViewModel> {
        SearchViewModel(get(), get())
    }

    /**
     * Now Playing screen ViewModel for current playback interface.
     * Depends on MusicStore for current player state and controls.
     */
    factory<NowPlayingViewModel> {
        NowPlayingViewModel(get())
    }

    /**
     * Settings screen ViewModel managing application preferences and configuration.
     * Depends on AuthStore for authentication, AppStore for theme/settings,
     * and SettingsRepository for persistent preferences.
     */
    factory<SettingsViewModel> {
        SettingsViewModel(get(), get(), get())
    }

    /**
     * Artist Detail screen ViewModel displaying artist information and discography.
     * Depends on AuthStore for authentication and SubsonicRepositoryManager for artist data.
     */
    factory<ArtistDetailViewModel> {
        ArtistDetailViewModel(get(), get())
    }

    /**
     * Album Detail screen ViewModel showing album tracks and information.
     * Depends on AuthStore for authentication and SubsonicRepositoryManager for album data.
     */
    factory<AlbumDetailViewModel> {
        AlbumDetailViewModel(get(), get())
    }

    /**
     * Cache Management screen ViewModel for cache statistics and controls.
     * Depends on MusicPlayer for cache operations and AppStore for settings.
     */
    factory<CacheManagementViewModel> {
        CacheManagementViewModel(get(), get())
    }
}