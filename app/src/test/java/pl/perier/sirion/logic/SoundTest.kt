package pl.perier.sirion.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SoundTest {
    @Test fun `presets set the three bands`() {
        assertEquals(Triple(0, 0, 0), Sound().withPreset(Sound.Preset.FLAT).bands)
        assertEquals(Triple(6, 0, 2), Sound().withPreset(Sound.Preset.BASS).bands)
        assertEquals(Triple(-2, 4, 2), Sound().withPreset(Sound.Preset.VOICE).bands)
    }

    @Test fun `custom keeps what the driver set`() {
        val custom = Sound().withBand(Sound.Band.BASS, 3).withBand(Sound.Band.TREBLE, -2)
        assertEquals(Sound.Preset.CUSTOM, custom.preset)
        val flat = custom.withPreset(Sound.Preset.FLAT)
        assertEquals(Triple(3, 0, -2), flat.withPreset(Sound.Preset.CUSTOM).bands)
    }

    @Test fun `bands are clamped to the safe range`() {
        assertEquals(10, Sound().withBand(Sound.Band.BASS, 15).bass)
        assertEquals(-10, Sound().withBand(Sound.Band.MIDDLE, -40).middle)
    }

    @Test fun `decibel text uses a real minus sign`() {
        assertEquals("+3 dB", Sound.decibels(3))
        assertEquals("0 dB", Sound.decibels(0))
        assertEquals("−2 dB", Sound.decibels(-2))
    }

    @Test fun `header line names the preset`() {
        assertEquals("Custom, and it stays after a restart", Sound().withBand(Sound.Band.BASS, 1).line)
        assertEquals("Flat · no boost or cut", Sound().line)
    }

    @Test fun `balance and fader read as a person would say them`() {
        assertEquals("Centre", Sound.balanceText(0))
        assertEquals("3 left", Sound.balanceText(-3))
        assertEquals("2 right", Sound.balanceText(2))
        assertEquals("2 front", Sound.faderText(2))
        assertEquals("1 rear", Sound.faderText(-1))
    }

    @Test fun `position row says centre, a jump name, or the two values`() {
        assertEquals("Centre", Sound().positionText)
        assertEquals("Driver", Sound().withPosition(Sound.Jump.DRIVER.balance, Sound.Jump.DRIVER.fader).positionText)
        assertEquals("4 right · 1 rear", Sound().withPosition(4, -1).positionText)
    }

    @Test fun `centred sound leaves every speaker at full level`() {
        assertEquals(listOf(0, 0, 0, 0), Sound().trims)
    }

    @Test fun `moving left and front turns the right and rear speakers down`() {
        // Order: front left, front right, rear left, rear right.
        assertEquals(listOf(0, -6, -4, -10), Sound().withPosition(-3, 2).trims)
    }

    @Test fun `position is clamped`() {
        val s = Sound().withPosition(-99, 99)
        assertEquals(-Sound.POSITION_STEPS, s.balance)
        assertEquals(Sound.POSITION_STEPS, s.fader)
    }

    @Test fun `effects cover the bands and the four speakers`() {
        val effects = Sound().withPreset(Sound.Preset.BASS).withPosition(-1, 0).effects
        assertEquals(
            listOf(10 to 6, 14 to 0, 17 to 2, 0 to 0, 1 to -2, 2 to 0, 3 to -2),
            effects.map { it.id to it.value },
        )
    }

    @Test fun `saves and loads`() {
        val s = Sound().withBand(Sound.Band.MIDDLE, 4).withPreset(Sound.Preset.VOICE).withPosition(-3, 2)
        assertEquals(s, Sound.load(s.save()))
        assertEquals(Sound(), Sound.load(null))
        assertEquals(Sound(), Sound.load("garbage"))
    }

    @Test fun `helper accepts only known effects in range`() {
        assertEquals(HelperCommand.Effect(10, -3), HelperCommand.parse("effect 10 -3"))
        assertEquals(HelperCommand.Effect(0, -80), HelperCommand.parse("effect 0 -80"))
        assertNull(HelperCommand.parse("effect 23 5"))
        assertNull(HelperCommand.parse("effect 10 40"))
        assertNull(HelperCommand.parse("effect 3 4"))
    }
}
