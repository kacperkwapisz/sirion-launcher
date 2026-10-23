package pl.perier.sirion.logic

import java.time.ZoneId

/** Backlight settings the launcher hands to brightnessd: a mode and two levels (0-255). */
data class Brightness(val mode: String = "auto", val day: Int = 210, val night: Int = 30) {
    fun context(change: Sun.Change, zone: ZoneId): String = when (mode) {
        "day" -> "Stays at the day level"
        "night" -> "Stays at the night level"
        else -> {
            val time = TimeFormat.clock(change.at.atZone(zone).toLocalDateTime())
            if (change.isDay) "Day until sunset $time" else "Night until sunrise $time"
        }
    }

    /** The level that applies right now, which is the one the slider moves. */
    fun level(isDay: Boolean): Int = when (mode) {
        "day" -> day
        "night" -> night
        else -> if (isDay) day else night
    }

    fun withLevel(value: Int, isDay: Boolean): Brightness {
        val v = value.coerceIn(MIN_LEVEL, 255)
        return when (mode) {
            "day" -> copy(day = v)
            "night" -> copy(night = v)
            else -> if (isDay) copy(day = v) else copy(night = v)
        }
    }

    val command get() = HelperCommand.Brightness(mode, day, night)

    companion object {
        const val MIN_LEVEL = 5
    }
}
