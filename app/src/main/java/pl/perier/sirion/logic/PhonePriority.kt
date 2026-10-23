package pl.perier.sirion.logic

/**
 * "Connect first": which paired phone to try next. The vendor app only ever
 * retried the one phone it last saw, so a second driver's phone never got a
 * turn; this walks the whole list.
 */
object PhonePriority {
    fun next(order: List<String>, bonded: Set<String>, lastTried: String?, onlyFirst: Boolean = false): String? {
        val all = order.filter { it in bonded } + (bonded - order.toSet()).sorted()
        val candidates = if (onlyFirst) all.take(1) else all
        if (candidates.isEmpty()) return null
        val index = candidates.indexOf(lastTried)
        return candidates[(index + 1) % candidates.size]
    }

    /** A phone that connects is added at the end if it's new; the driver's order stays as set. */
    fun remember(order: List<String>, address: String): List<String> = if (address in order) order else order + address

    fun promote(order: List<String>, address: String): List<String> = listOf(address) + (order - address)

    /**
     * Once per CarPlay session, so a driver who turns Bluetooth back on
     * during CarPlay keeps it.
     */
    fun shouldTurnBluetoothOffForCarPlay(bluetoothOn: Boolean, carPlayConnected: Boolean, alreadyDone: Boolean) =
        bluetoothOn && carPlayConnected && !alreadyDone

    fun shouldTurnBluetoothOn(bluetoothOn: Boolean, carPlayConnected: Boolean) = !bluetoothOn && !carPlayConnected
}
