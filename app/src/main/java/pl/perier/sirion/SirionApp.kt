package pl.perier.sirion

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import pl.perier.sirion.data.BluetoothCalls
import pl.perier.sirion.data.BluetoothKeeper
import pl.perier.sirion.data.BrightnessStore
import pl.perier.sirion.data.ButtonLightsStore
import pl.perier.sirion.data.Demo
import pl.perier.sirion.data.RadioStore
import pl.perier.sirion.data.RootHelper
import pl.perier.sirion.data.SoundStore
import pl.perier.sirion.data.Updater
import pl.perier.sirion.logic.CallScreen
import pl.perier.sirion.logic.CarPlayFocus
import pl.perier.sirion.ui.CallActivity
import pl.perier.sirion.ui.HomeActivity

/**
 * Lives as long as the launcher process. As the home screen, Android keeps
 * it running and restarts it after QuickBoot or a crash.
 */
class SirionApp : Application() {
    /** Keeps CarPlay off screen unless the driver opened it (or the unit just woke). */
    val carPlayFocus = CarPlayFocus()
    private val main = Handler(Looper.getMainLooper())

    lateinit var helper: RootHelper
        private set
    lateinit var updater: Updater
        private set
    lateinit var bluetooth: BluetoothKeeper
        private set
    lateinit var calls: BluetoothCalls
        private set
    lateinit var brightness: BrightnessStore
        private set
    lateinit var radio: RadioStore
        private set
    lateinit var sound: SoundStore
        private set
    lateinit var buttonLights: ButtonLightsStore
        private set

    override fun onCreate() {
        super.onCreate()
        Demo.init(this)
        helper = RootHelper(this, onCarPlayFront = ::onCarPlayFront) { json -> radio.onTunerEvent(json) }
        // The unit just started: CarPlay may come up on its own this once.
        carPlayFocus.unitWoke()
        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = carPlayFocus.unitWoke()
        }, IntentFilter(QUICKBOOT_POWER_ON))
        radio = RadioStore(this, helper)
        updater = Updater(this, helper)
        // Sending the saved brightness also starts the root helper and brightnessd at boot.
        brightness = BrightnessStore(this, helper).also { it.apply() }
        sound = SoundStore(this, helper).also { it.start() }
        buttonLights = ButtonLightsStore(this).also { it.start() }
        bluetooth = BluetoothKeeper(this, helper).also { it.start() }
        calls = BluetoothCalls(this).also { it.start() }
        // A ringing or outgoing call brings up our call screen, wherever we are.
        var inCall = false
        calls.listen { call ->
            // A call just ended: fetch the phone's call list so Recents shows it.
            if (inCall && call == null) bluetooth.syncCallsSoon()
            inCall = call != null
            if (call?.screen == CallScreen.Incoming || call?.screen == CallScreen.Calling) {
                startActivity(Intent(this, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            }
        }
    }

    private fun onCarPlayFront(front: Boolean) = main.post {
        if (!front) {
            carPlayFocus.wentToBack()
        } else {
            val mode = getSystemService(AudioManager::class.java).mode
            val callOrVoice = mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION || mode == AudioManager.MODE_RINGTONE
            // Logged so a car test can confirm ZLink switches the mode for calls and Siri.
            Log.i("SirionCarPlay", "CarPlay came to the front, audio mode $mode")
            if (carPlayFocus.cameToFront(callOrVoice = callOrVoice) != CarPlayFocus.Action.SEND_HOME) return@post
            // The iPhone pulled CarPlay forward for something else (music, a notification): back home.
            startActivity(Intent(this, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private companion object {
        const val QUICKBOOT_POWER_ON = "autochips.intent.action.QB_POWERON"
    }
}
