package now.link.periphyllidae.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import now.link.periphyllidae.MainActivity
import now.link.periphyllidae.R
import now.link.periphyllidae.models.Song
import now.link.periphyllidae.mvi.MusicIntent
import now.link.periphyllidae.platform.PlaybackState
import now.link.periphyllidae.store.MusicStore
import now.link.periphyllidae.utils.LogManager
import org.koin.android.ext.android.inject

class MusicService : Service() {
    private val musicStore: MusicStore by inject()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var notificationManager: NotificationManager
    private lateinit var imageLoader: ImageLoader
    
    private val binder = MusicBinder()
    
    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }
    
    override fun onCreate() {
        super.onCreate()
        LogManager.i(TAG, "MusicService created")
        
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        imageLoader = ImageLoader(this)
        createNotificationChannel()
        setupMediaSession()
        observePlaybackState()
    }
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LogManager.d(TAG, "onStartCommand: ${intent?.action}")
        
        // Convert service intent to MusicStore intent and handle it
        val musicIntent = if (intent?.action == ACTION_SEEK) {
            MusicIntentConverter.convertSeekIntent(intent)
        } else {
            MusicIntentConverter.convertServiceIntent(intent?.action)
        }
        
        musicIntent?.let { musicStore.handleIntent(it) }
        
        // Special handling for stop action
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        LogManager.i(TAG, "MusicService destroyed")
        mediaSession.release()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls for music playback"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
            LogManager.d(TAG, "Notification channel created")
        }
    }
    
    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, TAG).apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    LogManager.d(TAG, "MediaSession: onPlay")
                    musicStore.handleIntent(MusicIntent.Play)
                }
                
                override fun onPause() {
                    LogManager.d(TAG, "MediaSession: onPause")
                    musicStore.handleIntent(MusicIntent.Pause)
                }
                
                override fun onSkipToNext() {
                    LogManager.d(TAG, "MediaSession: onSkipToNext")
                    musicStore.handleIntent(MusicIntent.Next)
                }
                
                override fun onSkipToPrevious() {
                    LogManager.d(TAG, "MediaSession: onSkipToPrevious")
                    musicStore.handleIntent(MusicIntent.Previous)
                }
                
                override fun onStop() {
                    LogManager.d(TAG, "MediaSession: onStop")
                    musicStore.handleIntent(MusicIntent.Pause) // Convert stop to pause to keep the queue
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                
                override fun onSeekTo(pos: Long) {
                    LogManager.d(TAG, "MediaSession: onSeekTo $pos")
                    musicStore.handleIntent(MusicIntent.SeekTo(pos))
                }
            })
            
            isActive = true
        }
        LogManager.d(TAG, "MediaSession setup completed")
    }
    
    private fun observePlaybackState() {
        scope.launch {
            // Observe MusicStore state only - no direct MusicPlayer access
            musicStore.state.collect { musicState ->
                val playbackInfo = PlaybackInfo(
                    song = musicState.currentSong,
                    isPlaying = musicState.isPlaying,
                    state = musicState.playbackState,
                    position = musicState.currentPosition,
                    duration = musicState.duration
                )
                updateMediaSession(playbackInfo)
                updateNotification(playbackInfo)
            }
        }
    }
    
    private fun updateMediaSession(playbackInfo: PlaybackInfo) {
        val song = playbackInfo.song
        
        // Update metadata
        val metadataBuilder = MediaMetadataCompat.Builder()
        if (song != null) {
            metadataBuilder
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.album)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, playbackInfo.duration)
            
            // Load album art asynchronously
            song.coverArt?.let { coverUrl ->
                scope.launch {
                    try {
                        val bitmap = loadAlbumArt(coverUrl)
                        bitmap?.let {
                            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it)
                            mediaSession.setMetadata(metadataBuilder.build())
                        }
                    } catch (e: Exception) {
                        LogManager.w(TAG, "Failed to load album art", e)
                    }
                }
            }
        }
        mediaSession.setMetadata(metadataBuilder.build())
        
        // Update playback state
        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(
                when (playbackInfo.state) {
                    PlaybackState.PLAYING -> PlaybackStateCompat.STATE_PLAYING
                    PlaybackState.PAUSED -> PlaybackStateCompat.STATE_PAUSED
                    PlaybackState.BUFFERING -> PlaybackStateCompat.STATE_BUFFERING
                    PlaybackState.ERROR -> PlaybackStateCompat.STATE_ERROR
                    PlaybackState.IDLE -> PlaybackStateCompat.STATE_STOPPED
                },
                playbackInfo.position,
                1.0f
            )
        
        mediaSession.setPlaybackState(stateBuilder.build())
    }
    
    private fun updateNotification(playbackInfo: PlaybackInfo) {
        val song = playbackInfo.song
        
        if (song == null || playbackInfo.state == PlaybackState.IDLE) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            return
        }
        
        val notification = createNotification(playbackInfo)
        startForeground(NOTIFICATION_ID, notification)
    }
    
    private fun createNotification(playbackInfo: PlaybackInfo): Notification {
        val song = playbackInfo.song!!
        
        // Create intent to open the main activity
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Create action intents
        val playPauseAction = if (playbackInfo.isPlaying) {
            NotificationCompat.Action(
                android.R.drawable.ic_media_pause,
                "Pause",
                createPendingIntent(ACTION_PAUSE)
            )
        } else {
            NotificationCompat.Action(
                android.R.drawable.ic_media_play,
                "Play",
                createPendingIntent(ACTION_PLAY)
            )
        }
        
        val previousAction = NotificationCompat.Action(
            android.R.drawable.ic_media_previous,
            "Previous",
            createPendingIntent(ACTION_PREVIOUS)
        )
        
        val nextAction = NotificationCompat.Action(
            android.R.drawable.ic_media_next,
            "Next",
            createPendingIntent(ACTION_NEXT)
        )
        
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(song.title)
            .setContentText("${song.artist} • ${song.album}")
            .setSmallIcon(R.drawable.periphyllidae_icon)
            .setContentIntent(openAppPendingIntent)
            .setDeleteIntent(createPendingIntent(ACTION_STOP))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .addAction(previousAction)
            .addAction(playPauseAction)
            .addAction(nextAction)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2) // Show all three actions in compact view
            )
        
        // Load album art asynchronously for notification
        song.coverArt?.let { coverUrl ->
            scope.launch {
                try {
                    val bitmap = loadAlbumArt(coverUrl)
                    bitmap?.let {
                        // Update notification with album art
                        val updatedNotification = notificationBuilder
                            .setLargeIcon(it)
                            .build()
                        notificationManager.notify(NOTIFICATION_ID, updatedNotification)
                    }
                } catch (e: Exception) {
                    LogManager.w(TAG, "Failed to load album art for notification", e)
                }
            }
        }
        
        return notificationBuilder.build()
    }
    
    private fun createPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, MusicService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    fun getMediaSession(): MediaSessionCompat = mediaSession
    
    /**
     * Load album art bitmap using Coil
     */
    private suspend fun loadAlbumArt(url: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val request = ImageRequest.Builder(this@MusicService)
                .data(url)
                .size(256, 256) // Resize for performance
                .build()
            
            val result = imageLoader.execute(request)
            when (result) {
                is SuccessResult -> {
                    // Convert the image to bitmap
                    result.image.toBitmap()
                }
                else -> {
                    LogManager.w(TAG, "Failed to load image, result: $result")
                    null
                }
            }
        } catch (e: Exception) {
            LogManager.w(TAG, "Failed to load album art from URL: $url", e)
            null
        }
    }
    
    private data class PlaybackInfo(
        val song: Song?,
        val isPlaying: Boolean,
        val state: PlaybackState,
        val position: Long,
        val duration: Long
    )
    
    companion object {
        private const val TAG = "MusicService"
        private const val CHANNEL_ID = "music_playback"
        private const val NOTIFICATION_ID = 1001
        
        const val ACTION_PLAY = "now.link.periphyllidae.action.PLAY"
        const val ACTION_PAUSE = "now.link.periphyllidae.action.PAUSE"
        const val ACTION_NEXT = "now.link.periphyllidae.action.NEXT"
        const val ACTION_PREVIOUS = "now.link.periphyllidae.action.PREVIOUS"
        const val ACTION_STOP = "now.link.periphyllidae.action.STOP"
        const val ACTION_SEEK = "now.link.periphyllidae.action.SEEK"
        const val EXTRA_SEEK_POSITION = "seek_position"
    }
}