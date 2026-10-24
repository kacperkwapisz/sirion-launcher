package pl.perier.sirion.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import pl.perier.sirion.R
import pl.perier.sirion.ui.settings.SettingsActivity
import pl.perier.sirion.SirionApp
import pl.perier.sirion.data.BluetoothMusic
import pl.perier.sirion.data.CarPlayMusic
import pl.perier.sirion.data.MusicSource
import pl.perier.sirion.data.BluetoothPhones
import pl.perier.sirion.data.CarPlayEngine
import pl.perier.sirion.data.RootHelper
import pl.perier.sirion.data.Updater
import pl.perier.sirion.logic.CarPlayStatus
import pl.perier.sirion.logic.HelperCommand
import pl.perier.sirion.logic.MusicCard
import pl.perier.sirion.logic.RadioPreset
import pl.perier.sirion.logic.Tuner

/** Home v4 · Media first + bottom bar. */
class HomeActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var bluetoothMusic: BluetoothMusic
    private lateinit var carPlayMusic: CarPlayMusic
    /** CarPlay's song while DiPlay runs a session, Bluetooth otherwise. */
    private val music: MusicSource get() = if (carPlayMusic.active) carPlayMusic else bluetoothMusic
    private lateinit var helper: RootHelper
    private lateinit var updater: Updater

    private var carPlay: CarPlayStatus = CarPlayStatus.Searching

    private val ticker = object : Runnable {
        override fun run() {
            refreshCarPlay()
            refreshMusic()
            main.postDelayed(this, 1000)
        }
    }

    private lateinit var bar: BottomBar

    private val connectionEvents = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == CONNECTIVITY_CHANGE) updater.checkSoon() else refreshCarPlay()
        }
    }

    /** After a minute of running, tell the helper this version works, so an update isn't rolled back. */
    private val reportHealthy = Runnable { helper.send(HelperCommand.Healthy(updater.installedVersion)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        // The D was only for the cold start; behind the cards it's plain black.
        window.setBackgroundDrawableResource(R.color.background)
        // android:clipToOutline in XML needs Android 12; set here so the art keeps the rounded corners.
        listOf(R.id.music_card, R.id.music_artwork).forEach { findViewById<View>(it).clipToOutline = true }
        bluetoothMusic = BluetoothMusic(this) { main.post(::refreshMusic) }
        // A DiPlay session starting or ending changes the CarPlay card too.
        carPlayMusic = CarPlayMusic(this) { main.post { refreshCarPlay(); refreshMusic() } }
        val app = application as SirionApp
        helper = app.helper
        updater = app.updater
        main.postDelayed(reportHealthy, HEALTHY_AFTER_MS)

        bar = BottomBar(this, BottomBar.Nav.HOME).also { it.bind() }
        bindCards()
        buildPresets()
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        bar.resume()
        registerReceiver(connectionEvents, IntentFilter().apply {
            addAction("com.zjinnova.zlink")
            addAction(CONNECTIVITY_CHANGE)
            addAction("android.bluetooth.device.action.ACL_CONNECTED")
            addAction("android.bluetooth.device.action.ACL_DISCONNECTED")
            addAction("android.bluetooth.adapter.action.STATE_CHANGED")
        })
        bluetoothMusic.start()
        carPlayMusic.start()
        updater.checkSoon()
        main.post(ticker)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onPause() {
        super.onPause()
        bar.pause()
        unregisterReceiver(connectionEvents)
        main.removeCallbacks(ticker)
        bluetoothMusic.stop()
        carPlayMusic.stop()
    }

    override fun onDestroy() {
        main.removeCallbacks(reportHealthy)
        radio.stopListening(onRadioChange)
        super.onDestroy()
    }

    /** This is the home screen; Back has nowhere to go. */
    @Deprecated("Activity API")
    override fun onBackPressed() = Unit

    // Cards

    private fun bindCards() {
        findViewById<View>(R.id.carplay_card).setOnClickListener { CarPlayEngine.open(this) }
        findViewById<View>(R.id.music_card).setOnClickListener {
            if (carPlay is CarPlayStatus.Connected) CarPlayEngine.open(this) else startActivity(Intent(this, MusicActivity::class.java))
        }
        findViewById<View>(R.id.music_prev).setOnClickListener { music.previous() }
        findViewById<View>(R.id.music_next).setOnClickListener { music.next() }
        findViewById<View>(R.id.music_play_pause).setOnClickListener { music.playPause() }
        findViewById<View>(R.id.phone_favourites).setOnClickListener { openPhone(PhoneActivity.TAB_FAVOURITES) }
        findViewById<View>(R.id.phone_recents).setOnClickListener { openPhone(PhoneActivity.TAB_RECENTS) }
        findViewById<View>(R.id.phone_contacts).setOnClickListener { openPhone(PhoneActivity.TAB_CONTACTS) }
        findViewById<View>(R.id.phone_connect).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_CATEGORY, SettingsActivity.Category.BLUETOOTH.name))
        }
    }

    private fun openPhone(tab: String) =
        startActivity(Intent(this, PhoneActivity::class.java).putExtra(PhoneActivity.EXTRA_TAB, tab))

    private fun refreshCarPlay() {
        carPlay = CarPlayEngine.status(diPlaySession = carPlayMusic.active)
        findViewById<ImageView>(R.id.carplay_mark).alpha = if (carPlay.live) 1f else DIMMED
        findViewById<View>(R.id.carplay_dot).backgroundTintList =
            ColorStateList.valueOf(getColor(if (carPlay.live) R.color.connected else R.color.text_secondary))
        findViewById<TextView>(R.id.carplay_context).text = carPlay.context
        findViewById<TextView>(R.id.carplay_line).text = carPlay.line

        val phone = BluetoothPhones.connectedName()
        findViewById<TextView>(R.id.phone_context).text = phone ?: getString(R.string.not_connected)
        findViewById<View>(R.id.phone_mark).alpha = if (phone != null) 1f else DIMMED
        findViewById<View>(R.id.phone_actions).visibility = if (phone != null) View.VISIBLE else View.GONE
        findViewById<View>(R.id.phone_connect).visibility = if (phone != null) View.GONE else View.VISIBLE
        findViewById<View>(R.id.status_bluetooth).visibility = if (BluetoothPhones.isOn()) View.VISIBLE else View.GONE
    }

    private fun refreshMusic() {
        val source = music
        val state = MusicCard.from(carPlay is CarPlayStatus.Connected, source.track, source.playing, source.positionMs, fromCarPlay = source === carPlayMusic)
        val title = findViewById<TextView>(R.id.music_title)
        val line = findViewById<TextView>(R.id.music_line)
        val action = findViewById<TextView>(R.id.music_action)
        val playingViews = listOf(R.id.music_progress_group, R.id.music_transport).map { findViewById<View>(it) }
        val markGlyph = findViewById<ImageView>(R.id.music_mark_glyph)
        // The square only for real album art (CarPlay through DiPlay); Bluetooth never sends any,
        // and then the title and artist get the whole width.
        val artwork = (state as? MusicCard.Playing)?.let { source.artwork }
        findViewById<ImageView>(R.id.music_artwork).apply {
            setImageBitmap(artwork)
            visibility = if (artwork != null) View.VISIBLE else View.GONE
        }
        // The same art dimmed behind the whole card, with the scrim that keeps the text readable.
        findViewById<ImageView>(R.id.music_background).apply {
            setImageBitmap(artwork)
            visibility = if (artwork != null) View.VISIBLE else View.GONE
        }
        findViewById<View>(R.id.music_scrim).visibility = if (artwork != null) View.VISIBLE else View.GONE
        listOf(title, line).forEach { view ->
            view.layoutParams = (view.layoutParams as ViewGroup.MarginLayoutParams).apply { marginEnd = if (artwork != null) ARTWORK_LANE else 0 }
        }
        val mark = findViewById<View>(R.id.music_mark)

        when (state) {
            is MusicCard.Playing -> {
                title.text = state.track.title
                line.text = state.track.artist
                playingViews.forEach { it.visibility = View.VISIBLE }
                action.visibility = View.GONE
                findViewById<ProgressBar>(R.id.music_progress).progress = (state.progress * 1000).toInt()
                findViewById<TextView>(R.id.music_elapsed).text = state.elapsed
                findViewById<TextView>(R.id.music_remaining).text = state.remaining
                findViewById<ImageButton>(R.id.music_play_pause)
                    .setImageResource(if (state.playing) R.drawable.g_pause else R.drawable.g_play)
                showMark(mark, markGlyph, carPlayMark = state.fromCarPlay, live = true)
            }
            MusicCard.NothingPlaying -> {
                val first = radio.presets.firstOrNull { it != null } ?: RadioPreset.defaults.first()
                title.setText(R.string.nothing_playing)
                line.setText(R.string.nothing_playing_line)
                playingViews.forEach { it.visibility = View.GONE }
                action.visibility = View.VISIBLE
                action.text = getString(R.string.play_preset, first.label)
                action.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.g_play, 0, 0, 0)
                action.setOnClickListener { radio.tune(first) }
                showMark(mark, markGlyph, carPlayMark = false, live = false)
            }
            MusicCard.InCarPlay -> {
                // Same card as playing music, so it reads as "music is on"; the whole card
                // opens CarPlay, so it needs no button of its own next to the CarPlay card.
                title.setText(R.string.playing_in_carplay)
                line.text = (carPlay as? CarPlayStatus.Connected)?.phone ?: "iPhone"
                playingViews.forEach { it.visibility = View.GONE }
                action.visibility = View.GONE
                showMark(mark, markGlyph, carPlayMark = true, live = true)
            }
        }
    }

    private fun showMark(mark: View, glyph: ImageView, carPlayMark: Boolean, live: Boolean) {
        if (carPlayMark) {
            mark.backgroundTintList = ColorStateList.valueOf(0)
            glyph.setImageResource(R.drawable.brand_carplay)
            glyph.layoutParams = glyph.layoutParams.apply { width = MARK; height = MARK }
        } else {
            mark.backgroundTintList = ColorStateList.valueOf(getColor(R.color.mark_bluetooth))
            glyph.setImageResource(R.drawable.brand_bluetooth)
            glyph.layoutParams = glyph.layoutParams.apply { width = MARK_GLYPH; height = MARK_GLYPH }
        }
        mark.alpha = if (live) 1f else DIMMED
    }

    // Radio

    private val radio get() = (application as SirionApp).radio
    private val onRadioChange: () -> Unit = { refreshRadio() }

    private fun buildPresets() {
        val row = findViewById<LinearLayout>(R.id.radio_presets)
        repeat(HOME_PRESETS) { slot ->
            val button = LayoutInflater.from(this).inflate(R.layout.radio_preset, row, false)
            if (slot > 0) (button.layoutParams as LinearLayout.LayoutParams).marginStart = PRESET_GAP
            button.setOnClickListener { radio.presets.getOrNull(slot)?.let(radio::tune) }
            row.addView(button)
        }
        findViewById<View>(R.id.radio_card).setOnClickListener { startActivity(Intent(this, RadioActivity::class.java)) }
        radio.listen(onRadioChange)
        refreshRadio()
    }

    private fun refreshRadio() {
        findViewById<TextView>(R.id.radio_context).text =
            if (radio.playing) "Playing" else getString(R.string.last_played, Tuner.label(radio.kilohertz))
        val row = findViewById<LinearLayout>(R.id.radio_presets)
        val presets = radio.presets
        for (slot in 0 until row.childCount) {
            val preset = presets.getOrNull(slot)
            (row.getChildAt(slot) as LinearLayout).showPreset(preset, playing = radio.playing && preset != null && Tuner.kilohertz(preset) == radio.kilohertz)
        }
    }

    private companion object {
        /** Room the square takes next to the title (128px square + 20px gap). */
        const val ARTWORK_LANE = 148
        const val DIMMED = 0.45f
        const val HEALTHY_AFTER_MS = 60_000L
        const val CONNECTIVITY_CHANGE = "android.net.conn.CONNECTIVITY_CHANGE"
        const val MARK = 28
        const val MARK_GLYPH = 18
        const val PRESET_GAP = 6
        const val HOME_PRESETS = 4
    }
}
