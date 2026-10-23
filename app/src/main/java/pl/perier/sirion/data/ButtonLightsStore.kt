package pl.perier.sirion.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import pl.perier.sirion.logic.ButtonLights

/**
 * Whether the unit's button lights follow the headlights or stay on. Sent to
 * the MCU through the vendor service's open broadcast (no root needed), and
 * again after every start and ignition on, a few seconds late so it lands
 * after the unit sets its own.
 */
class ButtonLightsStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("lights", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())

    var mode: ButtonLights.Mode
        get() = ButtonLights.Mode.load(prefs.getString(KEY, null))
        set(value) {
            prefs.edit().putString(KEY, value.name).apply()
            send(value)
        }

    fun start() {
        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = applySoon()
        }, IntentFilter().apply {
            addAction(QUICKBOOT_POWER_ON)
            addAction(VENDOR_ACC_ON)
        })
        applySoon()
    }

    private fun applySoon() {
        main.removeCallbacksAndMessages(null)
        // Following the headlights is the unit's own behaviour; only "always on" needs sending.
        main.postDelayed({ if (mode == ButtonLights.Mode.ALWAYS_ON) send(mode) }, APPLY_DELAY_MS)
    }

    private fun send(mode: ButtonLights.Mode) {
        context.sendBroadcast(Intent(VENDOR_MCU_SET).putExtra(VENDOR_MCU_EXTRA, ButtonLights.frame(mode)))
    }

    private companion object {
        const val KEY = "mode"
        const val APPLY_DELAY_MS = 5_000L
        const val QUICKBOOT_POWER_ON = "autochips.intent.action.QB_POWERON"
        const val VENDOR_ACC_ON = "xy.android.acc.on"
        const val VENDOR_MCU_SET = "action.mcukey.set"
        const val VENDOR_MCU_EXTRA = "mcu_key"
    }
}
