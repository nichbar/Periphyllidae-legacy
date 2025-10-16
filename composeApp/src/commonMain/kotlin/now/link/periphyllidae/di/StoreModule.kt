package now.link.periphyllidae.di

import now.link.periphyllidae.platform.MusicPlayer
import now.link.periphyllidae.store.AppStore
import now.link.periphyllidae.store.AuthStore
import now.link.periphyllidae.store.MusicStore
import org.koin.dsl.module

/**
 * Store module providing state management and media player dependencies.
 *
 * This module handles all state-related dependencies including:
 * - Authentication state management
 * - Music player state and queue management
 * - Global application state (theme, settings)
 * - Media player component using factory pattern
 */
val storeModule = module {

    /**
     * Media player component using factory pattern to avoid expect/actual issues.
     * Provides unified interface for cross-platform audio playback.
     */
    single<MusicPlayer> {
        MusicPlayer(get())
    }

    /**
     * Authentication store managing user authentication state and server configuration.
     * Handles login/logout flows and credential management.
     */
    single<AuthStore> {
        AuthStore(get(), get())
    }

    /**
     * Music store managing player state, playback queue, and current track information.
     * Provides reactive state management for all music playback functionality.
     */
    single<MusicStore> {
        MusicStore(get(), get(), get())
    }

    /**
     * Application store managing global app state including theme, settings, and preferences.
     * Provides centralized state for cross-component configuration.
     */
    single<AppStore> {
        AppStore(get())
    }
}