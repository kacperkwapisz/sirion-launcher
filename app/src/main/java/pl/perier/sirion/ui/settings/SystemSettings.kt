package pl.perier.sirion.ui.settings

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.content.Intent
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import pl.perier.sirion.R
import pl.perier.sirion.logic.HelperCommand
import pl.perier.sirion.ui.UpdatingActivity
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
        return kit.column(header, updateCard(), kit.card(clock))
    }

    /** Shown only while a downloaded update waits for the driver. */
    private fun updateCard(): View? {
        val update = host.app.updater.ready ?: return null
        return kit.card(kit.row(
            "Update ready", "Sirion ${update.versionName} is downloaded",
            trailing = kit.pill("See what's new") { host.open(::update) },
        ))
    }

    private fun update(): View {
        val update = host.app.updater.ready ?: return screen()
        val install = kit.pill("Install") {
            host.startActivity(Intent(host, UpdatingActivity::class.java).putExtra(UpdatingActivity.EXTRA_VERSION, update.versionName))
        }
        // Every version since the installed one, newest first, so a skipped release's notes aren't lost.
        val releases = update.changesSince(host.app.updater.installedVersion)
        val changes = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card)
            setPadding(kit.dp(20), kit.dp(20), kit.dp(20), kit.dp(20))
            releases.forEachIndexed { i, release ->
                val label = if (releases.size == 1) "What changed" else "Sirion ${release.versionName}"
                addView(kit.text(R.style.Text_Context, label), LinearLayout.LayoutParams(-1, -2).apply { if (i > 0) topMargin = kit.dp(24) })
                release.notes.ifEmpty { listOf("Fixes and small changes") }.forEach { line ->
                    addView(kit.text(R.style.Text, line).apply { setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22f); maxLines = 3 },
                        LinearLayout.LayoutParams(-1, -2).apply { topMargin = kit.dp(14) })
                }
            }
        }
        return kit.column(
            kit.backHeader("Sirion ${update.versionName}", "Takes about a minute. The launcher restarts on its own", host::back, install),
            changes,
        )
    }

    private companion object {
        const val CONFIRM_MS = 4_000L
    }
}
