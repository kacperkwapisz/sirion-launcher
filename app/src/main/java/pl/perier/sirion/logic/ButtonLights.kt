package pl.perier.sirion.logic

/**
 * The unit's button lights, set through the MCU frame the vendor colour-light
 * screen sends: [7, 11, mode, R, G, B, flags, brightness]. On this unit the
 * LEDs are plain white, so colour and brightness do nothing; the headlight
 * wire lights them directly, so nothing here can turn them off while the
 * headlights are on. "Always on" is the one thing the MCU adds.
 */
object ButtonLights {
    enum class Mode {
        WITH_HEADLIGHTS, ALWAYS_ON;

        companion object {
            fun load(saved: String?) = values().firstOrNull { it.name == saved } ?: WITH_HEADLIGHTS
        }
    }

    fun frame(mode: Mode): ByteArray = when (mode) {
        // Preview (not saved in the unit): colour lights + always on, white, full.
        Mode.ALWAYS_ON -> bytes(7, 11, PREVIEW, 255, 255, 255, COLOUR or ALWAYS, 100)
        // Restore: the unit's own saved settings (all off, brightness 100).
        Mode.WITH_HEADLIGHTS -> bytes(7, 11, RESTORE, 0, 0, 0, 0, 100)
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private const val PREVIEW = 1
    private const val RESTORE = 3
    private const val COLOUR = 1
    private const val ALWAYS = 2
}
