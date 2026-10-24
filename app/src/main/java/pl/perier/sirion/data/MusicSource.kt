package pl.perier.sirion.data

import android.graphics.Bitmap
import pl.perier.sirion.logic.Track

/** Where the music card's song comes from: Bluetooth, or CarPlay through DiPlay. */
interface MusicSource {
    /** Shuffle or repeat as the phone reports it; unavailable when it isn't offered. */
    enum class Mode { UNAVAILABLE, OFF, ON }

    val track: Track?
    val album: String
    val playing: Boolean
    val positionMs: Long
    /** The real album art, or null (never a stand-in picture). */
    val artwork: Bitmap?
    /** Whether dragging the scrubber can move the song. */
    val canSeek: Boolean
    val shuffle: Mode
    val repeat: Mode

    fun start()
    fun stop()
    fun play()
    fun playPause()
    fun next()
    fun previous()
    fun seekTo(ms: Long)
    fun toggleShuffle()
    fun toggleRepeat()
}
