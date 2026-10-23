package pl.perier.sirion.logic

import java.time.LocalDateTime

/** Words for the settings screens. */
object SettingsText {
    fun lastConnected(time: LocalDateTime?, now: LocalDateTime) =
        if (time == null) "Paired" else "Last connected " + PhoneText.whenText(time, now)

    fun percent(level: Int) = "${(level * 100 + 127) / 255}%"

    fun savedNetworks(count: Int) = when (count) {
        0 -> "No saved networks"
        1 -> "1 saved network"
        else -> "$count saved networks"
    }

    /** Android wraps SSIDs in quotes and says `<unknown ssid>` when there's none. */
    fun ssid(raw: String?): String? {
        val name = raw?.removeSurrounding("\"")?.trim().orEmpty()
        return name.takeIf { it.isNotEmpty() && it != "<unknown ssid>" }
    }

    fun pairingCode(code: Int) = "%06d".format(code).let { it.take(3) + " " + it.drop(3) }
}
