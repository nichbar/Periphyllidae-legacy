package now.link.periphyllidae.components

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.bringToFront
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.value.Value
import kotlinx.serialization.Serializable

interface MainComponent: NavigationComponent {
    val tabStack: Value<ChildStack<*, TabChild>>

    fun selectTab(index: Int)

    sealed class TabChild {
        class HomeChild(val component: HomeTabComponent) : TabChild()
        class FindChild(val component: FindTabComponent) : TabChild()
        class PlaylistsChild(val component: PlaylistsTabComponent) : TabChild()
        class SettingsChild(val component: SettingsTabComponent) : TabChild()
    }
}

class DefaultMainComponent(
    componentContext: ComponentContext,
    private val nav: (Nav) -> Unit
) : MainComponent, ComponentContext by componentContext {

    private val tabNavigation = StackNavigation<TabConfig>()
    private var currentTab: TabConfig = TabConfig.Home

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
                DefaultHomeTabComponent(
                    componentContext = componentContext,
                    nav = nav
                )
            )
            TabConfig.Find -> MainComponent.TabChild.FindChild(
                DefaultFindTabComponent(
                    componentContext = componentContext,
                    nav = nav
                )
            )
            TabConfig.Playlists -> MainComponent.TabChild.PlaylistsChild(
                DefaultPlaylistsTabComponent(componentContext)
            )
            TabConfig.Settings -> MainComponent.TabChild.SettingsChild(
                DefaultSettingsTabComponent(
                    componentContext = componentContext,
                    nav = nav
                )
            )
        }

    override fun selectTab(index: Int) {
        val config = when (index) {
            0 -> TabConfig.Home
            1 -> TabConfig.Find
            2 -> TabConfig.Playlists
            3 -> TabConfig.Settings
            else -> TabConfig.Home
        }

        // Only navigate if switching to a different tab
        if (config != currentTab) {
            currentTab = config
            tabNavigation.bringToFront(config)
        }
    }

    override fun onNavigateTo(nav: Nav) {
        this.nav(nav)
    }

    @Serializable
    private sealed interface TabConfig {
        @Serializable
        data object Home : TabConfig

        @Serializable
        data object Find : TabConfig

        @Serializable
        data object Playlists : TabConfig

        @Serializable
        data object Settings : TabConfig
    }
}

// Tab components
interface HomeTabComponent: NavigationComponent

class DefaultHomeTabComponent(
    componentContext: ComponentContext,
    private val nav: (Nav) -> Unit,
) : HomeTabComponent, ComponentContext by componentContext {

    override fun onNavigateTo(nav: Nav) {
        this.nav(nav)
    }
}

interface FindTabComponent: NavigationComponent

class DefaultFindTabComponent(
    componentContext: ComponentContext,
    private val nav: (Nav) -> Unit
) : FindTabComponent, ComponentContext by componentContext {

    override fun onNavigateTo(nav: Nav) {
        this.nav(nav)
    }
}

interface PlaylistsTabComponent

class DefaultPlaylistsTabComponent(
    componentContext: ComponentContext
) : PlaylistsTabComponent, ComponentContext by componentContext

interface SettingsTabComponent: NavigationComponent

class DefaultSettingsTabComponent(
    componentContext: ComponentContext,
    private val nav: (Nav) -> Unit
) : SettingsTabComponent, ComponentContext by componentContext {

    override fun onNavigateTo(nav: Nav) {
        this.nav(nav)
    }
}
