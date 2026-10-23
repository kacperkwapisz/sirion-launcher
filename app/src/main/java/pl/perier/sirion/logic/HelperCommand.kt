package pl.perier.sirion.logic

/**
 * One line sent from the launcher to the root helper. The helper accepts
 * nothing else, so a stray connection can't make it run arbitrary commands.
 */
sealed class HelperCommand(val line: String) {
    data class Tune(val kilohertz: Int) : HelperCommand("tune $kilohertz")
    data class Seek(val up: Boolean) : HelperCommand(if (up) "seek up" else "seek down")
    object RadioOff : HelperCommand("radio off")
    data class Install(val apk: String) : HelperCommand("install $apk")
    data class Healthy(val versionCode: Int) : HelperCommand("healthy $versionCode")

    /** Backlight mode (auto, day, night) and the two levels, 0-255. */
    data class Brightness(val mode: String, val day: Int, val night: Int) : HelperCommand("brightness $mode $day $night")

    /** One value on the TDA7729 audio chip: tone bands 10/14/17 (±15 dB) or speaker trims 0-3 (0 to -80 dB). */
    data class Effect(val id: Int, val value: Int) : HelperCommand("effect $id $value")

    /** Makes the unit visible to phones for two minutes, for pairing. */
    object Discoverable : HelperCommand("bluetooth visible")

    /** Answers a phone's pairing request (the codes match, or not). */
    data class ConfirmPairing(val address: String, val accept: Boolean) :
        HelperCommand("bluetooth pair $address ${if (accept) "yes" else "no"}")

    data class Clock24(val on: Boolean) : HelperCommand(if (on) "clock 24" else "clock 12")

    object Restart : HelperCommand("restart")

    /** Clears the vendor's `zlink_bt_off` flag, which can leave Bluetooth off for good. */
    object UnstickBluetooth : HelperCommand("bluetooth unstick")

    companion object {
        /** Updates may only be installed from the launcher's own cache folder. */
        const val UPDATE_DIR = "/data/user/0/pl.perier.sirion/cache/ota/"

        fun tune(preset: RadioPreset) = Tune(preset.centiMhz * 10)

        fun parse(line: String): HelperCommand? {
            val parts = line.trim().split(' ')
            return when {
                parts.size == 2 && parts[0] == "tune" -> parts[1].toIntOrNull()?.let(::Tune)
                parts == listOf("seek", "up") -> Seek(up = true)
                parts == listOf("seek", "down") -> Seek(up = false)
                parts == listOf("radio", "off") -> RadioOff
                parts == listOf("bluetooth", "unstick") -> UnstickBluetooth
                parts.size == 4 && parts[0] == "brightness" -> brightness(parts[1], parts[2], parts[3])
                parts.size == 2 && parts[0] == "install" -> parts[1].takeIf(::isUpdateApk)?.let(::Install)
                parts.size == 3 && parts[0] == "effect" -> effect(parts[1], parts[2])
                parts == listOf("bluetooth", "visible") -> Discoverable
                parts.size == 4 && parts[0] == "bluetooth" && parts[1] == "pair" && MAC.matches(parts[2]) && parts[3] in listOf("yes", "no") ->
                    ConfirmPairing(parts[2], parts[3] == "yes")
                parts == listOf("clock", "24") -> Clock24(true)
                parts == listOf("clock", "12") -> Clock24(false)
                parts == listOf("restart") -> Restart
                parts.size == 2 && parts[0] == "healthy" -> parts[1].toIntOrNull()?.let(::Healthy)
                else -> null
            }
        }

        private val MAC = Regex("[0-9A-F]{2}(:[0-9A-F]{2}){5}")

        private fun effect(id: String, value: String): Effect? {
            val i = id.toIntOrNull() ?: return null
            val v = value.toIntOrNull() ?: return null
            val ok = when (i) {
                10, 14, 17 -> v in -15..15
                0, 1, 2, 3 -> v in -80..0
                else -> false
            }
            return if (ok) Effect(i, v) else null
        }

        private fun brightness(mode: String, day: String, night: String): Brightness? {
            if (mode !in listOf("auto", "day", "night")) return null
            val d = day.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
            val n = night.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
            return Brightness(mode, d, n)
        }

        private fun isUpdateApk(path: String) =
            path.startsWith(UPDATE_DIR) && path.endsWith(".apk") && ".." !in path && path.count { it == '/' } == UPDATE_DIR.count { it == '/' }
    }
}
