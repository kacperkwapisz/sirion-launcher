package pl.perier.sirion.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class CarPlayFocusTest {
    @Test
    fun opensWhenTheDriverTapsIt() {
        val focus = CarPlayFocus()
        focus.driverOpened()
        assertEquals(CarPlayFocus.Action.ALLOW, focus.cameToFront())
    }

    @Test
    fun opensByItselfOnceAfterTheUnitWakes() {
        val focus = CarPlayFocus()
        focus.unitWoke()
        assertEquals(CarPlayFocus.Action.ALLOW, focus.cameToFront())
        focus.wentToBack()
        // The iPhone asking for the screen again later doesn't count.
        assertEquals(CarPlayFocus.Action.SEND_HOME, focus.cameToFront())
    }

    @Test
    fun theIphoneCantPullItForwardOnItsOwn() {
        val focus = CarPlayFocus()
        assertEquals(CarPlayFocus.Action.SEND_HOME, focus.cameToFront())
    }

    @Test
    fun aTapAllowsOneVisitUntilTheDriverLeaves() {
        val focus = CarPlayFocus()
        focus.driverOpened()
        assertEquals(CarPlayFocus.Action.ALLOW, focus.cameToFront())
        // Staying in CarPlay (repeated reports) is fine.
        assertEquals(CarPlayFocus.Action.ALLOW, focus.cameToFront())
        focus.wentToBack()
        assertEquals(CarPlayFocus.Action.SEND_HOME, focus.cameToFront())
    }

    @Test
    fun aTapLongAgoWithoutCarPlayComingUpDoesntLingerForever() {
        val focus = CarPlayFocus()
        focus.driverOpened(nowMs = 0)
        assertEquals(CarPlayFocus.Action.SEND_HOME, focus.cameToFront(nowMs = CarPlayFocus.TAP_WINDOW_MS + 1))
    }

    @Test
    fun aCallOrSiriMayBringItForwardLikeInBigBrandCars() {
        val focus = CarPlayFocus()
        assertEquals(CarPlayFocus.Action.ALLOW, focus.cameToFront(callOrVoice = true))
        // It stays while the call goes on, then the guard is back.
        assertEquals(CarPlayFocus.Action.ALLOW, focus.cameToFront())
        focus.wentToBack()
        assertEquals(CarPlayFocus.Action.SEND_HOME, focus.cameToFront())
    }
}
