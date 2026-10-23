package pl.perier.sirion.logic

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class BrightnessTest {
    private val zone = ZoneId.of("Europe/Warsaw")
    private val sunrise = Sun.Change(isDay = false, at = ZonedDateTime.of(2026, 10, 23, 7, 14, 0, 0, zone).toInstant())
    private val sunset = Sun.Change(isDay = true, at = ZonedDateTime.of(2026, 10, 22, 17, 48, 0, 0, zone).toInstant())

    @Test
    fun autoSaysWhatHappensNext() {
        assertEquals("Night until sunrise 07:14", Brightness().context(sunrise, zone))
        assertEquals("Day until sunset 17:48", Brightness().context(sunset, zone))
    }

    @Test
    fun fixedModesSayTheyStay() {
        assertEquals("Stays at the day level", Brightness(mode = "day").context(sunrise, zone))
        assertEquals("Stays at the night level", Brightness(mode = "night").context(sunset, zone))
    }

    @Test
    fun sliderChangesTheLevelInUseNow() {
        val b = Brightness(mode = "auto", day = 210, night = 30)
        assertEquals(30, b.level(isDay = false))
        assertEquals(Brightness("auto", 210, 60), b.withLevel(60, isDay = false))
        assertEquals(Brightness("auto", 180, 30), b.withLevel(180, isDay = true))
        assertEquals(Brightness("night", 210, 50), Brightness("night", 210, 30).withLevel(50, isDay = true))
    }

    @Test
    fun levelsStayInRange() {
        assertEquals(255, Brightness().withLevel(400, isDay = true).day)
        // Never fully black: the screen must stay readable.
        assertEquals(Brightness.MIN_LEVEL, Brightness().withLevel(0, isDay = false).night)
    }

    @Test
    fun becomesAHelperCommand() {
        assertEquals(HelperCommand.Brightness("auto", 210, 30), Brightness().command)
    }
}
