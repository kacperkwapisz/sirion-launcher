package pl.perier.sirion.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TunerTest {
    @Test
    fun stepsByOneTenthOfAMegahertzAndWraps() {
        assertEquals(92_100, Tuner.step(92_000, up = true))
        assertEquals(91_900, Tuner.step(92_000, up = false))
        assertEquals(87_500, Tuner.step(108_000, up = true))
        assertEquals(108_000, Tuner.step(87_500, up = false))
    }

    @Test
    fun labelsAndPresetsAgree() {
        assertEquals("94.8", Tuner.label(94_800))
        assertEquals(94_800, Tuner.kilohertz(RadioPreset(9480, "RMF FM")))
    }

    @Test
    fun readsTheFrequencyWhateverUnitTheTunerUses() {
        // The library's status format isn't documented, so accept the common encodings.
        assertEquals(94_800, Tuner.frequencyFrom("""{"Action":"Freq","Freq":94800}"""))
        assertEquals(94_800, Tuner.frequencyFrom("""{"Action":"Freq","Freq":9480}"""))
        assertEquals(94_800, Tuner.frequencyFrom("""{"Action":"Freq","Freq":948}"""))
        assertEquals(94_800, Tuner.frequencyFrom("""{"Freq":"94.8"}"""))
        assertNull(Tuner.frequencyFrom("""{"Action":"Stereo","STEREO":1}"""))
        assertNull(Tuner.frequencyFrom("""{"Freq":5}"""))
    }

    @Test
    fun readsStereoAndStationName() {
        assertEquals(true, Tuner.stereoFrom("""{"Action":"Stereo","STEREO":1}"""))
        assertEquals(false, Tuner.stereoFrom("""{"STEREO":0}"""))
        assertNull(Tuner.stereoFrom("""{"Freq":948}"""))
        assertEquals("RMF FM", Tuner.stationFrom("""{"Action":"RDS","PS":"RMF FM  "}"""))
        assertNull(Tuner.stationFrom("""{"PS":"   "}"""))
    }

    @Test
    fun presetsKeepEightSlots() {
        val presets = Tuner.savePreset(RadioPreset.defaults, slot = 5, preset = RadioPreset(8840, "Trójka"))
        assertEquals(8, presets.size)
        assertEquals(RadioPreset(8840, "Trójka"), presets[5])
        assertNull(Tuner.savePreset(RadioPreset.defaults, slot = 9, preset = RadioPreset(8840, "Trójka")).getOrNull(9))
    }
}
