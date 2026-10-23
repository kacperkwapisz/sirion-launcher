package pl.perier.sirion.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhonePriorityTest {
    private val kacper = "AA:00:00:00:00:01"
    private val work = "AA:00:00:00:00:02"
    private val other = "AA:00:00:00:00:03"

    @Test
    fun tryPhonesInPriorityOrderThenWrapAround() {
        val order = listOf(kacper, work)
        val bonded = setOf(kacper, work)
        assertEquals(kacper, PhonePriority.next(order, bonded, lastTried = null))
        assertEquals(work, PhonePriority.next(order, bonded, lastTried = kacper))
        assertEquals(kacper, PhonePriority.next(order, bonded, lastTried = work))
    }

    @Test
    fun skipsPhonesThatAreNoLongerPaired() {
        assertEquals(work, PhonePriority.next(listOf(kacper, work), setOf(work), lastTried = null))
    }

    @Test
    fun pairedPhonesMissingFromTheListComeLast() {
        val order = listOf(kacper)
        val bonded = setOf(kacper, other)
        assertEquals(other, PhonePriority.next(order, bonded, lastTried = kacper))
    }

    @Test
    fun nothingToTryWithoutPairedPhones() {
        assertNull(PhonePriority.next(listOf(kacper), emptySet(), lastTried = null))
    }

    @Test
    fun makeFirstMovesAPhoneToTheFront() {
        assertEquals(listOf(work, kacper), PhonePriority.promote(listOf(kacper, work), work))
        assertEquals(listOf(other, kacper, work), PhonePriority.promote(listOf(kacper, work), other))
    }

    @Test
    fun aNewPhoneThatConnectsGoesLastAndKnownOnesStayPut() {
        assertEquals(listOf(kacper, work, other), PhonePriority.remember(listOf(kacper, work), other))
        assertEquals(listOf(kacper, work), PhonePriority.remember(listOf(kacper, work), work))
    }

    @Test
    fun withoutSwitchingOnlyTheFirstPhoneIsTried() {
        val bonded = setOf(kacper, work)
        assertEquals(kacper, PhonePriority.next(listOf(kacper, work), bonded, lastTried = kacper, onlyFirst = true))
    }

    @Test
    fun bluetoothIsForcedOnUnlessCarPlayIsRunning() {
        // The vendor turns Bluetooth off for CarPlay and can leave it stuck off.
        assertTrue(PhonePriority.shouldTurnBluetoothOn(bluetoothOn = false, carPlayConnected = false))
        assertFalse(PhonePriority.shouldTurnBluetoothOn(bluetoothOn = true, carPlayConnected = false))
        assertFalse(PhonePriority.shouldTurnBluetoothOn(bluetoothOn = false, carPlayConnected = true))
    }

    @Test
    fun bluetoothGoesOffOnceWhenCarPlayStarts() {
        // With the vendor Bluetooth app off, we do its one useful job: the iPhone's
        // calls and audio go through CarPlay, not also over Bluetooth.
        assertTrue(PhonePriority.shouldTurnBluetoothOffForCarPlay(bluetoothOn = true, carPlayConnected = true, alreadyDone = false))
        assertFalse(PhonePriority.shouldTurnBluetoothOffForCarPlay(bluetoothOn = true, carPlayConnected = true, alreadyDone = true))
        assertFalse(PhonePriority.shouldTurnBluetoothOffForCarPlay(bluetoothOn = true, carPlayConnected = false, alreadyDone = false))
        assertFalse(PhonePriority.shouldTurnBluetoothOffForCarPlay(bluetoothOn = false, carPlayConnected = true, alreadyDone = false))
    }
}
