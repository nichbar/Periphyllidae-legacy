package now.link.periphyllidae.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class MpvStateMapperTest {
    @Test
    fun mapsCoreMpvStatesToEngineStates() {
        assertEquals(EnginePlaybackState.PLAYING, MpvStateMapper.toEngineState(MpvPlaybackStatus.PLAYING))
        assertEquals(EnginePlaybackState.PAUSED, MpvStateMapper.toEngineState(MpvPlaybackStatus.PAUSED))
        assertEquals(EnginePlaybackState.BUFFERING, MpvStateMapper.toEngineState(MpvPlaybackStatus.BUFFERING))
        assertEquals(EnginePlaybackState.FINISHED, MpvStateMapper.toEngineState(MpvPlaybackStatus.FINISHED))
        assertEquals(EnginePlaybackState.IDLE, MpvStateMapper.toEngineState(MpvPlaybackStatus.IDLE))
    }
}
