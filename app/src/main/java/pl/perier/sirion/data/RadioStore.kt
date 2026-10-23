package pl.perier.sirion.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import pl.perier.sirion.logic.HelperCommand
import pl.perier.sirion.logic.RadioPreset
import pl.perier.sirion.logic.Tuner

/**
 * The radio, driven straight through our root helper and the tuner library.
 * Keeps the eight presets and the last station, and turns the tuner's
 * status messages into live frequency, stereo and station name.
 */
class RadioStore(context: Context, private val helper: RootHelper) {
    private val prefs = context.getSharedPreferences("radio", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    private val listeners = mutableListOf<() -> Unit>()

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
        helper.send(HelperCommand.Seek(up))
        changed()
    }

    fun off() {
        playing = false
        helper.send(HelperCommand.RadioOff)
        changed()
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
        const val KEY_LAST = "last_khz"
        const val KEY_PRESETS = "presets"
    }
}
