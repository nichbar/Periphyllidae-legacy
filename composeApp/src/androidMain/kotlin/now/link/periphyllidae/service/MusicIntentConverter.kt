package now.link.periphyllidae.service

import android.content.Intent
import now.link.periphyllidae.mvi.MusicIntent

/**
 * Utility class to convert service intents to MusicStore intents
 */
object MusicIntentConverter {
    
    fun convertServiceIntent(action: String?): MusicIntent? {
        return when (action) {
            MusicService.ACTION_PLAY -> MusicIntent.Play
            MusicService.ACTION_PAUSE -> MusicIntent.Pause
            MusicService.ACTION_NEXT -> MusicIntent.Next
            MusicService.ACTION_PREVIOUS -> MusicIntent.Previous
            MusicService.ACTION_STOP -> MusicIntent.Pause // Convert stop to pause to keep the queue
            else -> null
        }
    }
    
    fun convertSeekIntent(intent: Intent): MusicIntent? {
        val position = intent.getLongExtra(MusicService.EXTRA_SEEK_POSITION, -1)
        return if (position >= 0) {
            MusicIntent.SeekTo(position)
        } else {
            null
        }
    }
}