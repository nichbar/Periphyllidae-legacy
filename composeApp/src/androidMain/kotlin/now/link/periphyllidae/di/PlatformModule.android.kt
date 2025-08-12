package now.link.periphyllidae.di

import org.koin.dsl.module
import now.link.periphyllidae.platform.MusicPlayer
import org.koin.android.ext.koin.androidContext
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import now.link.periphyllidae.database.PeriphyllidaeDatabase

actual val platformModule = module {
    single<MusicPlayer> { MusicPlayer(androidContext()) }
    single<SqlDriver> { 
        AndroidSqliteDriver(PeriphyllidaeDatabase.Schema, androidContext(), "periphyllidae.db") 
    }
    single<PeriphyllidaeDatabase> { PeriphyllidaeDatabase(get()) }
}
