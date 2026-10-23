package pl.perier.sirion.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallStateTest {
    @Test
    fun mapsHandsFreeStatesToScreens() {
        assertEquals(CallScreen.Incoming, CallScreen.from(4))
        assertEquals(CallScreen.Incoming, CallScreen.from(5)) // waiting
        assertEquals(CallScreen.Calling, CallScreen.from(2))  // dialing
        assertEquals(CallScreen.Calling, CallScreen.from(3))  // alerting
        assertEquals(CallScreen.InCall, CallScreen.from(0))
        assertEquals(CallScreen.InCall, CallScreen.from(1))   // held
        assertEquals(CallScreen.Ended, CallScreen.from(7))
    }

    @Test
    fun lineSaysWhatIsHappening() {
        assertEquals("Incoming call", CallScreen.Incoming.line(elapsedMs = 0))
        assertEquals("Calling…", CallScreen.Calling.line(elapsedMs = 0))
        assertEquals("04:12", CallScreen.InCall.line(elapsedMs = 252_000))
    }

    @Test
    fun numbersMatchWhateverWayTheyAreWritten() {
        assertTrue(CallScreen.sameNumber("+48 512 300 118", "512300118"))
        assertTrue(CallScreen.sameNumber("0048512300118", "+48 512-300-118"))
        assertFalse(CallScreen.sameNumber("512300118", "512300119"))
        assertFalse(CallScreen.sameNumber("", ""))
    }
}
