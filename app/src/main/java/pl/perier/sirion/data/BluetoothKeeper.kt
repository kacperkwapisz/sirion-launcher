package pl.perier.sirion.data

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.util.Log
import pl.perier.sirion.logic.CarPlayStatus
import pl.perier.sirion.logic.HelperCommand
import pl.perier.sirion.logic.PhonePriority

/**
 * Keeps Bluetooth on and gets a phone connected, whoever is driving.
 *
 * - Turns Bluetooth off when CarPlay starts (the vendor app's one useful
 *   rule, so calls go through CarPlay) and back on, clearing the vendor's
 *   stuck-off flag, whenever it is off and CarPlay isn't running.
 * - While no phone is connected, tries each paired phone in "Connect first"
 *   order, one every [TRY_EVERY_MS], so a second driver's phone gets a turn.
 * - The driver sets that order in Settings (Connect first); a new phone that
 *   connects is added at the end.
 *
 * Uses Android's own A2DP sink and hands-free profiles; no vendor app.
 */
class BluetoothKeeper(private val context: Context, private val helper: RootHelper) {
    private val main = Handler(Looper.getMainLooper())
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val prefs = context.getSharedPreferences("phones", Context.MODE_PRIVATE)
    private val proxies = mutableMapOf<Int, BluetoothProfile>()
    private var lastTried: String? = null

    /** Contact photos and favourites, which the unit's own sync leaves out. */
    val extras = PhoneExtras(context)

    /** The driver turned Bluetooth off in Settings: leave it off until they turn it on or the next ignition. */
    var userOff = false
    /** The driver disconnected a phone: stop reconnecting until they tap Connect or the next ignition. */
    private var paused = false
    /** Bluetooth was turned off for this CarPlay session. */
    private var offForCarPlay = false

    /** "Switch phones without re-pairing": Connect on another phone takes over, and every paired phone gets a turn. */
    var switchPhones: Boolean
        get() = prefs.getBoolean(KEY_SWITCH, true)
        set(value) = prefs.edit().putBoolean(KEY_SWITCH, value).apply()

    var order: List<String>
        get() = prefs.getString(KEY_ORDER, "").orEmpty().split(',').filter { it.isNotBlank() }
        set(value) = prefs.edit().putString(KEY_ORDER, value.joinToString(",")).apply()

    private val tick = object : Runnable {
        override fun run() {
            runCatching { step() }.onFailure { Log.w(TAG, "Bluetooth step failed", it) }
            main.postDelayed(this, TRY_EVERY_MS)
        }
    }

    private val events = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothDevice.ACTION_ACL_CONNECTED) {
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
                order = PhonePriority.remember(order, device.address)
                prefs.edit().putLong(KEY_SEEN + device.address, System.currentTimeMillis()).apply()
                // Give the phone a moment to finish connecting, then copy its contacts and calls.
                main.postDelayed({ syncPhonebook(device) }, SYNC_DELAY_MS)
                // Again a minute later: the iPhone only shares once its "Sync contacts" is allowed,
                // and people often allow it after the first connection.
                main.postDelayed({ syncPhonebook(device, force = true) }, RESYNC_DELAY_MS)
            } else {
                if (intent.action == QUICKBOOT_POWER_ON) {
                    userOff = false
                    paused = false
                    lastTried = null
                }
                if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) ensureName()
                // Woken up, screen on, or Bluetooth changed: act now, not in 10 s.
                main.removeCallbacks(tick)
                main.post(tick)
            }
        }
    }

    fun start() {
        listOf(A2DP_SINK, HEADSET_CLIENT).forEach { profile ->
            adapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(p: Int, proxy: BluetoothProfile) { proxies[p] = proxy }
                override fun onServiceDisconnected(p: Int) { proxies.remove(p) }
            }, profile)
        }
        context.registerReceiver(events, IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(QUICKBOOT_POWER_ON)
        })
        main.post(tick)
        ensureName()
        // A phone that was connected before the launcher started never sends
        // ACL_CONNECTED, so ask it for contacts now too.
        pbManager
        main.postDelayed({ phones().firstOrNull(::isConnected)?.let { syncPhonebook(it, force = true) } }, SYNC_DELAY_MS)
    }

    /** Phones see the unit by this name; put it back whenever Bluetooth comes on. */
    private fun ensureName() {
        val bluetooth = adapter ?: return
        if (bluetooth.isEnabled && bluetooth.name != UNIT_NAME) bluetooth.setName(UNIT_NAME)
    }

    /** Paired phones in "Connect first" order. */
    fun phones(): List<BluetoothDevice> {
        val bonded = adapter?.bondedDevices.orEmpty().associateBy { it.address }
        val order = order
        return order.mapNotNull { bonded[it] } + bonded.values.filter { it.address !in order }.sortedBy { it.name }
    }

    fun isConnected(device: BluetoothDevice) =
        runCatching { device.javaClass.getMethod("isConnected").invoke(device) as Boolean }.getOrDefault(false)

    fun lastConnected(device: BluetoothDevice): Long? = prefs.getLong(KEY_SEEN + device.address, 0).takeIf { it > 0 }

    fun makeFirst(device: BluetoothDevice) {
        order = PhonePriority.promote(order, device.address)
    }

    fun connect(device: BluetoothDevice) {
        paused = false
        val others = phones().filter { it.address != device.address && isConnected(it) }
        others.forEach(::drop)
        // Give the other phone a moment to let go before this one takes over.
        main.postDelayed({ call("connect", device) }, if (others.isEmpty()) 0 else TAKEOVER_DELAY_MS)
    }

    fun disconnect(device: BluetoothDevice) {
        paused = true
        drop(device)
    }

    fun setOn(on: Boolean) {
        userOff = !on
        if (on) adapter?.enable() else adapter?.disable()
    }

    private fun drop(device: BluetoothDevice) = call("disconnect", device)

    private fun call(method: String, device: BluetoothDevice) = proxies.values.forEach { proxy ->
        runCatching { proxy.javaClass.getMethod(method, BluetoothDevice::class.java).invoke(proxy, device) }
    }

    private fun step() {
        val bluetooth = adapter ?: return
        val carPlay = CarPlayEngine.status() is CarPlayStatus.Connected
        if (PhonePriority.shouldTurnBluetoothOffForCarPlay(bluetooth.isEnabled, carPlay, offForCarPlay)) {
            offForCarPlay = true
            bluetooth.disable()
            return
        }
        if (!carPlay) offForCarPlay = false
        if (!userOff && PhonePriority.shouldTurnBluetoothOn(bluetooth.isEnabled, carPlay)) {
            helper.send(HelperCommand.UnstickBluetooth)
            bluetooth.enable()
            return
        }
        if (paused || carPlay || !bluetooth.isEnabled || BluetoothPhones.connectedName() != null) return

        val bonded = bluetooth.bondedDevices.orEmpty().associateBy { it.address }
        val address = PhonePriority.next(order, bonded.keys, lastTried, onlyFirst = !switchPhones) ?: return
        lastTried = address
        call("connect", bonded.getValue(address))
    }

    /**
     * Asks the phone for its contacts and call history over PBAP. The download
     * manager is part of the unit's framework (not the vendor app), so we can
     * call it ourselves; the results land in the Bluetooth stack's stores
     * that [PhoneBook] reads.
     */
    private var lastSync = 0L

    /**
     * The unit's phonebook manager (in its framework, not the vendor app). It
     * runs a state machine, so it must be made on the main thread, and it binds
     * the phonebook-client profile, which takes a moment to be ready.
     */
    private val pbManager: Any? by lazy {
        runCatching {
            Class.forName("com.autochips.bluetooth.BluetoothPBManager")
                .getMethod("getInstance", Context::class.java).invoke(null, context)
        }.onFailure { Log.w(TAG, "No phonebook manager", it) }.getOrNull()
    }

    /** Asks the connected phone for contacts and calls again (at most every 2 minutes). Any thread. */
    fun syncContactsSoon() = main.post {
        phones().firstOrNull(::isConnected)?.let { syncPhonebook(it) }
    }

    /** Asks the connected phone for its call list again, a moment after a call ends. Any thread. */
    fun syncCallsSoon() = main.postDelayed({
        val device = phones().firstOrNull(::isConnected) ?: return@postDelayed
        val manager = pbManager ?: return@postDelayed
        runCatching {
            manager.javaClass.getMethod("startDownload", BluetoothDevice::class.java, Int::class.javaPrimitiveType)
                .invoke(manager, device, PBAP_CALL_HISTORY)
        }.onFailure { Log.w(TAG, "Call list sync failed", it) }
        Log.i(TAG, "Call list download asked for ${device.name}")
    }, CALL_SYNC_DELAY_MS)

    /**
     * The phone serves one phonebook download at a time, so photos wait for
     * the unit's own sync: its call list is the last thing it writes. Starts
     * once that list has been quiet for [PHOTOS_QUIET_MS], or after
     * [PHOTOS_DELAY_MS] if nothing changed. Main thread.
     */
    private fun photosWhenSyncIsDone(device: BluetoothDevice, force: Boolean) {
        val resolver = context.contentResolver
        var started = false
        lateinit var observer: ContentObserver
        val start = Runnable {
            if (started) return@Runnable
            started = true
            resolver.unregisterContentObserver(observer)
            syncPhotos(device, force)
        }
        val fallback = Runnable { start.run() }
        observer = object : ContentObserver(main) {
            override fun onChange(selfChange: Boolean) {
                main.removeCallbacks(start)
                main.postDelayed(start, PHOTOS_QUIET_MS)
            }
        }
        resolver.registerContentObserver(PhoneBook.CALLS, true, observer)
        main.postDelayed(fallback, PHOTOS_DELAY_MS)
    }

    /** Photos take a while and rarely change: at most twice a day, or when forced (a fresh start), once the unit's own sync is done. */
    private fun syncPhotos(device: BluetoothDevice, force: Boolean, attempt: Int = 0) {
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong(KEY_PHOTOS_AT, 0) < PHOTOS_EVERY_MS) return
        if (!isConnected(device)) return
        extras.download(device) { ok ->
            if (ok) prefs.edit().putLong(KEY_PHOTOS_AT, System.currentTimeMillis()).apply()
            // The phone may still be busy with the unit's own sync: one more go later.
            else if (attempt == 0) main.postDelayed({ syncPhotos(device, force, attempt + 1) }, PHOTOS_DELAY_MS)
        }
    }

    /** Main thread only. Contacts first, then the call history a little later. */
    private fun syncPhonebook(device: BluetoothDevice, force: Boolean = false, attempt: Int = 0) {
        val now = System.currentTimeMillis()
        if (attempt == 0 && !force && now - lastSync < RESYNC_MIN_GAP_MS) return
        lastSync = now
        val manager = pbManager ?: return
        val started = runCatching {
            val download = manager.javaClass.getMethod("startDownload", BluetoothDevice::class.java, Int::class.javaPrimitiveType)
            val ok = download.invoke(manager, device, PBAP_CONTACTS) as? Boolean ?: false
            if (ok) main.postDelayed({
                runCatching { download.invoke(manager, device, PBAP_CALL_HISTORY) }
                photosWhenSyncIsDone(device, force)
            }, SYNC_DELAY_MS)
            ok
        }.onFailure { Log.w(TAG, "Phonebook sync failed", it) }.getOrDefault(false)
        Log.i(TAG, "Phonebook download ${if (started) "started" else "not started"} for ${device.name} (try ${attempt + 1})")
        // The profile may not be bound yet right after start: try again shortly.
        if (!started && attempt < SYNC_RETRIES) main.postDelayed({ syncPhonebook(device, force, attempt + 1) }, SYNC_RETRY_MS)
    }

    private companion object {
        const val TAG = "SirionBluetooth"
        const val SYNC_DELAY_MS = 8_000L
        const val PHOTOS_DELAY_MS = 60_000L
        const val PHOTOS_QUIET_MS = 5_000L
        const val PHOTOS_EVERY_MS = 12 * 60 * 60_000L
        const val KEY_PHOTOS_AT = "photos_at"
        /** The phone writes the call to its own list a moment after hanging up. */
        const val CALL_SYNC_DELAY_MS = 4_000L
        const val RESYNC_DELAY_MS = 60_000L
        const val RESYNC_MIN_GAP_MS = 120_000L
        const val SYNC_RETRIES = 3
        const val SYNC_RETRY_MS = 5_000L
        /** Download paths the vendor app uses: 6 = phonebook, 168 = all call history. */
        const val PBAP_CONTACTS = 6
        const val PBAP_CALL_HISTORY = 168
        const val KEY_ORDER = "order"
        const val KEY_SWITCH = "switch_phones"
        const val KEY_SEEN = "seen_"
        const val UNIT_NAME = "My Sirion"
        const val TAKEOVER_DELAY_MS = 1_500L
        const val TRY_EVERY_MS = 10_000L
        /** Hidden BluetoothProfile constants on Android 8.1. */
        const val A2DP_SINK = 11
        const val HEADSET_CLIENT = 16
        /** Sent by the unit's QuickBoot manager when the ignition comes back on. */
        const val QUICKBOOT_POWER_ON = "autochips.intent.action.QB_POWERON"
    }
}
