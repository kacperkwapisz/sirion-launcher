package pl.perier.sirion.logic

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ButtonLightsTest {
    @Test
    fun alwaysOnSendsTheFrameThatLitThemWithHeadlightsOff() {
        // Tested in the car on 2026-10-23: colour lights + always on, white, full.
        assertArrayEquals(
            byteArrayOf(7, 11, 1, -1, -1, -1, 3, 100),
            ButtonLights.frame(ButtonLights.Mode.ALWAYS_ON),
        )
    }

    @Test
    fun withHeadlightsSendsTheUnitsOwnSettingsBack() {
        assertArrayEquals(
            byteArrayOf(7, 11, 3, 0, 0, 0, 0, 100),
            ButtonLights.frame(ButtonLights.Mode.WITH_HEADLIGHTS),
        )
    }

    @Test
    fun savedNamesRoundTripAndUnknownMeansWithHeadlights() {
        ButtonLights.Mode.values().forEach { assertEquals(it, ButtonLights.Mode.load(it.name)) }
        assertEquals(ButtonLights.Mode.WITH_HEADLIGHTS, ButtonLights.Mode.load(null))
        assertEquals(ButtonLights.Mode.WITH_HEADLIGHTS, ButtonLights.Mode.load("nonsense"))
    }
}
