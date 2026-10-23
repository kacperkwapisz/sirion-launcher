package pl.perier.sirion.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import pl.perier.sirion.logic.HelperCommand
import pl.perier.sirion.logic.Sound

/**
 * Tone and speaker settings, saved on our side and sent straight to the audio
 * chip through the root helper. Sent again after every start and ignition on,
 * a few seconds late so they land after anything else that sets the chip.
 */
class SoundStore(private val context: Context, private val helper: RootHelper) {
    private val prefs = context.getSharedPreferences("sound", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    private var sent: List<Sound.Effect> = emptyList()

    var current: Sound
        get() = Sound.load(prefs.getString(KEY, null))
        set(value) {
            prefs.edit().putString(KEY, value.save()).apply()
            send(value.effects.filter { it !in sent })
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
        main.postDelayed({ sent = emptyList(); send(current.effects) }, APPLY_DELAY_MS)
    }

    private fun send(effects: List<Sound.Effect>) {
        effects.forEach { helper.send(HelperCommand.Effect(it.id, it.value)) }
        sent = (sent.filter { old -> effects.none { it.id == old.id } } + effects)
    }

    private companion object {
        const val KEY = "sound"
        const val APPLY_DELAY_MS = 5_000L
        const val QUICKBOOT_POWER_ON = "autochips.intent.action.QB_POWERON"
        const val VENDOR_ACC_ON = "xy.android.acc.on"
    }
}
