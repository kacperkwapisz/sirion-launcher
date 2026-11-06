package pl.perier.sirion.ui.settings

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import pl.perier.sirion.R
import pl.perier.sirion.logic.HelperCommand
import pl.perier.sirion.logic.SettingsText
import pl.perier.sirion.ui.Kit
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Settings · Bluetooth, Connect first, and Pair a new phone. Android's own Bluetooth stack; no vendor app. */
class BluetoothSettings(private val host: SettingsActivity) {
    private val kit get() = host.kit
    private val keeper get() = host.app.bluetooth
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val main = Handler(Looper.getMainLooper())
    private val connecting = mutableSetOf<String>()

    private val unitName get() = adapter?.name ?: "Sirion"

    fun screen(): View {
        val on = adapter?.isEnabled == true
        val header = kit.header(
            "Bluetooth",
            if (on) "Visible to phones as \"$unitName\"" else "Off · phones can't connect",
            kit.toggle(on) { keeper.setOn(it); host.refreshSoon() },
        )
        if (!on) return kit.column(header)

        val phones = keeper.phones()
        val anyConnected = phones.any(keeper::isConnected)
        val rows = phones.map { phoneRow(it, anyConnected) } +
            kit.row("Pair a new phone", leading = kit.glyph(R.drawable.g_plus), height = 76) { host.open(::pair) }
        val first = phones.firstOrNull()
        val connectFirst = first?.let {
            kit.card(kit.row(
                "Connect first", "Which phone connects when both are in the car",
                trailing = kit.inner(it.name ?: it.address, chevron = true) { host.open(::connectFirst) },
            ))
        }
        return kit.column(header, kit.card(*rows.toTypedArray()), connectFirst)
    }

    private fun phoneRow(device: BluetoothDevice, anyConnected: Boolean): View {
        val connected = keeper.isConnected(device)
        if (connected) connecting -= device.address
        val isConnecting = device.address in connecting
        val dot = when {
            connected -> Kit.Dot.CONNECTED
            isConnecting -> Kit.Dot.CONNECTING
            else -> Kit.Dot.PAIRED
        }
        val line = when {
            connected -> "Connected · calls and music"
            isConnecting -> "Connecting"
            else -> SettingsText.lastConnected(keeper.lastConnected(device)?.let(::local), LocalDateTime.now())
        }
        val action = when {
            connected -> kit.inner("Disconnect") { keeper.disconnect(device); host.refreshSoon() }
            isConnecting -> null
            anyConnected && !keeper.switchPhones -> null
            else -> kit.pill("Connect") { connect(device) }
        }
        return kit.row(device.name ?: device.address, line, leading = kit.dot(dot), trailing = action)
    }

    fun openPairing() = host.open(::pair)

    private fun connect(device: BluetoothDevice) {
        connecting += device.address
        keeper.connect(device)
        host.render()
        main.postDelayed({ connecting -= device.address; host.refreshSoon() }, CONNECT_TIMEOUT_MS)
    }

    // Connect first

    private fun connectFirst(): View {
        val phones = keeper.phones()
        val firstName = phones.firstOrNull()?.name ?: "the first phone"
        val rows = phones.mapIndexed { i, device ->
            val number = kit.text(R.style.Text_RowTitle, "${i + 1}").apply {
                setBackgroundResource(R.drawable.bg_inner)
                gravity = Gravity.CENTER
                setTextColor(host.getColor(if (i == 0) R.color.text_primary else R.color.text_secondary))
            }
            if (i == 0) {
                kit.row(device.name ?: device.address, "Calls and music", leading = number, leadingSize = 44,
                    trailing = kit.text(R.style.Text_Context, "Connects first"))
            } else {
                kit.row(device.name ?: device.address, "Connects when $firstName isn't in the car", leading = number, leadingSize = 44,
                    trailing = kit.inner("Make first") { keeper.makeFirst(device); host.render() })
            }
        }
        val switch = kit.row(
            "Switch phones without re-pairing", "Tap Connect on the other phone and it takes over",
            trailing = kit.toggle(keeper.switchPhones) { keeper.switchPhones = it },
        )
        return kit.column(
            kit.backHeader("Connect first", "If both phones are in the car, number 1 connects", host::back),
            kit.card(*rows.toTypedArray(), switch),
        )
    }

    // Pair a new phone

    /**
     * A phone asking to pair. [variant] is Android's kind of request: confirm a code (2),
     * allow without a code (3, 6), give a PIN (0, 7), or type the unit's code on the phone (4, 5).
     */
    private data class Request(val device: BluetoothDevice, val code: Int?, val variant: Int) {
        /** The unit shows a code and the phone types it in: nothing to press here. */
        val phoneTypesCode get() = variant == VARIANT_DISPLAY_PASSKEY || variant == VARIANT_DISPLAY_PIN
        val needsPin get() = variant == VARIANT_PIN || variant == VARIANT_PIN_16
    }

    /** Pair was tapped and the answer is on its way; null when not, false when Bluetooth refused it. */
    private var sent: Boolean? = null

    private var request: Request? = null
    private var pairing = false

    private val pairingEvents = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
            when (intent.action) {
                BluetoothDevice.ACTION_PAIRING_REQUEST -> {
                    // Our screen answers it; keep Android's pairing dialog from opening on top.
                    if (isOrderedBroadcast) abortBroadcast()
                    val code = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_KEY, -1).takeIf { it >= 0 }
                    val variant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, VARIANT_PASSKEY_CONFIRMATION)
                    request = Request(device, code, variant)
                    sent = null
                    host.render()
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)) {
                    BluetoothDevice.BOND_BONDED -> {
                        stopPairing()
                        host.back()
                        connect(device)
                    }
                    BluetoothDevice.BOND_NONE -> if (request?.device?.address == device.address) {
                        request = null
                        host.render()
                    }
                }
            }
        }
    }

    private fun startPairing() {
        if (pairing) return
        pairing = true
        request = null
        sent = null
        // Bluetooth refusing the answer comes back from the helper; show it instead of waiting.
        host.app.helper.onPairing = { ok -> host.runOnUiThread { if (!ok) { sent = false; host.render() } } }
        host.app.helper.send(HelperCommand.Discoverable)
        host.registerReceiver(pairingEvents, IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            priority = IntentFilter.SYSTEM_HIGH_PRIORITY - 1
        })
    }

    fun stopPairing() {
        if (!pairing) return
        pairing = false
        host.app.helper.onPairing = null
        runCatching { host.unregisterReceiver(pairingEvents) }
    }

    private fun pair(): View {
        startPairing()
        val req = request
        val phoneName = req?.device?.name ?: "your phone"
        val second = when {
            req?.phoneTypesCode == true -> "Type this code on $phoneName"
            req?.needsPin == true -> "Pair, then type 0000 on $phoneName if it asks"
            else -> "Check that $phoneName shows the same code"
        }
        val steps = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            addView(step(1, "On your phone, open Bluetooth and choose \"$unitName\"", done = req != null, active = req == null))
            addView(step(2, second, done = false, active = req != null),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = kit.dp(20) })
        }
        val box = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_inner)
            setPadding(kit.dp(20), kit.dp(20), kit.dp(20), kit.dp(20))
            addView(kit.text(R.style.Text_Context, "Code"))
            val big = when {
                req == null -> kit.text(R.style.Text_Context, "Shows here when your phone asks").apply { maxLines = 2 }
                sent == false -> kit.text(R.style.Text_Context, "Didn't pair. Choose \"$unitName\" on $phoneName again").apply { maxLines = 3 }
                req.code == null -> kit.text(R.style.Text_Context, "$phoneName wants to pair")
                else -> kit.text(R.style.Text_Code, SettingsText.pairingCode(req.code))
            }
            addView(big, LinearLayout.LayoutParams(-2, 0, 1f).apply { topMargin = kit.dp(12) })
            (big as TextView).gravity = Gravity.CENTER_VERTICAL
            if (req != null && !req.phoneTypesCode && sent != false) {
                addView(LinearLayout(host).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(kit.pillSecondary("Cancel") {
                        host.app.helper.send(HelperCommand.ConfirmPairing(req.device.address, accept = false))
                        request = null
                        host.back()
                    }, LinearLayout.LayoutParams(0, kit.dp(64), 1f))
                    addView(kit.pill(if (sent == true) "Pairing…" else "Pair") {
                        if (sent == true) return@pill
                        sent = true
                        host.app.helper.send(
                            if (req.needsPin) HelperCommand.PairingPin(req.device.address, DEFAULT_PIN)
                            else HelperCommand.ConfirmPairing(req.device.address, accept = true),
                        )
                        host.render()
                    }.apply { isEnabled = sent != true }, LinearLayout.LayoutParams(0, kit.dp(64), 1f).apply { marginStart = kit.dp(8) })
                })
            }
        }
        val body = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundResource(R.drawable.bg_card)
            setPadding(kit.dp(24), kit.dp(24), kit.dp(24), kit.dp(24))
            addView(steps, LinearLayout.LayoutParams(0, -2, 1f))
            addView(box, LinearLayout.LayoutParams(kit.dp(300), kit.dp(320)).apply { marginStart = kit.dp(24) })
        }
        return kit.column(
            kit.backHeader("Pair a new phone", "$unitName stays visible for 2 minutes", host::back),
            body,
        )
    }

    private fun step(number: Int, text: String, done: Boolean, active: Boolean) = LinearLayout(host).apply {
        orientation = LinearLayout.HORIZONTAL
        val badge = if (done) android.widget.ImageView(host).apply { setImageResource(R.drawable.g_check_circle) }
        else kit.text(R.style.Text_StepNumber, "$number").apply {
            gravity = Gravity.CENTER
            setBackgroundResource(if (active) R.drawable.bg_step_active else R.drawable.bg_step)
            setTextColor(host.getColor(if (active) R.color.text_on_light else R.color.text_secondary))
        }
        addView(badge, LinearLayout.LayoutParams(kit.dp(26), kit.dp(26)).apply { topMargin = kit.dp(1) })
        addView(kit.text(R.style.Text_Step, text).apply {
            setTextColor(host.getColor(if (active) R.color.text_primary else R.color.text_secondary))
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = kit.dp(14) })
    }

    private fun local(epochMs: Long) = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault())

    private companion object {
        const val CONNECT_TIMEOUT_MS = 20_000L
        /** BluetoothDevice.PAIRING_VARIANT_* (some are hidden on 8.1). */
        const val VARIANT_PIN = 0
        const val VARIANT_PASSKEY_CONFIRMATION = 2
        const val VARIANT_DISPLAY_PASSKEY = 4
        const val VARIANT_DISPLAY_PIN = 5
        const val VARIANT_PIN_16 = 7
        /** What car units answer a PIN request with; the phone asks for the same. */
        const val DEFAULT_PIN = "0000"
    }
}
