package now.link.periphyllidae

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.arkivanov.decompose.defaultComponentContext
import now.link.periphyllidae.service.MusicServiceManager
import org.koin.android.ext.android.inject
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
        
        // Now we can inject after Koin is initialized
        val musicServiceManager: MusicServiceManager by inject()

        // Start and bind to music service
        // Service should outlive the activity, so we start it but don't stop it in onDestroy
        musicServiceManager.startService()
        musicServiceManager.bindService()

        // Create component context that handles back press
        val rootComponentContext = defaultComponentContext()

        setContent {
            App(rootComponentContext = rootComponentContext)
        }
    }
    
    override fun onStop() {
        super.onStop()
        // Unbind when activity stops (not destroyed) to save resources
        // Service continues running in background
        val musicServiceManager: MusicServiceManager by inject()
        musicServiceManager.unbindService()
    }
    
    override fun onRestart() {
        super.onRestart()
        // Rebind when activity restarts
        val musicServiceManager: MusicServiceManager by inject()
        musicServiceManager.bindService()
    }
}