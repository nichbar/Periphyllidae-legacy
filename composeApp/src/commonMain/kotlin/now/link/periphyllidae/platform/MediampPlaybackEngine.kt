package now.link.periphyllidae.platform

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.features.AudioLevelController
import org.openani.mediamp.playUri

class MediampPlaybackEngine(
    private val mediampPlayer: MediampPlayer,
) : PlaybackEngine {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override val playbackState: StateFlow<EnginePlaybackState> =
        mediampPlayer.playbackState
            .map { state ->
                when (state) {
                    org.openani.mediamp.PlaybackState.PAUSED -> EnginePlaybackState.PAUSED
                    org.openani.mediamp.PlaybackState.PAUSED_BUFFERING -> EnginePlaybackState.BUFFERING
                    org.openani.mediamp.PlaybackState.READY -> EnginePlaybackState.READY
                    org.openani.mediamp.PlaybackState.FINISHED -> EnginePlaybackState.FINISHED
                    org.openani.mediamp.PlaybackState.PLAYING -> EnginePlaybackState.PLAYING
                    else -> EnginePlaybackState.IDLE
                }
            }
            .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, EnginePlaybackState.IDLE)

    override suspend fun playUri(uri: String) {
        mediampPlayer.playUri(uri)
    }

    override fun pause() {
        mediampPlayer.pause()
    }

    override fun resume() {
        mediampPlayer.resume()
    }

    override fun stop() {
        mediampPlayer.stopPlayback()
    }

    override fun seekTo(positionMillis: Long) {
        mediampPlayer.seekTo(positionMillis)
    }

    override fun getCurrentPositionMillis(): Long {
        return mediampPlayer.getCurrentPositionMillis()
    }

    override fun setVolume(volume: Float) {
        mediampPlayer.features[AudioLevelController]?.setVolume(volume)
    }

    override fun close() {
        scope.cancel()
        mediampPlayer.close()
    }
}
