package pl.perier.sirion.ui

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.storage.StorageManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import pl.perier.sirion.R
import pl.perier.sirion.SirionApp
import pl.perier.sirion.data.BluetoothMusic
import pl.perier.sirion.data.CarPlayMusic
import pl.perier.sirion.data.MusicSource
import pl.perier.sirion.data.BluetoothPhones
import pl.perier.sirion.data.CarPlayEngine
import pl.perier.sirion.logic.CarPlayStatus
import pl.perier.sirion.logic.MusicCard
import pl.perier.sirion.logic.RadioPreset
import pl.perier.sirion.logic.Tuner

/** Music · full screen player for Bluetooth music or CarPlay's song (through DiPlay), with the source picker. */
class MusicActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private val radio get() = (application as SirionApp).radio
    private lateinit var bar: BottomBar
    private lateinit var bluetoothMusic: BluetoothMusic
    private lateinit var carPlayMusic: CarPlayMusic
    /** CarPlay's song while DiPlay runs a session, Bluetooth otherwise. */
    private val music: MusicSource get() = if (carPlayMusic.active) carPlayMusic else bluetoothMusic
    private var dragging = false
    /** Whether the playing source takes a seek (CarPlay doesn't). */
    private var canSeek = true
    private var picker: Popover? = null

    private val ticker = object : Runnable {
        override fun run() {
            render()
            main.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_music)
        // android:clipToOutline in XML needs Android 12; set here so the art keeps its rounded corners.
        findViewById<View>(R.id.player_artwork).clipToOutline = true
        bar = BottomBar(this, BottomBar.Nav.APPS).also { it.bind() }
        bluetoothMusic = BluetoothMusic(this) { main.post(::render) }
        carPlayMusic = CarPlayMusic(this) { main.post(::render) }

        findViewById<View>(R.id.player_prev).setOnClickListener { music.previous() }
        findViewById<View>(R.id.player_next).setOnClickListener { music.next() }
        findViewById<View>(R.id.player_play_pause).setOnClickListener { music.playPause() }
        findViewById<View>(R.id.player_shuffle).setOnClickListener { music.toggleShuffle() }
        findViewById<View>(R.id.player_repeat).setOnClickListener { music.toggleRepeat() }
        findViewById<View>(R.id.player_source).setOnClickListener { togglePicker() }
        findViewById<SeekBar>(R.id.player_scrubber).setOnTouchListener { _, _ -> !canSeek }
        findViewById<SeekBar>(R.id.player_scrubber).setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) = Unit
            override fun onStartTrackingTouch(bar: SeekBar) { dragging = true }
            override fun onStopTrackingTouch(bar: SeekBar) {
                dragging = false
                val duration = music.track?.durationMs ?: 0
                if (duration > 0) music.seekTo(duration * bar.progress / 1000)
            }
        })
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        bar.resume()
        bluetoothMusic.start()
        carPlayMusic.start()
        main.post(ticker)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onPause() {
        super.onPause()
        bar.pause()
        picker?.dismiss()
        main.removeCallbacks(ticker)
        bluetoothMusic.stop()
        carPlayMusic.stop()
    }

    private fun render() {
        val source = music
        val carPlay = CarPlayEngine.status(diPlaySession = carPlayMusic.active) is CarPlayStatus.Connected
        val state = MusicCard.from(carPlay, source.track, source.playing, source.positionMs, fromCarPlay = source === carPlayMusic)
        // Real album art over the placeholder background; none from Bluetooth.
        findViewById<ImageView>(R.id.player_artwork).setImageBitmap((state as? MusicCard.Playing)?.let { source.artwork })
        val phone = BluetoothPhones.connectedName()
        val onRadio = state == MusicCard.NothingPlaying && radio.playing
        findViewById<TextView>(R.id.player_source).text = when {
            carPlay -> "CarPlay"
            onRadio -> "Radio"
            else -> phone ?: "Bluetooth"
        }

        val title = findViewById<TextView>(R.id.player_title)
        val artist = findViewById<TextView>(R.id.player_artist)
        val album = findViewById<TextView>(R.id.player_album)
        val playing = listOf(R.id.player_progress_group, R.id.player_transport_gap, R.id.player_transport).map { findViewById<View>(it) }
        val empty = findViewById<View>(R.id.player_empty_actions)
        val secondary = findViewById<TextView>(R.id.player_secondary)
        showMark(carPlay, onRadio, live = state !is MusicCard.NothingPlaying || onRadio)

        when (state) {
            is MusicCard.Playing -> {
                title.text = state.track.title
                artist.text = state.track.artist
                artist.setTextColor(getColor(R.color.text_secondary))
                showAlbum(album, source.album)
                playing.forEach { it.visibility = View.VISIBLE }
                empty.visibility = View.GONE
                findViewById<SeekBar>(R.id.player_scrubber).apply {
                    // CarPlay takes no seek from the car: same look, but the knob doesn't move by hand.
                    canSeek = source.canSeek
                    if (!dragging) progress = (state.progress * 1000).toInt()
                }
                findViewById<TextView>(R.id.player_elapsed).text = state.elapsed
                findViewById<TextView>(R.id.player_remaining).text = state.remaining
                findViewById<ImageButton>(R.id.player_play_pause)
                    .setImageResource(if (state.playing) R.drawable.g_pause else R.drawable.g_play)
                showMode(findViewById(R.id.player_shuffle), source.shuffle)
                showMode(findViewById(R.id.player_repeat), source.repeat)
            }
            MusicCard.NothingPlaying -> {
                playing.forEach { it.visibility = View.GONE }
                empty.visibility = View.VISIBLE
                artist.setTextColor(getColor(R.color.text_on_artwork))
                showAlbum(album, "")
                if (onRadio) {
                    title.text = "Radio is on"
                    artist.text = "FM ${Tuner.label(radio.kilohertz)}" + (radio.name?.let { " · $it" } ?: "")
                    primary("Open radio", glyph = false) { startActivity(Intent(this, RadioActivity::class.java)) }
                    secondary.setOnClickListener { radio.off(); music.play() }
                } else {
                    val first = radio.presets.firstOrNull { it != null } ?: RadioPreset.defaults.first()
                    title.setText(R.string.nothing_playing)
                    artist.text = "Start music on your phone, or play the radio"
                    primary(getString(R.string.play_preset, first.label), glyph = true) {
                        radio.tune(first)
                        startActivity(Intent(this, RadioActivity::class.java))
                    }
                    secondary.setOnClickListener { music.play() }
                }
                secondary.text = "Play from phone"
                // Keeps its half of the row even without a phone, so the pill doesn't stretch.
                secondary.visibility = if (phone != null) View.VISIBLE else View.INVISIBLE
            }
            MusicCard.InCarPlay -> {
                playing.forEach { it.visibility = View.GONE }
                empty.visibility = View.VISIBLE
                title.setText(R.string.playing_in_carplay)
                artist.setText(R.string.playing_in_carplay_line)
                artist.setTextColor(getColor(R.color.text_on_artwork))
                showAlbum(album, "")
                primary(getString(R.string.open_carplay), glyph = false) { CarPlayEngine.open(this) }
                secondary.visibility = View.INVISIBLE
            }
        }
    }

    private fun primary(label: String, glyph: Boolean, onClick: () -> Unit) {
        findViewById<TextView>(R.id.player_primary_label).text = label
        findViewById<View>(R.id.player_primary_glyph).visibility = if (glyph) View.VISIBLE else View.GONE
        findViewById<View>(R.id.player_primary).setOnClickListener { onClick() }
    }

    private fun showAlbum(view: TextView, text: String) {
        view.text = text
        view.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
    }

    /** Shuffle and repeat: accent when on, grey when off, track grey and not tappable when the phone doesn't offer it. */
    private fun showMode(button: ImageButton, mode: MusicSource.Mode) {
        val tint = when (mode) {
            MusicSource.Mode.ON -> R.color.accent
            MusicSource.Mode.OFF -> R.color.text_secondary
            MusicSource.Mode.UNAVAILABLE -> R.color.surface_track
        }
        button.imageTintList = ColorStateList.valueOf(getColor(tint))
        button.isEnabled = mode != MusicSource.Mode.UNAVAILABLE
    }

    private fun showMark(carPlay: Boolean, onRadio: Boolean, live: Boolean) {
        val mark = findViewById<View>(R.id.player_mark)
        val glyph = findViewById<ImageView>(R.id.player_mark_glyph)
        glyph.imageTintList = null
        when {
            onRadio -> {
                mark.backgroundTintList = ColorStateList.valueOf(getColor(R.color.mark_radio))
                glyph.setImageResource(R.drawable.g_radio)
                glyph.imageTintList = ColorStateList.valueOf(getColor(R.color.text_on_light))
                glyph.layoutParams = glyph.layoutParams.apply { width = 16; height = 16 }
            }
            carPlay -> {
                mark.backgroundTintList = ColorStateList.valueOf(0)
                glyph.setImageResource(R.drawable.brand_carplay)
                glyph.layoutParams = glyph.layoutParams.apply { width = 28; height = 28 }
            }
            else -> {
                mark.backgroundTintList = ColorStateList.valueOf(getColor(R.color.mark_bluetooth))
                glyph.setImageResource(R.drawable.brand_bluetooth)
                glyph.layoutParams = glyph.layoutParams.apply { width = 18; height = 18 }
            }
        }
        mark.alpha = if (live) 1f else Kit.DIMMED
    }

    // Source picker

    private fun togglePicker() {
        picker?.let { it.dismiss(); return }
        val trigger = findViewById<TextView>(R.id.player_source)
        val card = LayoutInflater.from(this).inflate(R.layout.popover_source, null)
        val phone = BluetoothPhones.connectedName()
        val onRadio = radio.playing
        val stick = getSystemService(StorageManager::class.java).storageVolumes
            .any { it.isRemovable && it.state == Environment.MEDIA_MOUNTED }
        card.findViewById<TextView>(R.id.source_count).text = "4 sources"

        val bluetooth = card.findViewById<View>(R.id.source_bluetooth)
        card.findViewById<TextView>(R.id.source_bluetooth_line).text = phone ?: "No phone connected"
        card.findViewById<View>(R.id.source_bluetooth_check).visibility = if (!onRadio && phone != null) View.VISIBLE else View.INVISIBLE
        bluetooth.isSelected = !onRadio && phone != null
        if (phone == null) unavailable(bluetooth, card.findViewById(R.id.source_bluetooth_mark), card.findViewById(R.id.source_bluetooth_name))
        else bluetooth.setOnClickListener {
            if (radio.playing) radio.off()
            music.play()
            picker?.dismiss()
        }

        val radioRow = card.findViewById<View>(R.id.source_radio)
        card.findViewById<TextView>(R.id.source_radio_line).text =
            if (onRadio) "Playing FM ${Tuner.label(radio.kilohertz)}" else "Last played FM ${Tuner.label(radio.kilohertz)}"
        card.findViewById<View>(R.id.source_radio_check).visibility = if (onRadio) View.VISIBLE else View.INVISIBLE
        radioRow.isSelected = onRadio
        radioRow.setOnClickListener {
            picker?.dismiss()
            radio.tune(radio.kilohertz)
            startActivity(Intent(this, RadioActivity::class.java))
        }
        card.findViewById<View>(R.id.source_carplay).setOnClickListener { picker?.dismiss(); CarPlayEngine.open(this) }

        // No USB player of our own yet: a stick opens in the file manager.
        val usb = card.findViewById<ViewGroup>(R.id.source_usb)
        val files = packageManager.getLaunchIntentForPackage(FILE_MANAGER)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        card.findViewById<TextView>(R.id.source_usb_line).text = if (stick) "Opens in the file manager" else "Plug in a stick to play from it"
        if (!stick || files == null) {
            unavailable(usb, card.findViewById(R.id.source_usb_mark), card.findViewById(R.id.source_usb_name))
            usb.getChildAt(usb.childCount - 1).visibility = View.GONE
        } else usb.setOnClickListener { picker?.dismiss(); startActivity(files) }

        trigger.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.g_chevron_up, 0)
        picker = Popover(this, trigger, card, Popover.Placement.BELOW_TRIGGER, width = PICKER_WIDTH).also {
            it.onDismiss = {
                picker = null
                trigger.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.g_chevron_down, 0)
            }
            it.show()
        }
    }

    /** Source row rule: unavailable = dimmed mark, grey text, not tappable. */
    private fun unavailable(row: View, mark: View, name: TextView) {
        mark.alpha = Kit.DIMMED
        name.setTextColor(getColor(R.color.text_secondary))
        row.isClickable = false
    }

    private companion object {
        const val PICKER_WIDTH = 440
        const val FILE_MANAGER = "com.mediatek.filemanager"
    }
}
