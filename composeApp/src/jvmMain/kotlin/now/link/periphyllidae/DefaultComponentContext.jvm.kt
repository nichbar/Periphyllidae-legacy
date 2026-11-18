package now.link.periphyllidae

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.statekeeper.StateKeeperDispatcher

fun defaultComponentContext(): ComponentContext {
    val lifecycle = LifecycleRegistry()
    val stateKeeper = StateKeeperDispatcher()
    
    return DefaultComponentContext(
        lifecycle = lifecycle,
        stateKeeper = stateKeeper
    )
}
