package pl.perier.sirion.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import pl.perier.sirion.logic.HelperCommand
import pl.perier.sirion.logic.RadioPreset
import pl.perier.sirion.logic.Tuner

/**
 * The radio, driven straight through our root helper and the tuner library.
 * Keeps the eight presets and the last station, and turns the tuner's
 * status messages into live frequency, stereo and station name.
 *
 * The tuner plays into the audio chip's own input, not through Android, so
 * the radio holds Android's audio focus like any player: when Bluetooth
 * music or CarPlay takes it, the radio turns off and the chip goes back to
 * Android's input; a short interruption (a navigation prompt) switches to
 * Android's input until it's over.
 */
class RadioStore(context: Context, private val helper: RootHelper) {
    private val prefs = context.getSharedPreferences("radio", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    private val listeners = mutableListOf<() -> Unit>()
    private val audio = context.getSystemService(AudioManager::class.java)
    private var hasFocus = false
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        .setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener({ change -> onFocus(change) }, main)
        .build()

    var kilohertz: Int = prefs.getInt(KEY_LAST, Tuner.kilohertz(RadioPreset.defaults.first()))
        private set
    var playing = false
        private set
    var seeking = false
        private set
    /** Which way the current seek goes, for "Searching up". */
    var seekingUp = true
        private set
    var stereo = false
        private set
    var station: String? = null
        private set

    var presets: List<RadioPreset?>
        get() {
            val saved = prefs.getString(KEY_PRESETS, null) ?: return (0 until Tuner.SLOTS).map { RadioPreset.defaults.getOrNull(it) }
            return saved.split('\n').map { line ->
                line.split('|', limit = 2).takeIf { it.size == 2 }?.let { RadioPreset(it[0].toInt(), it[1]) }
            }
        }
        private set(value) {
            prefs.edit().putString(KEY_PRESETS, value.joinToString("\n") { it?.let { p -> "${p.centiMhz}|${p.name}" } ?: "" }).apply()
        }

    /** Name for the current frequency: the station's own RDS name, else the preset's. */
    val name: String?
        get() = station ?: presets.firstOrNull { it != null && Tuner.kilohertz(it) == kilohertz }?.name

    fun listen(listener: () -> Unit) { listeners += listener }
    fun stopListening(listener: () -> Unit) { listeners -= listener }

    fun tune(khz: Int) {
        kilohertz = khz
        playing = true
        seeking = false
        station = null
        prefs.edit().putInt(KEY_LAST, khz).apply()
        takeFocus()
        helper.send(HelperCommand.Tune(khz))
        changed()
    }

    fun tune(preset: RadioPreset) = tune(Tuner.kilohertz(preset))

    fun step(up: Boolean) = tune(Tuner.step(kilohertz, up))

    fun seek(up: Boolean) {
        playing = true
        seeking = true
        seekingUp = up
        station = null
        takeFocus()
        helper.send(HelperCommand.Seek(up))
        changed()
    }

    fun off() {
        playing = false
        if (hasFocus) audio.abandonAudioFocusRequest(focus)
        hasFocus = false
        helper.send(HelperCommand.RadioOff)
        changed()
    }

    private fun takeFocus() {
        if (!hasFocus) hasFocus = audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun onFocus(change: Int) {
        when (change) {
            // Another player took over (Bluetooth music, CarPlay): the radio steps aside.
            AudioManager.AUDIOFOCUS_LOSS -> { hasFocus = false; off() }
            // A prompt or a call: let Android's audio through until it's done.
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ->
                helper.send(HelperCommand.Effect(SOURCE_SELECTOR_MAIN, INPUT_ANDROID))
            AudioManager.AUDIOFOCUS_GAIN -> if (playing) helper.send(HelperCommand.Tune(kilohertz))
        }
    }

    fun savePreset(slot: Int) {
        presets = Tuner.savePreset(presets, slot, Tuner.preset(kilohertz, name ?: "FM ${Tuner.label(kilohertz)}"))
        changed()
    }

    /** A status line from the tuner library (JSON); see [Tuner] for what we read from it. */
    fun onTunerEvent(json: String) = main.post {
        Tuner.frequencyFrom(json)?.let { khz ->
            if (khz != kilohertz) station = null
            kilohertz = khz
            seeking = false
            prefs.edit().putInt(KEY_LAST, khz).apply()
        }
        Tuner.stereoFrom(json)?.let { stereo = it }
        Tuner.stationFrom(json)?.let { station = it }
        changed()
    }

    private fun changed() = listeners.toList().forEach { it() }

    private companion object {
        /** The audio chip's main input selector and Android's input (see the root helper). */
        const val SOURCE_SELECTOR_MAIN = 34
        const val INPUT_ANDROID = 2
        const val KEY_LAST = "last_khz"
        const val KEY_PRESETS = "presets"
    }
}
