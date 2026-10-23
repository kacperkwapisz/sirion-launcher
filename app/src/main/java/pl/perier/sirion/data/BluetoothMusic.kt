package pl.perier.sirion.data

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.SystemClock
import pl.perier.sirion.logic.Track

/**
 * Bluetooth music straight from Android's A2DP sink, which publishes a
 * normal media session. Android 8.1 sends no cover art over Bluetooth, so
 * there is never artwork from here.
 */
class BluetoothMusic(private val context: Context, private val onChange: () -> Unit) {
    /** Shuffle or repeat as the phone reports it; unavailable when the phone doesn't offer it over Bluetooth. */
    enum class Mode { UNAVAILABLE, OFF, ON }

    private var controller: MediaController? = null
    private var avrcp: BluetoothProfile? = null

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = onChange()
        override fun onPlaybackStateChanged(state: PlaybackState?) = onChange()
        override fun onSessionDestroyed() {
            controller = null
            onChange()
        }
    }

    private val browser: MediaBrowser = MediaBrowser(
        context,
        ComponentName("com.android.bluetooth", "com.android.bluetooth.a2dpsink.mbs.A2dpMediaBrowserService"),
        object : MediaBrowser.ConnectionCallback() {
            override fun onConnected() {
                controller = MediaController(context, browser.sessionToken).also { it.registerCallback(callback) }
                onChange()
            }

            override fun onConnectionSuspended() {
                controller = null
                onChange()
            }
        },
        null,
    )

    fun start() {
        if (!browser.isConnected) runCatching { browser.connect() }
        if (avrcp == null) BluetoothAdapter.getDefaultAdapter()?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) { avrcp = proxy; onChange() }
            override fun onServiceDisconnected(profile: Int) { avrcp = null }
        }, AVRCP_CONTROLLER)
    }

    fun stop() {
        controller?.unregisterCallback(callback)
        controller = null
        if (browser.isConnected) browser.disconnect()
        avrcp?.let { BluetoothAdapter.getDefaultAdapter()?.closeProfileProxy(AVRCP_CONTROLLER, it) }
        avrcp = null
    }

    val track: Track?
        get() {
            if (Demo.on) return Demo.track
            val metadata = controller?.metadata ?: return null
            return Track(
                title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
                artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
                durationMs = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION),
            )
        }

    val album: String get() = if (Demo.on) Demo.ALBUM else controller?.metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty()

    fun seekTo(ms: Long) = controller?.transportControls?.seekTo(ms)

    fun play() = controller?.transportControls?.play()

    val playing: Boolean get() = Demo.on || controller?.playbackState?.state == PlaybackState.STATE_PLAYING

    /** Position now, extrapolated from the last report while playing. */
    val positionMs: Long
        get() {
            if (Demo.on) return Demo.POSITION_MS
            val state = controller?.playbackState ?: return 0
            if (state.position < 0) return 0
            if (state.state != PlaybackState.STATE_PLAYING) return state.position
            val since = SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
            return state.position + (since * state.playbackSpeed).toLong()
        }

    fun playPause() {
        val controls = controller?.transportControls ?: return
        if (playing) controls.pause() else controls.play()
    }

    fun next() = controller?.transportControls?.skipToNext()

    fun previous() = controller?.transportControls?.skipToPrevious()

    // Shuffle and repeat go through the hidden AVRCP controller's player
    // application settings. Many phones (iPhones included) may not offer them.

    val shuffle: Mode get() = mode("SETTING_SHUFFLE")
    val repeat: Mode get() = mode("SETTING_REPEAT")

    fun toggleShuffle() = toggle("SETTING_SHUFFLE", shuffle)
    fun toggleRepeat() = toggle("SETTING_REPEAT", repeat)

    private val settingsClass by lazy { runCatching { Class.forName("android.bluetooth.BluetoothAvrcpPlayerSettings") }.getOrNull() }
    private fun constant(name: String) = settingsClass!!.getField(name).getInt(null)

    private fun mode(setting: String): Mode {
        if (Demo.on) return if (setting == "SETTING_REPEAT") Mode.ON else Mode.OFF
        return runCatching {
            val proxy = avrcp ?: return Mode.UNAVAILABLE
            val device = proxy.connectedDevices.firstOrNull() ?: return Mode.UNAVAILABLE
            val settings = proxy.javaClass.getMethod("getPlayerSettings", BluetoothDevice::class.java).invoke(proxy, device)
                ?: return Mode.UNAVAILABLE
            val id = constant(setting)
            val supported = (settingsClass!!.getMethod("getSettings").invoke(settings) as Int) and id != 0
            if (!supported) return Mode.UNAVAILABLE
            val value = settingsClass!!.getMethod("getSettingValue", Int::class.javaPrimitiveType).invoke(settings, id) as Int
            if (value == constant("STATE_OFF")) Mode.OFF else Mode.ON
        }.getOrDefault(Mode.UNAVAILABLE)
    }

    private fun toggle(setting: String, current: Mode) {
        if (current == Mode.UNAVAILABLE) return
        runCatching {
            val proxy = avrcp ?: return
            val id = constant(setting)
            val value = if (current == Mode.ON) constant("STATE_OFF") else constant("STATE_ALL_TRACK")
            val settings = settingsClass!!.getConstructor(Int::class.javaPrimitiveType).newInstance(id)
            settingsClass!!.getMethod("addSettingValue", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).invoke(settings, id, value)
            proxy.javaClass.getMethod("setPlayerApplicationSetting", settingsClass).invoke(proxy, settings)
        }
        onChange()
    }

    private companion object {
        /** Hidden BluetoothProfile.AVRCP_CONTROLLER on Android 8.1. */
        const val AVRCP_CONTROLLER = 12
    }
}
