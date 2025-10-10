package now.link.periphyllidae.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.content.ContextCompat
import now.link.periphyllidae.utils.LogManager

class MusicServiceManager(private val context: Context) {
    private var musicService: MusicService? = null
    private var isBound = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            LogManager.d(TAG, "Service connected")
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isBound = true
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            LogManager.d(TAG, "Service disconnected")
            musicService = null
            isBound = false
        }
    }
    
    fun bindService() {
        if (!isBound) {
            LogManager.d(TAG, "Binding to MusicService")
            val intent = Intent(context, MusicService::class.java)
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }
    
    fun unbindService() {
        if (isBound) {
            LogManager.d(TAG, "Unbinding from MusicService")
            context.unbindService(serviceConnection)
            isBound = false
            musicService = null
        }
    }
    
    fun startService() {
        LogManager.d(TAG, "Starting MusicService")
        val intent = Intent(context, MusicService::class.java)
        ContextCompat.startForegroundService(context, intent)
    }
    
    fun stopService() {
        LogManager.d(TAG, "Stopping MusicService")
        val intent = Intent(context, MusicService::class.java)
        context.stopService(intent)
    }
    
    fun getMediaSession(): MediaSessionCompat? = musicService?.getMediaSession()
    
    fun isServiceBound(): Boolean = isBound
    
    companion object {
        private const val TAG = "MusicServiceManager"
    }
}