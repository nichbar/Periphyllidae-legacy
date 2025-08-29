package now.link.periphyllidae.di

import org.koin.dsl.module
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import org.koin.android.ext.koin.androidContext
import now.link.periphyllidae.platform.MediampPlayerFactory
import now.link.periphyllidae.platform.AndroidMediampPlayerFactory

actual val platformModule = module {
    single<Settings> { 
        SharedPreferencesSettings(
            androidContext().getSharedPreferences("periphyllidae_settings", android.content.Context.MODE_PRIVATE)
        )
    }
    
    // MediaMP player factory for Android
    single<MediampPlayerFactory> { AndroidMediampPlayerFactory(androidContext()) }
}
