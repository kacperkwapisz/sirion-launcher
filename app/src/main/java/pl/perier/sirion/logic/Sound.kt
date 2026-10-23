package pl.perier.sirion.logic

/**
 * Sound settings for the unit's TDA7729 audio chip: a tone preset (or the
 * driver's own bass, middle and treble) and where the sound is centred.
 */
data class Sound(
    val preset: Preset = Preset.FLAT,
    val custom: Triple<Int, Int, Int> = Triple(0, 0, 0),
    val balance: Int = 0,
    val fader: Int = 0,
) {
    enum class Preset(val label: String, val bands: Triple<Int, Int, Int>?, val line: String) {
        FLAT("Flat", Triple(0, 0, 0), "Flat · no boost or cut"),
        BASS("Bass", Triple(6, 0, 2), "Bass · more low end"),
        VOICE("Voice", Triple(-2, 4, 2), "Voice · clearer speech"),
        CUSTOM("Custom", null, "Custom, and it stays after a restart"),
    }

    enum class Band(val label: String, val effectId: Int) { BASS("Bass", 10), MIDDLE("Middle", 14), TREBLE("Treble", 17) }

    /** Balance (negative = left) and fader (positive = front) for the "Jump to" buttons. Left-hand drive. */
    enum class Jump(val label: String, val balance: Int, val fader: Int) {
        DRIVER("Driver", -3, 2), FRONT("Front", 0, 5), WHOLE_CAR("Whole car", 0, 0),
    }

    data class Effect(val id: Int, val value: Int)

    val bands: Triple<Int, Int, Int> get() = preset.bands ?: custom
    val bass get() = bands.first
    val middle get() = bands.second
    val treble get() = bands.third
    val line get() = preset.line

    fun withPreset(preset: Preset) = copy(preset = preset)

    fun withBand(band: Band, value: Int): Sound {
        val v = value.coerceIn(-BAND_RANGE, BAND_RANGE)
        val (b, m, t) = bands
        val next = when (band) {
            Band.BASS -> Triple(v, m, t)
            Band.MIDDLE -> Triple(b, v, t)
            Band.TREBLE -> Triple(b, m, v)
        }
        return copy(preset = Preset.CUSTOM, custom = next)
    }

    fun level(band: Band) = when (band) {
        Band.BASS -> bass
        Band.MIDDLE -> middle
        Band.TREBLE -> treble
    }

    fun withPosition(balance: Int, fader: Int) = copy(
        balance = balance.coerceIn(-POSITION_STEPS, POSITION_STEPS),
        fader = fader.coerceIn(-POSITION_STEPS, POSITION_STEPS),
    )

    val positionText: String
        get() {
            if (balance == 0 && fader == 0) return "Centre"
            Jump.entries.firstOrNull { it.balance == balance && it.fader == fader }?.let { return it.label }
            return listOf(balance.takeIf { it != 0 }?.let(::balanceText), fader.takeIf { it != 0 }?.let(::faderText))
                .filterNotNull().joinToString(" · ")
        }

    /** Attenuation per speaker in dB: front left, front right, rear left, rear right. */
    val trims: List<Int>
        get() {
            val left = if (balance > 0) -balance * DB_PER_STEP else 0
            val right = if (balance < 0) balance * DB_PER_STEP else 0
            val front = if (fader < 0) fader * DB_PER_STEP else 0
            val rear = if (fader > 0) -fader * DB_PER_STEP else 0
            return listOf(left + front, right + front, left + rear, right + rear)
        }

    /** Everything to send to the chip, in a fixed order. */
    val effects: List<Effect>
        get() = Band.entries.map { Effect(it.effectId, level(it)) } +
            trims.mapIndexed { speaker, db -> Effect(speaker, db) }

    fun save() = listOf(preset.name, custom.first, custom.second, custom.third, balance, fader).joinToString("|")

    companion object {
        const val BAND_RANGE = 10
        const val POSITION_STEPS = 10
        const val DB_PER_STEP = 2

        fun decibels(value: Int) = when {
            value > 0 -> "+$value dB"
            value < 0 -> "−${-value} dB"
            else -> "0 dB"
        }

        fun balanceText(value: Int) = when {
            value < 0 -> "${-value} left"
            value > 0 -> "$value right"
            else -> "Centre"
        }

        fun faderText(value: Int) = when {
            value > 0 -> "$value front"
            value < 0 -> "${-value} rear"
            else -> "Centre"
        }

        fun load(text: String?): Sound {
            val parts = text?.split('|') ?: return Sound()
            if (parts.size != 6) return Sound()
            val preset = Preset.entries.firstOrNull { it.name == parts[0] } ?: return Sound()
            val n = parts.drop(1).map { it.toIntOrNull() ?: return Sound() }
            return Sound(preset, Triple(n[0], n[1], n[2])).withPosition(n[3], n[4])
        }
    }
}
