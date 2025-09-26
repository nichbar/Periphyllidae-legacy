package now.link.periphyllidae

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.arkivanov.decompose.defaultComponentContext
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext.startKoin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        // Initialize Koin for Android
        if (org.koin.core.context.GlobalContext.getOrNull() == null) {
            startKoin {
                androidContext(this@MainActivity)
                modules(now.link.periphyllidae.di.commonModule, now.link.periphyllidae.di.platformModule)
            }
        }

        // Create component context that handles back press
        val rootComponentContext = defaultComponentContext()

        setContent {
            App(rootComponentContext = rootComponentContext)
        }
    }
}