package now.link.periphyllidae.di

import now.link.periphyllidae.repository.SettingsRepository
import now.link.periphyllidae.repository.SettingsRepositoryImpl
import now.link.periphyllidae.repository.SubsonicRepositoryFactory
import now.link.periphyllidae.repository.SubsonicRepositoryManager
import org.koin.dsl.module

/**
 * Data module providing repositories and data access layer dependencies.
 *
 * This module handles all data-related dependencies including:
 * - Settings persistence and retrieval
 * - Subsonic repository factory and management
 * - Data source abstraction layer
 */
val dataModule = module {

    /**
     * Repository for application settings and preferences.
     * Provides persistence layer using platform-specific Settings implementation.
     */
    single<SettingsRepository> {
        SettingsRepositoryImpl(get(), get())
    }

    /**
     * Factory for creating Subsonic repository instances.
     * Manages repository creation with proper dependency injection.
     */
    single<SubsonicRepositoryFactory> {
        SubsonicRepositoryFactory(get(), get(), get())
    }

    /**
     * Manager for Subsonic repository lifecycle and access.
     * Provides centralized access to the current repository instance.
     */
    single<SubsonicRepositoryManager> {
        SubsonicRepositoryManager(get())
    }
}