package now.link.periphyllidae.di

import org.koin.dsl.module
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import org.koin.android.ext.koin.androidContext
import now.link.periphyllidae.platform.MediampPlayerFactory
import now.link.periphyllidae.platform.AndroidMediampPlayerFactory
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

    // MediaMP player factory for Android
    single<MediampPlayerFactory> { AndroidMediampPlayerFactory(androidContext()) }

    // System tray (not supported on Android)
    single<SystemTray> { SystemTrayManager() }

    // Music service manager for Android
    single { MusicServiceManager(androidContext()) }
}
