package now.link.periphyllidae.platform

enum class MpvPlaybackStatus {
    IDLE,
    PLAYING,
    PAUSED,
    BUFFERING,
    FINISHED,
}

object MpvStateMapper {
    fun toEngineState(status: MpvPlaybackStatus): EnginePlaybackState = when (status) {
        MpvPlaybackStatus.PLAYING -> EnginePlaybackState.PLAYING
        MpvPlaybackStatus.PAUSED -> EnginePlaybackState.PAUSED
        MpvPlaybackStatus.BUFFERING -> EnginePlaybackState.BUFFERING
        MpvPlaybackStatus.FINISHED -> EnginePlaybackState.FINISHED
        MpvPlaybackStatus.IDLE -> EnginePlaybackState.IDLE
    }
}
