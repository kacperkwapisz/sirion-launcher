package pl.perier.sirion.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HelperCommandTest {
    @Test
    fun tuneCarriesTheFrequencyInKilohertz() {
        assertEquals(HelperCommand.Tune(92_000), HelperCommand.parse("tune 92000"))
        assertEquals("tune 94800", HelperCommand.Tune(94_800).line)
    }

    @Test
    fun presetsTurnIntoTuneCommands() {
        assertEquals(HelperCommand.Tune(92_000), HelperCommand.tune(RadioPreset(9200, "Szczecin")))
    }

    @Test
    fun seekGoesBothWays() {
        assertEquals(HelperCommand.Seek(up = true), HelperCommand.parse("seek up"))
        assertEquals(HelperCommand.Seek(up = false), HelperCommand.parse("seek down"))
        assertEquals("seek down", HelperCommand.Seek(up = false).line)
    }

    @Test
    fun radioOffIsItsOwnCommand() {
        assertEquals(HelperCommand.RadioOff, HelperCommand.parse("radio off"))
    }

    @Test
    fun installOnlyTakesApksFromOurUpdateFolder() {
        val good = "/data/user/0/pl.perier.sirion/cache/ota/sirion-7.apk"
        assertEquals(HelperCommand.Install(good), HelperCommand.parse("install $good"))
        assertNull(HelperCommand.parse("install /sdcard/evil.apk"))
        assertNull(HelperCommand.parse("install /data/user/0/pl.perier.sirion/cache/ota/../../../evil.apk"))
        assertNull(HelperCommand.parse("install /data/user/0/pl.perier.sirion/cache/ota/notes.txt"))
    }

    @Test
    fun healthyReportsTheRunningVersion() {
        assertEquals(HelperCommand.Healthy(7), HelperCommand.parse("healthy 7"))
        assertEquals("healthy 7", HelperCommand.Healthy(7).line)
    }

    @Test
    fun unstickBluetoothClearsTheVendorFlag() {
        assertEquals(HelperCommand.UnstickBluetooth, HelperCommand.parse("bluetooth unstick"))
    }

    @Test
    fun brightnessCarriesModeAndBothLevels() {
        assertEquals(HelperCommand.Brightness("night", 210, 30), HelperCommand.parse("brightness night 210 30"))
        assertEquals("brightness auto 210 30", HelperCommand.Brightness("auto", 210, 30).line)
        assertNull(HelperCommand.parse("brightness loud 210 30"))
        assertNull(HelperCommand.parse("brightness auto 999 30"))
        assertNull(HelperCommand.parse("brightness auto 210"))
    }

    @Test
    fun garbageIsIgnored() {
        assertNull(HelperCommand.parse(""))
        assertNull(HelperCommand.parse("tune"))
        assertNull(HelperCommand.parse("tune abc"))
        assertNull(HelperCommand.parse("rm -rf /"))
    }

    @Test
    fun bluetoothClockAndRestart() {
        assertEquals(HelperCommand.Discoverable, HelperCommand.parse("bluetooth visible"))
        val pair = HelperCommand.ConfirmPairing("AA:BB:CC:00:11:22", accept = true)
        assertEquals("bluetooth pair AA:BB:CC:00:11:22 yes", pair.line)
        assertEquals(pair, HelperCommand.parse(pair.line))
        assertNull(HelperCommand.parse("bluetooth pair AA:BB yes"))
        assertNull(HelperCommand.parse("bluetooth pair AA:BB:CC:00:11:22; maybe"))
        assertEquals(HelperCommand.Clock24(false), HelperCommand.parse("clock 12"))
        assertEquals(HelperCommand.Restart, HelperCommand.parse("restart"))
    }
}
