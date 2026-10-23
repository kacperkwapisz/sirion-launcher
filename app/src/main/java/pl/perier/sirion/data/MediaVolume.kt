package pl.perier.sirion.data

import android.content.Context
import android.media.AudioManager
import android.util.Log
import pl.perier.sirion.logic.Volume

/** Media volume through Android's AudioManager. */
class MediaVolume(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val prefs = context.getSharedPreferences("volume", Context.MODE_PRIVATE)

    val max: Int get() = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    val current: Int get() = audio.getStreamVolume(AudioManager.STREAM_MUSIC)

    /** The level before the mute, shown greyed out while muted. */
    val remembered: Int get() = prefs.getInt(KEY_REMEMBERED, 0)

    fun step(delta: Int) = set(Volume.stepFrom(current, remembered, delta, max))

    /**
     * On this unit, volume 0 counts as a Do Not Disturb change, which needs
     * notification-policy access (setup grants it). Never crash over it.
     */
    fun set(value: Int) {
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, value.coerceIn(0, max), 0) }
            .onFailure { Log.w("SirionVolume", "Volume $value refused", it) }
    }

    val muted: Boolean get() = current == 0

    fun toggleMute() {
        val next = Volume.toggleMute(current, prefs.getInt(KEY_REMEMBERED, 0), max)
        prefs.edit().putInt(KEY_REMEMBERED, next.remembered).apply()
        set(next.volume)
    }

    private companion object {
        const val KEY_REMEMBERED = "before_mute"
    }
}
