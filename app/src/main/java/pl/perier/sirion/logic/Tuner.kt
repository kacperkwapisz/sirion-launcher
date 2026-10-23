package pl.perier.sirion.logic

/** FM tuning maths and the tuner library's JSON status messages. */
object Tuner {
    const val MIN_KHZ = 87_500
    const val MAX_KHZ = 108_000
    const val STEP_KHZ = 100
    const val SLOTS = 8

    fun step(kilohertz: Int, up: Boolean): Int {
        val next = kilohertz + if (up) STEP_KHZ else -STEP_KHZ
        return when {
            next > MAX_KHZ -> MIN_KHZ
            next < MIN_KHZ -> MAX_KHZ
            else -> next
        }
    }

    fun label(kilohertz: Int) = "%d.%d".format(kilohertz / 1000, (kilohertz % 1000) / 100)

    fun kilohertz(preset: RadioPreset) = preset.centiMhz * 10

    fun preset(kilohertz: Int, name: String) = RadioPreset(kilohertz / 10, name)

    /** Frequency in kHz from a status message, accepting kHz, 10 kHz, 100 kHz or MHz text. */
    fun frequencyFrom(json: String): Int? {
        val raw = Regex("\"Freq\"\\s*:\\s*\"?([0-9.]+)").find(json)?.groupValues?.get(1) ?: return null
        val value = raw.toDoubleOrNull() ?: return null
        val khz = when {
            raw.contains('.') -> value * 1000
            value >= 20_000 -> value
            value >= 2_000 -> value * 10
            value >= 200 -> value * 100
            else -> return null
        }.toInt()
        return khz.takeIf { it in MIN_KHZ..MAX_KHZ }
    }

    fun stereoFrom(json: String): Boolean? =
        Regex("\"STEREO\"\\s*:\\s*\"?(\\d)").find(json)?.groupValues?.get(1)?.let { it != "0" }

    fun stationFrom(json: String): String? =
        Regex("\"PS\"\\s*:\\s*\"([^\"]*)\"").find(json)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }

    /** Puts a station into one of the [SLOTS] preset slots; other slots keep their station. */
    fun savePreset(presets: List<RadioPreset?>, slot: Int, preset: RadioPreset): List<RadioPreset?> {
        if (slot !in 0 until SLOTS) return presets
        val filled = (0 until SLOTS).map { presets.getOrNull(it) }.toMutableList()
        filled[slot] = preset
        return filled
    }
}
