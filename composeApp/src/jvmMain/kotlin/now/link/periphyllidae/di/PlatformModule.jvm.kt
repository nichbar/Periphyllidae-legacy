package now.link.periphyllidae.di

import org.koin.dsl.module
import now.link.periphyllidae.platform.MusicPlayer
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.sqlite.NativeSqliteDriver
import now.link.periphyllidae.database.PeriphyllidaeDatabase

actual val platformModule = module {
    single<MusicPlayer> { MusicPlayer() }
    single<SqlDriver> { 
        NativeSqliteDriver(PeriphyllidaeDatabase.Schema, "periphyllidae.db") 
    }
    single<PeriphyllidaeDatabase> { PeriphyllidaeDatabase(get()) }
}
