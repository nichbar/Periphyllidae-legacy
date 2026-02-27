package now.link.periphyllidae.di

import org.koin.dsl.module
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import org.koin.android.ext.koin.androidContext
import now.link.periphyllidae.platform.PlaybackEngineFactory
import now.link.periphyllidae.platform.AndroidPlaybackEngineFactory
import now.link.periphyllidae.platform.SystemTray
import now.link.periphyllidae.platform.SystemTrayManager
import now.link.periphyllidae.service.MusicServiceManager
import now.link.periphyllidae.utils.PlatformStorage

actual val platformModule = module {
    single<Settings> {
        SharedPreferencesSettings(
            androidContext().getSharedPreferences("periphyllidae_settings", android.content.Context.MODE_PRIVATE)
        )
    }

    // Initialize PlatformStorage for Android with proper context
    single<PlatformStorage> {
        PlatformStorage.initialize(androidContext())
        PlatformStorage()
    }

    // Playback engine factory for Android
    single<PlaybackEngineFactory> { AndroidPlaybackEngineFactory(androidContext()) }

    // System tray (not supported on Android)
    single<SystemTray> { SystemTrayManager() }

    // Music service manager for Android
    single { MusicServiceManager(androidContext()) }
}
