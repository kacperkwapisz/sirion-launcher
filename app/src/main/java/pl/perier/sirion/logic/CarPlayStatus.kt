package pl.perier.sirion.logic

/**
 * What the CarPlay card says. ZLink publishes its state as system
 * properties (`rw.zlink.connected`, `rw.zlink.phone.type`); the Bluetooth
 * phone name tells "not started" apart from "no iPhone at all".
 */
sealed class CarPlayStatus(val context: String, val line: String, val live: Boolean) {
    data class Connected(val phone: String?) : CarPlayStatus("Connected", phone ?: "iPhone", live = true)

    data class NotStarted(val phone: String) :
        CarPlayStatus("Not started", "Start on $phone", live = false)

    object Searching :
        CarPlayStatus("Searching for iPhone", "Turn on Bluetooth and Wi-Fi on your iPhone", live = false)

    companion object {
        fun from(connected: String, phoneType: String, bluetoothPhone: String?): CarPlayStatus = when {
            connected == "1" && phoneType.startsWith("carplay") -> Connected(bluetoothPhone)
            bluetoothPhone != null -> NotStarted(bluetoothPhone)
            else -> Searching
        }
    }
}
