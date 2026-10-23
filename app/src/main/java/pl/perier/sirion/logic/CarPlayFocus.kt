package pl.perier.sirion.logic

/**
 * Whether CarPlay may be on screen. ZLink comes to the front whenever the
 * iPhone asks for the screen; the driver wants it when they open it, once on
 * its own after the unit wakes, and for a call or Siri (as in factory cars).
 */
class CarPlayFocus {
    enum class Action { ALLOW, SEND_HOME }

    /** When the driver last tapped to open CarPlay, or null. */
    private var tappedAt: Long? = null
    private var wakeAllowance = false
    /** CarPlay is on screen with permission; it may stay until it leaves. */
    private var visiting = false

    fun driverOpened(nowMs: Long = System.currentTimeMillis()) {
        tappedAt = nowMs
    }

    fun unitWoke() {
        wakeAllowance = true
    }

    /** [callOrVoice]: the unit's audio is in call or voice mode (a CarPlay call or Siri). */
    fun cameToFront(nowMs: Long = System.currentTimeMillis(), callOrVoice: Boolean = false): Action {
        if (visiting) return Action.ALLOW
        val tapped = tappedAt?.let { nowMs - it <= TAP_WINDOW_MS } == true
        if (tapped || wakeAllowance || callOrVoice) {
            visiting = true
            tappedAt = null
            wakeAllowance = false
            return Action.ALLOW
        }
        return Action.SEND_HOME
    }

    fun wentToBack() {
        visiting = false
    }

    companion object {
        /** A tap counts if CarPlay comes up within this long (ZLink can be slow to start). */
        const val TAP_WINDOW_MS = 15_000L
    }
}
