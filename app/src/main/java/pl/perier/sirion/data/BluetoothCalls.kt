package pl.perier.sirion.data

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Parcelable
import android.util.Log
import pl.perier.sirion.logic.CallScreen

/** The call the phone reports, read from its hidden BluetoothHeadsetClientCall. */
data class Call(val screen: CallScreen, val number: String, val raw: Parcelable)

/**
 * Calls through Android's own hands-free profile (HFP client). The classes
 * are hidden in the SDK, so they're reached by reflection; they only need the
 * normal BLUETOOTH permission on Android 8.1.
 */
class BluetoothCalls(private val context: Context) {
    private var proxy: BluetoothProfile? = null
    private val listeners = mutableListOf<(Call?) -> Unit>()

    var current: Call? = null
        get() = Demo.call ?: field
        private set

    private val events = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val raw = intent.getParcelableExtra<Parcelable>(EXTRA_CALL) ?: return
            val call = runCatching {
                val state = raw.javaClass.getMethod("getState").invoke(raw) as Int
                val number = raw.javaClass.getMethod("getNumber").invoke(raw) as String? ?: ""
                Call(CallScreen.from(state), number, raw)
            }.getOrNull() ?: return
            current = call.takeIf { it.screen != CallScreen.Ended }
            listeners.toList().forEach { it(current) }
        }
    }

    fun start() {
        BluetoothAdapter.getDefaultAdapter()?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, p: BluetoothProfile) { proxy = p }
            override fun onServiceDisconnected(profile: Int) { proxy = null }
        }, HEADSET_CLIENT)
        context.registerReceiver(events, IntentFilter(ACTION_CALL_CHANGED))
    }

    fun listen(listener: (Call?) -> Unit) { listeners += listener }

    fun stopListening(listener: (Call?) -> Unit) { listeners -= listener }

    private fun device(): BluetoothDevice? = proxy?.connectedDevices?.firstOrNull()

    fun dial(number: String): Boolean = invoke("dial", arrayOf(BluetoothDevice::class.java, String::class.java), number) != null

    fun answer() = invoke("acceptCall", arrayOf(BluetoothDevice::class.java, Int::class.javaPrimitiveType!!), 0)

    fun decline() = invoke("rejectCall", arrayOf(BluetoothDevice::class.java))

    /** A keypad tone during a call (menus, PINs). */
    fun sendTone(key: Char) = invoke("sendDTMF", arrayOf(BluetoothDevice::class.java, Byte::class.javaPrimitiveType!!), key.code.toByte())

    fun end() {
        val raw = current?.raw ?: return
        invoke("terminateCall", arrayOf(BluetoothDevice::class.java, raw.javaClass), raw)
    }

    /** Moves the call's audio back to the phone ("Use phone"). */
    fun moveToPhone() = invoke("disconnectAudio", arrayOf(BluetoothDevice::class.java))

    private fun invoke(name: String, types: Array<Class<*>>, vararg args: Any): Any? {
        val p = proxy ?: return null
        val device = device() ?: return null
        return runCatching { p.javaClass.getMethod(name, *types).invoke(p, device, *args) }
            .onFailure { Log.w(TAG, "$name failed", it) }
            .getOrNull()
    }

    private companion object {
        const val TAG = "SirionCalls"
        /** BluetoothProfile.HEADSET_CLIENT, hidden in the public SDK. */
        const val HEADSET_CLIENT = 16
        const val ACTION_CALL_CHANGED = "android.bluetooth.headsetclient.profile.action.AG_CALL_CHANGED"
        const val EXTRA_CALL = "android.bluetooth.headsetclient.extra.CALL"
    }
}
