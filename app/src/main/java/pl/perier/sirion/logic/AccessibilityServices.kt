package pl.perier.sirion.logic

/** Android's colon-separated list of enabled accessibility services (Settings.Secure). */
object AccessibilityServices {
    const val VOLUME_POPUP = "pl.perier.sirion/pl.perier.sirion.ui.VolumePopup"
    /** Its name in 0.3, before it watched the volume instead of keys. */
    private const val OLD_VOLUME_KEYS = "pl.perier.sirion/pl.perier.sirion.ui.VolumeKeys"

    /** [current] with our volume card in it, whatever else was on staying on. */
    fun withVolumePopup(current: String?): String {
        val services = current.orEmpty().split(':')
            .filter { it.isNotBlank() && it != "null" && it != OLD_VOLUME_KEYS }
        return (if (VOLUME_POPUP in services) services else services + VOLUME_POPUP).joinToString(":")
    }
}
