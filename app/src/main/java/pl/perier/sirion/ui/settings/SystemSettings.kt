package pl.perier.sirion.ui.settings

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.view.View
import pl.perier.sirion.logic.HelperCommand
import java.time.ZoneId

/** Settings · System: what's running, a restart, and the clock. */
class SystemSettings(private val host: SettingsActivity) {
    private val kit get() = host.kit
    private val main = Handler(Looper.getMainLooper())
    private var confirmRestart = false

    fun screen(): View {
        val version = host.packageManager.getPackageInfo(host.packageName, 0).versionName
        val restart = kit.inner(if (confirmRestart) "Tap again to restart" else "Restart") {
            if (confirmRestart) {
                host.app.helper.send(HelperCommand.Restart)
            } else {
                // A restart takes the screen away for a minute, so it asks twice.
                confirmRestart = true
                host.render()
                main.postDelayed({ confirmRestart = false; host.render() }, CONFIRM_MS)
            }
        }
        val header = kit.header("System", "Sirion launcher $version · Android ${Build.VERSION.RELEASE.removeSuffix(".0")}", restart)
        val zone = ZoneId.systemDefault().id.substringAfterLast('/').replace('_', ' ')
        val clock = kit.row(
            "24-hour clock", "Time zone $zone",
            trailing = kit.toggle(DateFormat.is24HourFormat(host)) { on ->
                host.app.helper.send(HelperCommand.Clock24(on))
                main.postDelayed({ host.render() }, 500)
            },
        )
        return kit.column(header, kit.card(clock))
    }

    private companion object {
        const val CONFIRM_MS = 4_000L
    }
}
