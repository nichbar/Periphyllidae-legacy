package now.link.periphyllidae.platform

class JvmPlaybackEngineFactory : PlaybackEngineFactory {
    override fun createEngine(): PlaybackEngine =
        MpvPlaybackEngine(JnaMpvBridge())
}
