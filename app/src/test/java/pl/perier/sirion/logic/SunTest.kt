package pl.perier.sirion.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** Same cases as brightnessd's Go tests, so the launcher and the tool agree. */
class SunTest {
    private val utc = ZoneId.of("UTC")
    private val warsaw = ZoneId.of("Europe/Warsaw")
    private val lat = 53.452
    private val lon = 14.212

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int) = ZonedDateTime.of(y, m, d, h, min, 0, 0, utc).toInstant()

    @Test
    fun elevationMatchesTheBrightnessTool() {
        assertTrue(Sun.elevation(at(2026, 10, 22, 10, 55), lat, lon) in 24.6..26.0)
        assertTrue(Sun.elevation(at(2026, 10, 22, 0, 0), lat, lon) in -90.0..-40.0)
        assertTrue(Sun.elevation(at(2026, 3, 20, 12, 7), 0.0, 0.0) in 87.0..90.0)
        assertTrue(Sun.elevation(at(2026, 6, 21, 10, 59), lat, lon) in 59.5..60.6)
    }

    @Test
    fun crossesTheHorizonNearSunset() {
        assertTrue(Sun.elevation(at(2026, 10, 22, 15, 28), lat, lon) > 0)
        assertTrue(Sun.elevation(at(2026, 10, 22, 16, 8), lat, lon) < 0)
    }

    @Test
    fun findsTheNextSunsetDuringTheDay() {
        val change = Sun.nextChange(at(2026, 10, 22, 10, 0), lat, lon)
        assertTrue(change.isDay)
        val local = change.at.atZone(warsaw)
        assertEquals(17, local.hour)
        assertTrue(local.minute in 30..59)
    }

    @Test
    fun findsTheNextSunriseAtNight() {
        val change = Sun.nextChange(at(2026, 10, 22, 21, 0), lat, lon)
        assertEquals(false, change.isDay)
        assertEquals(7, change.at.atZone(warsaw).hour)
    }
}
