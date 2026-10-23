package pl.perier.sirion.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.storage.StorageManager
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import pl.perier.sirion.R
import pl.perier.sirion.SirionApp
import pl.perier.sirion.data.BluetoothMusic
import pl.perier.sirion.data.BluetoothPhones
import pl.perier.sirion.data.CarPlayEngine
import pl.perier.sirion.data.PhoneBook
import pl.perier.sirion.logic.CarPlayStatus
import pl.perier.sirion.logic.PhoneText
import pl.perier.sirion.logic.Tuner
import pl.perier.sirion.ui.settings.SettingsActivity
import java.time.LocalDateTime

/** Apps: every place you can go, each tile with its live state (Paper: Apps and media · Apps). */
class AppsActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private val app get() = application as SirionApp
    private lateinit var kit: Kit
    private lateinit var bar: BottomBar
    private lateinit var music: BluetoothMusic

    private val ticker = object : Runnable {
        override fun run() {
            render()
            main.postDelayed(this, 2000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tiles)
        kit = Kit(this)
        bar = BottomBar(this, BottomBar.Nav.APPS).also { it.bind() }
        music = BluetoothMusic(this) { main.post(::render) }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        bar.resume()
        music.start()
        main.post(ticker)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onPause() {
        super.onPause()
        bar.pause()
        music.stop()
        main.removeCallbacks(ticker)
    }

    private fun render() {
        val tiles = listOf(carPlay(), music(), radio(), phone(), usb(), camera(), settings(), unitApps())
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tiles.chunked(4).forEachIndexed { r, row ->
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.forEachIndexed { c, tile ->
                line.addView(tile, LinearLayout.LayoutParams(0, -1, 1f).apply { if (c > 0) marginStart = kit.dp(6) })
            }
            grid.addView(line, LinearLayout.LayoutParams(-1, 0, 1f).apply { if (r > 0) topMargin = kit.dp(6) })
        }
        findViewById<FrameLayout>(R.id.tiles_content).apply {
            removeAllViews()
            addView(grid)
        }
    }

    private fun carPlay(): View {
        val status = CarPlayEngine.status()
        val line = when (status) {
            is CarPlayStatus.Connected -> status.phone ?: "iPhone"
            is CarPlayStatus.NotStarted -> status.phone
            CarPlayStatus.Searching -> "No iPhone nearby"
        }
        val context = if (status is CarPlayStatus.Searching) "Searching" else status.context
        val mark = ImageView(this).apply { setImageResource(R.drawable.brand_carplay) }
        return kit.tile(mark, context, "CarPlay", line, live = status.live,
            dot = if (status.live) R.color.connected else R.color.text_muted) { CarPlayEngine.open(this) }
    }

    private fun music(): View {
        val track = music.track
        val context = when {
            track == null -> "Nothing playing"
            music.playing -> "Playing"
            else -> "Paused"
        }
        val mark = kit.mark(R.drawable.brand_bluetooth, R.color.mark_bluetooth, glyphTint = R.color.text_primary, glyphSize = 18)
        return kit.tile(mark, context, "Music", track?.title ?: "Start music on your phone", live = track != null) {
            startActivity(Intent(this, MusicActivity::class.java))
        }
    }

    private fun radio(): View {
        val radio = app.radio
        val mark = kit.mark(R.drawable.g_radio, R.color.mark_radio, glyphTint = R.color.text_on_light)
        return kit.tile(mark, "FM ${Tuner.label(radio.kilohertz)}", "Radio", radio.name ?: "Change station in one tap", live = true) {
            startActivity(Intent(this, RadioActivity::class.java))
        }
    }

    private fun phone(): View {
        val connected = BluetoothPhones.connectedName() != null
        val now = LocalDateTime.now()
        val missed = if (connected) {
            PhoneText.missedToday(PhoneBook(this).recents().map { PhoneText.Call(it.type, PhoneText.parseCallTime(it.time), it.name) }, now)
        } else null
        val line = missed ?: if (connected) "Favourites, recents, contacts" else "Connect in Settings"
        val mark = kit.mark(R.drawable.g_phone, R.color.mark_phone, glyphTint = R.color.text_on_light)
        return kit.tile(mark, if (connected) "Connected" else "Not connected", "Phone", line, live = connected,
            dot = if (connected) R.color.connected else null) {
            startActivity(Intent(this, PhoneActivity::class.java))
        }
    }

    /** No USB player of our own yet, so a stick opens in the file manager. */
    private fun usb(): View {
        val stick = getSystemService(StorageManager::class.java).storageVolumes
            .any { it.isRemovable && it.state == Environment.MEDIA_MOUNTED }
        val files = launchIntent(FILE_MANAGER)
        val available = stick && files != null
        return kit.tile(kit.mark(R.drawable.g_usb), if (stick) "Stick in" else "No stick", "USB music",
            if (stick) "Opens in the file manager" else "Plug one in to play it", available = available) {
            files?.let(::startActivity)
        }
    }

    /** The vendor keeps the reverse camera (it starts before Android); this only opens its view if it has one. */
    private fun camera(): View {
        val view = launchIntent(BACK_CAMERA)
        return kit.tile(kit.mark(R.drawable.g_camera), "Rear", "Camera",
            if (view != null) "See what's behind you" else "Shows in reverse", available = view != null) {
            view?.let(::startActivity)
        }
    }

    private fun settings() = kit.tile(kit.mark(R.drawable.g_sliders), "", "Settings", "Bluetooth, sound, display") {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun unitApps() = kit.tile(kit.mark(R.drawable.g_apps), "", "Unit apps", "The original menus") {
        startActivity(Intent(this, UnitAppsActivity::class.java))
    }

    private fun launchIntent(pkg: String) =
        packageManager.getLaunchIntentForPackage(pkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private companion object {
        const val FILE_MANAGER = "com.mediatek.filemanager"
        const val BACK_CAMERA = "com.autochips.android.backcar"
    }
}
