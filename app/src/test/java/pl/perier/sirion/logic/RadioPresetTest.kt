package pl.perier.sirion.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RadioPresetTest {
    @Test
    fun labelShowsOneDecimal() {
        assertEquals("92.0", RadioPreset(9200, "Szczecin").label)
        assertEquals("101.7", RadioPreset(10170, "Eska").label)
    }

    @Test
    fun parsesTypedFrequencies() {
        assertEquals(9480, RadioPreset.parseFm("94.8"))
        assertEquals(9200, RadioPreset.parseFm("92"))
        assertEquals(10170, RadioPreset.parseFm("101.7"))
    }

    @Test
    fun rejectsFrequenciesOutsideFm() {
        assertNull(RadioPreset.parseFm("86.0"))
        assertNull(RadioPreset.parseFm("108.5"))
        assertNull(RadioPreset.parseFm("abc"))
    }

    @Test
    fun defaultPresetsAreTheFourOnTheCard() {
        assertEquals(listOf("92.0", "94.8", "99.6", "101.7"), RadioPreset.defaults.map { it.label })
    }
}
