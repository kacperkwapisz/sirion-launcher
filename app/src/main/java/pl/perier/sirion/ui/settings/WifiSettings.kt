package pl.perier.sirion.ui.settings

import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import pl.perier.sirion.R
import pl.perier.sirion.data.WifiNetworks
import pl.perier.sirion.logic.SettingsText
import pl.perier.sirion.ui.Kit

/** Settings · Wi-Fi, nearby networks, and Join network. Straight to Android's WifiManager. */
class WifiSettings(private val host: SettingsActivity) {
    private val kit get() = host.kit
    private val wifi = WifiNetworks(host)

    fun screen(): View {
        val on = wifi.isOn
        val saved = if (on) wifi.saved() else emptyList()
        val header = kit.header(
            "Wi-Fi",
            if (on) SettingsText.savedNetworks(saved.size) else "Off",
            kit.toggle(on) { wifi.isOn = it; host.refreshSoon() },
        )
        if (!on) return kit.column(header)

        val rows = saved.map { network ->
            if (network.connected) {
                kit.row(network.name, "Connected · used for updates", leading = kit.dot(Kit.Dot.CONNECTED),
                    trailing = kit.inner("Disconnect") { wifi.disconnect(); host.refreshSoon() })
            } else {
                kit.row(network.name, "Saved", leading = kit.dot(Kit.Dot.PAIRED),
                    trailing = kit.pill("Join") { wifi.join(network.id); host.refreshSoon() })
            }
        } + kit.row("Join another network", leading = kit.glyph(R.drawable.g_plus), height = 76) {
            wifi.scan()
            host.open(::nearby)
        }
        val carPlay = kit.card(kit.row("CarPlay uses this Wi-Fi", "Joining another network pauses CarPlay for a few seconds"))
        return kit.column(header, kit.card(*rows.toTypedArray()), carPlay)
    }

    private fun nearby(): View {
        val networks = wifi.nearby()
        val rows = networks.map { n ->
            kit.row(n.name, if (n.secured) "Needs a password" else "Open",
                trailing = kit.pill("Join") {
                    if (n.secured) host.open { join(n.name) } else { wifi.join(n.name, ""); host.back() }
                })
        }
        val list = if (rows.isEmpty()) kit.card(kit.row("Looking for networks", "Nearby networks show here in a few seconds"))
        else kit.card(*rows.toTypedArray())
        return kit.column(kit.backHeader("Join another network", "Networks the unit can see now", host::back), list)
    }

    /** Kept while open, so a Wi-Fi refresh doesn't wipe what's been typed. */
    private var joinScreen: Pair<String, View>? = null

    private fun join(name: String): View =
        joinScreen?.takeIf { it.first == name }?.second ?: buildJoin(name).also { joinScreen = name to it }

    private fun leaveJoin() {
        joinScreen = null
        host.back()
    }

    private fun buildJoin(name: String): View {
        val field = EditText(host, null, 0, R.style.Text_RowTitle).apply {
            hint = "Password"
            setHintTextColor(host.getColor(R.color.text_secondary))
            setTextColor(host.getColor(R.color.text_primary))
            setBackgroundResource(R.drawable.bg_field)
            setPadding(kit.dp(20), 0, kit.dp(20), 0)
            minHeight = kit.dp(64)
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            // Keep the keyboard below the field instead of a full-screen text box.
            imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_ACTION_DONE
        }
        val connect = {
            hideKeyboard(field)
            wifi.join(name, field.text.toString())
            leaveJoin()
            host.back()
        }
        field.setOnEditorActionListener { _, action, _ -> (action == EditorInfo.IME_ACTION_DONE).also { if (it) connect() } }
        val show = kit.inner("Show") {}
        show.setOnClickListener {
            val hidden = field.inputType and InputType.TYPE_TEXT_VARIATION_PASSWORD != 0
            field.inputType = InputType.TYPE_CLASS_TEXT or
                if (hidden) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else InputType.TYPE_TEXT_VARIATION_PASSWORD
            field.setSelection(field.text.length)
            show.text = if (hidden) "Hide" else "Show"
        }
        val body = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundResource(R.drawable.bg_card)
            setPadding(kit.dp(12), kit.dp(12), kit.dp(12), kit.dp(12))
            addView(field, LinearLayout.LayoutParams(0, kit.dp(64), 1f))
            addView(show, LinearLayout.LayoutParams(-2, kit.dp(64)).apply { marginStart = kit.dp(8) })
        }
        field.post {
            field.requestFocus()
            host.getSystemService(InputMethodManager::class.java).showSoftInput(field, 0)
        }
        return kit.column(
            kit.backHeader("Join \"$name\"", "CarPlay pauses while you join", ::leaveJoin, kit.pill("Join") { connect() }),
            body,
        )
    }

    private fun hideKeyboard(view: View) =
        host.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(view.windowToken, 0)
}
