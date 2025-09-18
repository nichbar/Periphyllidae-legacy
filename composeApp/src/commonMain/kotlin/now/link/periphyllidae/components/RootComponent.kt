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
        class ArtistDetailChild(val component: ArtistDetailComponent) : Child()
    }
}

@OptIn(DelicateDecomposeApi::class)
class DefaultRootComponent(
    componentContext: ComponentContext,
    private val settingsRepository: SettingsRepository
) : RootComponent, ComponentContext by componentContext {
    
    private val navigation = StackNavigation<Config>()
    
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
            is Config.ArtistDetail -> RootComponent.Child.ArtistDetailChild(artistDetailComponent(componentContext, config))
        }
    
    private fun loginComponent(componentContext: ComponentContext): LoginComponent =
        DefaultLoginComponent(
            componentContext = componentContext,
            onLoginSuccess = {
                navigation.push(Config.Main)
            }
        )
    
    private fun mainComponent(componentContext: ComponentContext): MainComponent =
        DefaultMainComponent(
            componentContext = componentContext,
            onNavigateToNowPlaying = {
                navigation.push(Config.NowPlaying)
            },
            onNavigateToArtist = { artistId ->
                navigation.push(Config.ArtistDetail(artistId = artistId))
            },
            onNavigateToLogs = {
                navigation.push(Config.Logs)
            },
            onNavigateToServerManagement = {
                navigation.push(Config.ServerManagement)
            },
            onNavigateToLogin = {
                navigation.push(Config.Login)
            },
            onLogout = {
                navigation.push(Config.Login)
            }
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
            onNavigateToAddServer = {
                navigation.push(Config.AddServer)
            }
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
            onNavigateToNowPlaying = {
                navigation.push(Config.NowPlaying)
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
        data class ArtistDetail(val artistId: String) : Config
    }
}

interface ServerManagementComponent {
    fun onNavigateBack()
    fun onNavigateToAddServer()
}

class DefaultServerManagementComponent(
    componentContext: ComponentContext,
    private val onNavigateBack: () -> Unit,
    private val onNavigateToAddServer: () -> Unit
) : ServerManagementComponent, ComponentContext by componentContext {
    
    override fun onNavigateBack() {
        onNavigateBack.invoke()
    }
    
    override fun onNavigateToAddServer() {
        onNavigateToAddServer.invoke()
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
