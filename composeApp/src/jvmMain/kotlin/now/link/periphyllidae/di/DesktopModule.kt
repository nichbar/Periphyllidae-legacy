package now.link.periphyllidae.di

import org.koin.dsl.module
import now.link.periphyllidae.platform.SystemTray
import now.link.periphyllidae.platform.SystemTrayManager

/**
 * Desktop-specific dependency injection module
 */
val desktopModule = module {
    single<SystemTray> { SystemTrayManager() }
}