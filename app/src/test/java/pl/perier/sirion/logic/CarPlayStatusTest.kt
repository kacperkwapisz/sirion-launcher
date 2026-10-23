package pl.perier.sirion.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class CarPlayStatusTest {
    @Test
    fun connectedWhenZlinkSaysWirelessCarPlay() {
        val status = CarPlayStatus.from(connected = "1", phoneType = "carplay_wireless", bluetoothPhone = null)
        assertEquals(CarPlayStatus.Connected(null), status)
    }

    @Test
    fun wiredCarPlayCountsAsConnected() {
        val status = CarPlayStatus.from(connected = "1", phoneType = "carplay_wired", bluetoothPhone = null)
        assertEquals(CarPlayStatus.Connected(null), status)
    }

    @Test
    fun androidAutoIsNotCarPlay() {
        val status = CarPlayStatus.from(connected = "1", phoneType = "android_auto", bluetoothPhone = null)
        assertEquals(CarPlayStatus.Searching, status)
    }

    @Test
    fun notStartedWhenPhoneIsOnBluetoothButCarPlayIsOff() {
        val status = CarPlayStatus.from(connected = "0", phoneType = "", bluetoothPhone = "Kacper's iPhone")
        assertEquals(CarPlayStatus.NotStarted("Kacper's iPhone"), status)
    }

    @Test
    fun searchingWhenNothingIsConnected() {
        assertEquals(CarPlayStatus.Searching, CarPlayStatus.from(connected = "", phoneType = "", bluetoothPhone = null))
    }

    @Test
    fun cardTextSaysTheRealState() {
        assertEquals("Connected", CarPlayStatus.Connected("Kacper's iPhone").context)
        assertEquals("Kacper's iPhone", CarPlayStatus.Connected("Kacper's iPhone").line)
        assertEquals("iPhone", CarPlayStatus.Connected(null).line)
        assertEquals("Not started", CarPlayStatus.NotStarted("Kacper's iPhone").context)
        assertEquals("Start on Kacper's iPhone", CarPlayStatus.NotStarted("Kacper's iPhone").line)
        assertEquals("Searching for iPhone", CarPlayStatus.Searching.context)
        assertEquals("Turn on Bluetooth and Wi-Fi on your iPhone", CarPlayStatus.Searching.line)
    }
}
