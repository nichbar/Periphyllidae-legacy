package now.link.periphyllidae.components

import com.arkivanov.decompose.ComponentContext

interface LoginComponent: NavigationComponent

class DefaultLoginComponent(
    componentContext: ComponentContext,
    private val nav: (Nav) -> Unit
) : LoginComponent,  ComponentContext by componentContext {

    override fun onNavigateTo(nav: Nav) {
        this.nav(nav)
    }

}
