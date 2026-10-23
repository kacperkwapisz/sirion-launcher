package pl.perier.sirion.logic

/** An FM preset. [centiMhz] is the frequency in hundredths of a MHz (92.0 MHz = 9200). */
data class RadioPreset(val centiMhz: Int, val name: String) {
    val label: String get() = "%d.%d".format(centiMhz / 100, (centiMhz % 100) / 10)

    companion object {
        private const val FM_MIN = 8750
        private const val FM_MAX = 10800

        val defaults = listOf(
            RadioPreset(9200, "Szczecin"),
            RadioPreset(9480, "RMF FM"),
            RadioPreset(9960, "Radio ZET"),
            RadioPreset(10170, "Eska"),
        )

        fun parseFm(text: String): Int? {
            val mhz = text.trim().toBigDecimalOrNull() ?: return null
            val centi = mhz.movePointRight(2).toInt()
            return centi.takeIf { it in FM_MIN..FM_MAX }
        }
    }
}
