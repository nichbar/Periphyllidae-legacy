package now.link.periphyllidae.components

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.DelicateDecomposeApi
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.push
import com.arkivanov.decompose.value.Value
import kotlinx.serialization.Serializable
import now.link.periphyllidae.repository.SettingsRepository

/**
 * Sealed class representing all possible navigation events in the app
 */
sealed class Nav {
    data object Main : Nav()
    data object NowPlaying : Nav()
    data object Logs : Nav()
    data object ServerManagement : Nav()
    data object AddServer : Nav()
    data object CacheManagement : Nav()
    data object Login : Nav()
    data object Logout : Nav()
    data class ArtistDetail(val artistId: String) : Nav()
    data class AlbumDetail(val albumId: String) : Nav()
}

interface RootComponent {
    val stack: Value<ChildStack<*, Child>>
    
    fun onBackClicked(toIndex: Int)
    
    sealed class Child {
        class LoginChild(val component: LoginComponent) : Child()
        class MainChild(val component: MainComponent) : Child()
        class NowPlayingChild(val component: NowPlayingComponent) : Child()
        class LogsChild(val component: LogsComponent) : Child()
        class ServerManagementChild(val component: ServerManagementComponent) : Child()
        class AddServerChild(val component: AddServerComponent) : Child()
        class CacheManagementChild(val component: CacheManagementComponent) : Child()
        class ArtistDetailChild(val component: ArtistDetailComponent) : Child()
        class AlbumDetailChild(val component: AlbumDetailComponent) : Child()
    }
}

@OptIn(DelicateDecomposeApi::class)
class DefaultRootComponent(
    componentContext: ComponentContext,
    private val settingsRepository: SettingsRepository
) : RootComponent, ComponentContext by componentContext {
    
    private val navigation = StackNavigation<Config>()

    /**
     * Central navigation handler using sealed class Nav
     */
    private val nav: (Nav) -> Unit = { event ->
        when (event) {
            is Nav.Main -> navigation.push(Config.Main)
            is Nav.NowPlaying -> navigation.push(Config.NowPlaying)
            is Nav.ArtistDetail -> navigation.push(Config.ArtistDetail(artistId = event.artistId))
            is Nav.AlbumDetail -> navigation.push(Config.AlbumDetail(albumId = event.albumId))
            is Nav.Logs -> navigation.push(Config.Logs)
            is Nav.ServerManagement -> navigation.push(Config.ServerManagement)
            is Nav.AddServer -> navigation.push(Config.AddServer)
            is Nav.CacheManagement -> navigation.push(Config.CacheManagement)
            is Nav.Login -> navigation.push(Config.Login)
            is Nav.Logout -> navigation.push(Config.Login)
        }
    }
    
    override val stack: Value<ChildStack<*, RootComponent.Child>> =
        childStack(
            source = navigation,
            serializer = Config.serializer(),
            initialConfiguration = if (settingsRepository.getIsAuthenticated()) Config.Main else Config.Login,
            handleBackButton = true,
            childFactory = ::child,
        )

    private fun child(config: Config, componentContext: ComponentContext): RootComponent.Child =
        when (config) {
            is Config.Login -> RootComponent.Child.LoginChild(loginComponent(componentContext))
            is Config.Main -> RootComponent.Child.MainChild(mainComponent(componentContext))
            is Config.NowPlaying -> RootComponent.Child.NowPlayingChild(nowPlayingComponent(componentContext))
            is Config.Logs -> RootComponent.Child.LogsChild(logsComponent(componentContext))
            is Config.ServerManagement -> RootComponent.Child.ServerManagementChild(serverManagementComponent(componentContext))
            is Config.AddServer -> RootComponent.Child.AddServerChild(addServerComponent(componentContext))
            is Config.CacheManagement -> RootComponent.Child.CacheManagementChild(cacheManagementComponent(componentContext))
            is Config.ArtistDetail -> RootComponent.Child.ArtistDetailChild(artistDetailComponent(componentContext, config))
            is Config.AlbumDetail -> RootComponent.Child.AlbumDetailChild(albumDetailComponent(componentContext, config))
        }
    
    private fun loginComponent(componentContext: ComponentContext): LoginComponent =
        DefaultLoginComponent(
            componentContext = componentContext,
            nav = nav,
        )
    
    private fun mainComponent(componentContext: ComponentContext): MainComponent =
        DefaultMainComponent(
            componentContext = componentContext,
            nav = nav
        )
    
    private fun nowPlayingComponent(componentContext: ComponentContext): NowPlayingComponent =
        DefaultNowPlayingComponent(
            componentContext = componentContext,
            onNavigateBack = {
                navigation.pop()
            }
        )
    
    private fun logsComponent(componentContext: ComponentContext): LogsComponent =
        DefaultLogsComponent(
            componentContext = componentContext,
            onNavigateBack = {
                navigation.pop()
            }
        )
    
    private fun serverManagementComponent(componentContext: ComponentContext): ServerManagementComponent =
        DefaultServerManagementComponent(
            componentContext = componentContext,
            onNavigateBack = {
                navigation.pop()
            },
            nav = nav
        )
    
    private fun addServerComponent(componentContext: ComponentContext): AddServerComponent =
        DefaultAddServerComponent(
            componentContext = componentContext,
            onServerAdded = {
                navigation.pop() // Go back to ServerManagement
            },
            onNavigateBack = {
                navigation.pop()
            }
        )
    
    private fun artistDetailComponent(componentContext: ComponentContext, config: Config.ArtistDetail): ArtistDetailComponent =
        DefaultArtistDetailComponent(
            componentContext = componentContext,
            artistId = config.artistId,
            onNavigateBack = {
                navigation.pop()
            },
            nav = nav
        )

    private fun albumDetailComponent(componentContext: ComponentContext, config: Config.AlbumDetail): AlbumDetailComponent =
        DefaultAlbumDetailComponent(
            componentContext = componentContext,
            albumId = config.albumId,
            onNavigateBack = {
                navigation.pop()
            },
            nav = nav
        )

    private fun cacheManagementComponent(componentContext: ComponentContext): CacheManagementComponent =
        DefaultCacheManagementComponent(
            componentContext = componentContext,
            onNavigateBack = {
                navigation.pop()
            }
        )

    override fun onBackClicked(toIndex: Int) {
        navigation.pop()
    }

    @Serializable
    private sealed interface Config {
        @Serializable
        data object Login : Config

        @Serializable
        data object Main : Config

        @Serializable
        data object NowPlaying : Config

        @Serializable
        data object Logs : Config

        @Serializable
        data object ServerManagement : Config

        @Serializable
        data object AddServer : Config

        @Serializable
        data object CacheManagement : Config

        @Serializable
        data class ArtistDetail(val artistId: String) : Config

        @Serializable
        data class AlbumDetail(val albumId: String) : Config
    }
}

interface ServerManagementComponent {
    fun onNavigateBack()
    fun onNavigateTo(nav: Nav)
}

class DefaultServerManagementComponent(
    componentContext: ComponentContext,
    private val onNavigateBack: () -> Unit,
    private val nav: (Nav) -> Unit
) : ServerManagementComponent, ComponentContext by componentContext {
    
    override fun onNavigateBack() {
        onNavigateBack.invoke()
    }
    
    override fun onNavigateTo(nav: Nav) {
        this.nav(nav)
    }
}

interface AddServerComponent {
    fun onServerAdded()
    fun onNavigateBack()
}

class DefaultAddServerComponent(
    componentContext: ComponentContext,
    private val onServerAdded: () -> Unit,
    private val onNavigateBack: () -> Unit
) : AddServerComponent, ComponentContext by componentContext {
    
    override fun onServerAdded() {
        onServerAdded.invoke()
    }
    
    override fun onNavigateBack() {
        onNavigateBack.invoke()
    }
}
