package now.link.periphyllidae.platform

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MpvPlaybackEngine(
    private val bridge: MpvBridge,
) : PlaybackEngine {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val _state = MutableStateFlow(EnginePlaybackState.IDLE)

    override val playbackState: StateFlow<EnginePlaybackState> = _state.asStateFlow()

    init {
        bridge.observePlaybackStatus { status ->
            _state.value = MpvStateMapper.toEngineState(status)
        }
    }

    override suspend fun playUri(uri: String) {
        bridge.initialize()
        bridge.loadUrl(uri)
    }

    override fun pause() = bridge.pause(true)

    override fun resume() = bridge.pause(false)

    override fun stop() = bridge.stop()

    override fun seekTo(positionMillis: Long) = bridge.seekTo(positionMillis)

    override fun getCurrentPositionMillis(): Long = bridge.currentPositionMillis()

    override fun setVolume(volume: Float) = bridge.setVolume(volume)

    override fun close() {
        scope.cancel()
        bridge.close()
    }
}
