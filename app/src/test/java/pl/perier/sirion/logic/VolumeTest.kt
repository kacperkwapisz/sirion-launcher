package pl.perier.sirion.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class VolumeTest {
    @Test
    fun stepsUpAndDownByOne() {
        assertEquals(19, Volume.step(current = 18, delta = 1, max = 40))
        assertEquals(17, Volume.step(current = 18, delta = -1, max = 40))
    }

    @Test
    fun staysInsideTheRange() {
        assertEquals(40, Volume.step(current = 40, delta = 1, max = 40))
        assertEquals(0, Volume.step(current = 0, delta = -1, max = 40))
    }

    @Test
    fun muteRemembersTheLevelAndUnmuteBringsItBack() {
        assertEquals(Volume.Mute(volume = 0, remembered = 12), Volume.toggleMute(current = 12, remembered = 0, max = 30))
        assertEquals(Volume.Mute(volume = 12, remembered = 12), Volume.toggleMute(current = 0, remembered = 12, max = 30))
    }

    @Test
    fun unmuteWithNothingRememberedUsesAQuietLevel() {
        assertEquals(Volume.Mute(volume = Volume.UNMUTE_DEFAULT, remembered = 0), Volume.toggleMute(current = 0, remembered = 0, max = 30))
        assertEquals(Volume.Mute(volume = 5, remembered = 9), Volume.toggleMute(current = 0, remembered = 9, max = 5))
    }

    @Test
    fun plusOrMinusWhileMutedCarriesOnFromTheRememberedLevel() {
        assertEquals(13, Volume.stepFrom(current = 0, remembered = 12, delta = +1, max = 30))
        assertEquals(11, Volume.stepFrom(current = 0, remembered = 12, delta = -1, max = 30))
        assertEquals(1, Volume.stepFrom(current = 0, remembered = 0, delta = +1, max = 30))
        assertEquals(8, Volume.stepFrom(current = 7, remembered = 12, delta = +1, max = 30))
    }
}
