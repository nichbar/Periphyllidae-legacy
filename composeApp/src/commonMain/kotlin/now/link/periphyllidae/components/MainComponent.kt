package now.link.periphyllidae.components

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.value.Value
import kotlinx.serialization.Serializable

interface MainComponent {
    val tabStack: Value<ChildStack<*, TabChild>>
    
    fun selectTab(index: Int)
    fun onNavigateToNowPlaying()
    fun onNavigateToArtist(artistId: String)
    fun onNavigateToLogs()
    fun onNavigateToServerManagement()
    fun onNavigateToLogin()
    fun onLogout()
    
    sealed class TabChild {
        class HomeChild(val component: HomeTabComponent) : TabChild()
        class SearchChild(val component: SearchTabComponent) : TabChild()
        class PlaylistsChild(val component: PlaylistsTabComponent) : TabChild()
        class SettingsChild(val component: SettingsTabComponent) : TabChild()
    }
}

class DefaultMainComponent(
    componentContext: ComponentContext,
    private val onNavigateToNowPlaying: () -> Unit,
    private val onNavigateToArtist: (String) -> Unit,
    private val onNavigateToLogs: () -> Unit,
    private val onNavigateToServerManagement: () -> Unit,
    private val onNavigateToLogin: () -> Unit,
    private val onLogout: () -> Unit
) : MainComponent, ComponentContext by componentContext {
    
    private val tabNavigation = StackNavigation<TabConfig>()
    
    override val tabStack: Value<ChildStack<*, MainComponent.TabChild>> =
        childStack(
            source = tabNavigation,
            serializer = TabConfig.serializer(),
            initialConfiguration = TabConfig.Home,
            handleBackButton = false,
            childFactory = ::createTabChild,
        )
    
    private fun createTabChild(config: TabConfig, componentContext: ComponentContext): MainComponent.TabChild =
        when (config) {
            TabConfig.Home -> MainComponent.TabChild.HomeChild(
                DefaultHomeTabComponent(componentContext)
            )
            TabConfig.Search -> MainComponent.TabChild.SearchChild(
                DefaultSearchTabComponent(
                    componentContext = componentContext,
                    onNavigateToNowPlaying = onNavigateToNowPlaying,
                    onNavigateToArtist = onNavigateToArtist
                )
            )
            TabConfig.Playlists -> MainComponent.TabChild.PlaylistsChild(
                DefaultPlaylistsTabComponent(componentContext)
            )
            TabConfig.Settings -> MainComponent.TabChild.SettingsChild(
                DefaultSettingsTabComponent(
                    componentContext = componentContext,
                    onNavigateToLogs = onNavigateToLogs,
                    onNavigateToServerManagement = onNavigateToServerManagement,
                    onNavigateToLogin = onNavigateToLogin,
                    onLogout = onLogout
                )
            )
        }
    
    override fun selectTab(index: Int) {
        val config = when (index) {
            0 -> TabConfig.Home
            1 -> TabConfig.Search
            2 -> TabConfig.Playlists
            3 -> TabConfig.Settings
            else -> TabConfig.Home
        }
        tabNavigation.navigate(
            transformer = { listOf(config) },
            onComplete = { _, _ -> }
        )
    }
    
    override fun onNavigateToNowPlaying() {
        onNavigateToNowPlaying.invoke()
    }
    
    override fun onNavigateToArtist(artistId: String) {
        onNavigateToArtist.invoke(artistId)
    }
    
    override fun onNavigateToLogs() {
        onNavigateToLogs.invoke()
    }
    
    override fun onNavigateToServerManagement() {
        onNavigateToServerManagement.invoke()
    }
    
    override fun onNavigateToLogin() {
        onNavigateToLogin.invoke()
    }
    
    override fun onLogout() {
        onLogout.invoke()
    }
    
    @Serializable
    private sealed interface TabConfig {
        @Serializable
        data object Home : TabConfig
        
        @Serializable
        data object Search : TabConfig
        
        @Serializable
        data object Playlists : TabConfig
        
        @Serializable
        data object Settings : TabConfig
    }
}

// Tab components
interface HomeTabComponent

class DefaultHomeTabComponent(
    componentContext: ComponentContext
) : HomeTabComponent, ComponentContext by componentContext

interface SearchTabComponent {
    fun onNavigateToNowPlaying()
    fun onNavigateToArtist(artistId: String)
}

class DefaultSearchTabComponent(
    componentContext: ComponentContext,
    private val onNavigateToNowPlaying: () -> Unit,
    private val onNavigateToArtist: (String) -> Unit
) : SearchTabComponent, ComponentContext by componentContext {
    
    override fun onNavigateToNowPlaying() {
        onNavigateToNowPlaying.invoke()
    }
    
    override fun onNavigateToArtist(artistId: String) {
        onNavigateToArtist.invoke(artistId)
    }
}

interface PlaylistsTabComponent

class DefaultPlaylistsTabComponent(
    componentContext: ComponentContext
) : PlaylistsTabComponent, ComponentContext by componentContext

interface SettingsTabComponent {
    fun onNavigateToLogs()
    fun onNavigateToServerManagement()
    fun onNavigateToLogin()
    fun onLogout()
}

class DefaultSettingsTabComponent(
    componentContext: ComponentContext,
    private val onNavigateToLogs: () -> Unit,
    private val onNavigateToServerManagement: () -> Unit,
    private val onNavigateToLogin: () -> Unit,
    private val onLogout: () -> Unit
) : SettingsTabComponent, ComponentContext by componentContext {
    
    override fun onNavigateToLogs() {
        onNavigateToLogs.invoke()
    }
    
    override fun onNavigateToServerManagement() {
        onNavigateToServerManagement.invoke()
    }
    
    override fun onNavigateToLogin() {
        onNavigateToLogin.invoke()
    }
    
    override fun onLogout() {
        onLogout.invoke()
    }
}
