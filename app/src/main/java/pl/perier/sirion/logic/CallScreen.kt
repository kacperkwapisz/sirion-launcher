package pl.perier.sirion.logic

/** Which call screen to show, from the hands-free profile's call state. */
enum class CallScreen {
    Incoming, Calling, InCall, Ended;

    fun line(elapsedMs: Long): String = when (this) {
        Incoming -> "Incoming call"
        Calling -> "Calling…"
        InCall -> TimeFormat.callTimer(elapsedMs)
        Ended -> "Call ended"
    }

    companion object {
        /** BluetoothHeadsetClientCall states on Android 8.1. */
        fun from(state: Int): CallScreen = when (state) {
            4, 5 -> Incoming
            2, 3 -> Calling
            0, 1, 6 -> InCall
            else -> Ended
        }

        /** Compares the last 9 digits, so +48, 0048 and spaces don't matter. */
        fun sameNumber(a: String, b: String): Boolean {
            val left = PhoneText.numberKey(a)
            return left.isNotEmpty() && left == PhoneText.numberKey(b)
        }
    }
}
