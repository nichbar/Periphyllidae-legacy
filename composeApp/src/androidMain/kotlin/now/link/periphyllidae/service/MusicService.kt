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
import androidx.media.app.NotificationCompat.MediaStyle
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
import java.util.concurrent.atomic.AtomicInteger

class MusicService : Service() {
    private val musicStore: MusicStore by inject()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var notificationManager: NotificationManager
    private lateinit var imageLoader: ImageLoader

    private val binder = MusicBinder()
    private var playbackObserverJob: Job? = null
    private var isInForeground = false

    private var currentPlaybackKey: String? = null
    private var lastMetadataSongId: String? = null
    private var lastMetadataDuration: Long = -1L
    private var lastMediaSessionCoverArt: String? = null
    private var lastNotificationCoverArt: String? = null
    private var lastNotificationSongId: String? = null
    private var lastNotificationIsPlaying: Boolean? = null
    private var lastNotificationPlaybackState: PlaybackState? = null

    private var playbackEmissionCount = 0
    private var playbackEmissionWindowStartMs = 0L

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
        val action = intent?.action
        LogManager.d(TAG, "onStartCommand: $action")

        val musicIntent = if (action == ACTION_SEEK) {
            MusicIntentConverter.convertSeekIntent(intent)
        } else {
            MusicIntentConverter.convertServiceIntent(action)
        }

        musicIntent?.let { musicStore.handleIntent(it) }

        if (action == ACTION_STOP) {
            stopForegroundIfNeeded()
            stopSelf()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        LogManager.i(TAG, "MusicService destroyed - cleanup started")

        playbackObserverJob?.cancel()
        playbackObserverJob = null
        serviceScope.cancel("MusicService destroyed")

        stopForegroundIfNeeded()
        mediaSession.release()

        LogManager.i(TAG, "MusicService cleanup completed")
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
                    musicStore.handleIntent(MusicIntent.Stop)
                    stopForegroundIfNeeded()
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
        if (playbackObserverJob?.isActive == true) {
            LogManager.w(TAG, "Playback observer already active; skipping duplicate registration")
            return
        }

        playbackObserverJob = serviceScope.launch {
            val collectors = activeCollectorCount.incrementAndGet()
            LogManager.d(TAG, "Playback observer started (activeCollectors=$collectors)")

            try {
                musicStore.state.collect { musicState ->
                    val now = System.currentTimeMillis()
                    if (playbackEmissionWindowStartMs == 0L) {
                        playbackEmissionWindowStartMs = now
                    }
                    playbackEmissionCount++
                    if (now - playbackEmissionWindowStartMs >= EMISSION_LOG_WINDOW_MS) {
                        val durationMs = (now - playbackEmissionWindowStartMs).coerceAtLeast(1L)
                        val emissionRatePerSecond = (playbackEmissionCount * 1000L) / durationMs
                        LogManager.d(
                            TAG,
                            "Playback state cadence: emissions=$playbackEmissionCount, rate=${emissionRatePerSecond}/s, activeCollectors=${activeCollectorCount.get()}"
                        )
                        playbackEmissionCount = 0
                        playbackEmissionWindowStartMs = now
                    }

                    val playbackInfo = PlaybackInfo(
                        song = musicState.currentSong,
                        isPlaying = musicState.isPlaying,
                        state = musicState.playbackState,
                        position = musicState.currentPosition,
                        duration = musicState.duration
                    )

                    val newPlaybackKey = playbackInfo.song?.let { "${it.id}|${it.coverArt ?: ""}" } ?: "none"
                    val trackChanged = newPlaybackKey != currentPlaybackKey
                    currentPlaybackKey = newPlaybackKey

                    val metadataChanged =
                        playbackInfo.song?.id != lastMetadataSongId || playbackInfo.duration != lastMetadataDuration

                    val notificationChanged =
                        playbackInfo.song?.id != lastNotificationSongId ||
                            playbackInfo.isPlaying != lastNotificationIsPlaying ||
                            playbackInfo.state != lastNotificationPlaybackState

                    updateMediaSession(playbackInfo, metadataChanged, trackChanged)
                    updateNotification(playbackInfo, trackChanged, notificationChanged)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                LogManager.e(TAG, "Playback observer failed", e)
            } finally {
                val remaining = activeCollectorCount.decrementAndGet().coerceAtLeast(0)
                LogManager.d(TAG, "Playback observer stopped (activeCollectors=$remaining)")
            }
        }
    }

    private fun updateMediaSession(
        playbackInfo: PlaybackInfo,
        metadataChanged: Boolean,
        trackChanged: Boolean
    ) {
        val song = playbackInfo.song

        if (metadataChanged) {
            mediaSession.setMetadata(buildMetadata(song, playbackInfo.duration, null))
            lastMetadataSongId = song?.id
            lastMetadataDuration = playbackInfo.duration
        }

        if (trackChanged) {
            val coverUrl = song?.coverArt
            if (coverUrl == null) {
                lastMediaSessionCoverArt = null
            } else if (coverUrl != lastMediaSessionCoverArt) {
                lastMediaSessionCoverArt = coverUrl
                val expectedPlaybackKey = currentPlaybackKey
                val expectedSongId = song.id
                val duration = playbackInfo.duration

                serviceScope.launch {
                    try {
                        val bitmap = loadAlbumArt(coverUrl)
                        if (
                            bitmap != null &&
                                currentPlaybackKey == expectedPlaybackKey &&
                                musicStore.state.value.currentSong?.id == expectedSongId
                        ) {
                            mediaSession.setMetadata(buildMetadata(song, duration, bitmap))
                        }
                    } catch (e: Exception) {
                        LogManager.w(TAG, "Failed to load album art", e)
                    }
                }
            }
        }

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
                if (playbackInfo.isPlaying) 1.0f else 0f
            )

        mediaSession.setPlaybackState(stateBuilder.build())
    }

    private fun updateNotification(
        playbackInfo: PlaybackInfo,
        trackChanged: Boolean,
        notificationChanged: Boolean
    ) {
        val song = playbackInfo.song

        if (song == null || playbackInfo.state == PlaybackState.IDLE) {
            stopForegroundIfNeeded()
            lastNotificationSongId = null
            lastNotificationIsPlaying = null
            lastNotificationPlaybackState = null
            lastNotificationCoverArt = null
            return
        }

        if (!notificationChanged && !trackChanged) {
            return
        }

        val notification = createNotification(playbackInfo)
        if (isInForeground) {
            notificationManager.notify(NOTIFICATION_ID, notification)
        } else {
            startForeground(NOTIFICATION_ID, notification)
            isInForeground = true
        }

        lastNotificationSongId = song.id
        lastNotificationIsPlaying = playbackInfo.isPlaying
        lastNotificationPlaybackState = playbackInfo.state

        if (trackChanged) {
            val coverUrl = song.coverArt
            if (coverUrl == null) {
                lastNotificationCoverArt = null
            } else if (coverUrl != lastNotificationCoverArt) {
                lastNotificationCoverArt = coverUrl
                val expectedPlaybackKey = currentPlaybackKey
                val expectedSongId = song.id

                serviceScope.launch {
                    try {
                        val bitmap = loadAlbumArt(coverUrl)
                        val latestState = musicStore.state.value
                        if (
                            bitmap != null &&
                                isInForeground &&
                                currentPlaybackKey == expectedPlaybackKey &&
                                latestState.currentSong?.id == expectedSongId &&
                                latestState.playbackState != PlaybackState.IDLE
                        ) {
                            val latestPlaybackInfo = PlaybackInfo(
                                song = latestState.currentSong,
                                isPlaying = latestState.isPlaying,
                                state = latestState.playbackState,
                                position = latestState.currentPosition,
                                duration = latestState.duration
                            )
                            val updatedNotification = createNotification(latestPlaybackInfo, bitmap)
                            notificationManager.notify(NOTIFICATION_ID, updatedNotification)
                        }
                    } catch (e: Exception) {
                        LogManager.w(TAG, "Failed to load album art for notification", e)
                    }
                }
            }
        }
    }

    private fun buildMetadata(song: Song?, duration: Long, bitmap: Bitmap?): MediaMetadataCompat {
        val metadataBuilder = MediaMetadataCompat.Builder()
        if (song != null) {
            metadataBuilder
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.album)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
            bitmap?.let {
                metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it)
            }
        }
        return metadataBuilder.build()
    }

    private fun createNotification(playbackInfo: PlaybackInfo, largeIcon: Bitmap? = null): Notification {
        val song = playbackInfo.song!!

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

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

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
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
                    .setShowActionsInCompactView(0, 1, 2)
            )

        largeIcon?.let { builder.setLargeIcon(it) }

        return builder.build()
    }

    private fun createPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, MusicService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun stopForegroundIfNeeded() {
        if (isInForeground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isInForeground = false
        }
    }

    fun getMediaSession(): MediaSessionCompat = mediaSession

    private suspend fun loadAlbumArt(url: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val request = ImageRequest.Builder(this@MusicService)
                .data(url)
                .size(256, 256)
                .build()

            val result = imageLoader.execute(request)
            when (result) {
                is SuccessResult -> result.image.toBitmap()
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
        private const val EMISSION_LOG_WINDOW_MS = 30_000L

        private val activeCollectorCount = AtomicInteger(0)

        const val ACTION_PLAY = "now.link.periphyllidae.action.PLAY"
        const val ACTION_PAUSE = "now.link.periphyllidae.action.PAUSE"
        const val ACTION_NEXT = "now.link.periphyllidae.action.NEXT"
        const val ACTION_PREVIOUS = "now.link.periphyllidae.action.PREVIOUS"
        const val ACTION_STOP = "now.link.periphyllidae.action.STOP"
        const val ACTION_SEEK = "now.link.periphyllidae.action.SEEK"
        const val EXTRA_SEEK_POSITION = "seek_position"
    }
}
