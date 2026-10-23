package pl.perier.sirion.logic

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

/**
 * Sun position, the same NOAA approximation as brightnessd's SunElevation,
 * so the brightness popover describes exactly what the tool is doing.
 */
object Sun {
    data class Change(val isDay: Boolean, val at: Instant)

    fun elevation(time: Instant, lat: Double, lon: Double): Double {
        val rad = PI / 180
        val jd = time.epochSecond / 86400.0 + 2440587.5
        val jc = (jd - 2451545) / 36525

        val meanLong = (280.46646 + jc * (36000.76983 + jc * 0.0003032)).mod(360.0)
        val meanAnom = 357.52911 + jc * (35999.05029 - 0.0001537 * jc)
        val ecc = 0.016708634 - jc * (0.000042037 + 0.0000001267 * jc)
        val center = sin(meanAnom * rad) * (1.914602 - jc * (0.004817 + 0.000014 * jc)) +
            sin(2 * meanAnom * rad) * (0.019993 - 0.000101 * jc) +
            sin(3 * meanAnom * rad) * 0.000289
        val appLong = meanLong + center - 0.00569 - 0.00478 * sin((125.04 - 1934.136 * jc) * rad)
        val obliq = 23 + (26 + (21.448 - jc * (46.815 + jc * (0.00059 - jc * 0.001813))) / 60) / 60
        val obliqCorr = obliq + 0.00256 * cos((125.04 - 1934.136 * jc) * rad)
        val decl = asin(sin(obliqCorr * rad) * sin(appLong * rad)) / rad

        val y = tan(obliqCorr / 2 * rad).pow(2)
        val eqTime = 4 / rad * (y * sin(2 * meanLong * rad) - 2 * ecc * sin(meanAnom * rad) +
            4 * ecc * y * sin(meanAnom * rad) * cos(2 * meanLong * rad) -
            0.5 * y * y * sin(4 * meanLong * rad) - 1.25 * ecc * ecc * sin(2 * meanAnom * rad))

        val utc = time.atOffset(ZoneOffset.UTC)
        val minutes = utc.hour * 60 + utc.minute + utc.second / 60.0
        val trueSolar = (minutes + eqTime + 4 * lon).mod(1440.0)
        var hourAngle = trueSolar / 4 - 180
        if (hourAngle < -180) hourAngle += 360

        val cosZenith = sin(lat * rad) * sin(decl * rad) + cos(lat * rad) * cos(decl * rad) * cos(hourAngle * rad)
        return 90 - acos(cosZenith.coerceIn(-1.0, 1.0)) / rad
    }

    /** Whether it's day now, and when that next flips (sunset or sunrise), to the minute. */
    fun nextChange(now: Instant, lat: Double, lon: Double): Change {
        val isDay = elevation(now, lat, lon) >= 0
        var t = now.truncatedTo(ChronoUnit.MINUTES)
        repeat(36 * 60) {
            t = t.plus(1, ChronoUnit.MINUTES)
            if ((elevation(t, lat, lon) >= 0) != isDay) return Change(isDay, t)
        }
        return Change(isDay, t)
    }

    /** Today's sunrise and sunset in local time, to the minute (null in polar day or night). */
    fun today(date: LocalDate, zone: ZoneId, lat: Double, lon: Double): Pair<LocalTime?, LocalTime?> {
        var rise: LocalTime? = null
        var set: LocalTime? = null
        var t = date.atStartOfDay(zone).toInstant()
        var wasDay = elevation(t, lat, lon) >= 0
        repeat(24 * 60) {
            t = t.plus(1, ChronoUnit.MINUTES)
            val isDay = elevation(t, lat, lon) >= 0
            if (isDay != wasDay) {
                val local = t.atZone(zone).toLocalTime()
                if (isDay) rise = rise ?: local else set = set ?: local
            }
            wasDay = isDay
        }
        return rise to set
    }
}
