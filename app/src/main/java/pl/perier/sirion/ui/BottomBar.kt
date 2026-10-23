package pl.perier.sirion.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import pl.perier.sirion.R
import pl.perier.sirion.SirionApp
import pl.perier.sirion.data.MediaVolume
import pl.perier.sirion.logic.TimeFormat
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The status strip and bottom bar, shared by every screen. [active] is the
 * nav item that shows where you are.
 */
class BottomBar(private val activity: Activity, private val active: Nav) {
    enum class Nav { HOME, APPS }

    private val volume = MediaVolume(activity)
    private val brightness get() = (activity.application as SirionApp).brightness
    private val updater get() = (activity.application as SirionApp).updater
    private var popover: Popover? = null

    private val events = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == VOLUME_CHANGED) refreshVolume() else refreshClock()
        }
    }

    fun bind() {
        val home = activity.findViewById<ImageButton>(R.id.nav_home)
        val apps = activity.findViewById<ImageButton>(R.id.nav_apps)
        home.setImageResource(if (active == Nav.HOME) R.drawable.g_home_filled else R.drawable.g_home)
        apps.setImageResource(if (active == Nav.APPS) R.drawable.g_apps_filled else R.drawable.g_apps)
        home.imageTintList = activity.getColorStateList(if (active == Nav.HOME) R.color.accent else R.color.text_primary)
        apps.imageTintList = activity.getColorStateList(if (active == Nav.APPS) R.color.accent else R.color.text_primary)

        home.setOnClickListener {
            if (active != Nav.HOME || activity !is HomeActivity) {
                activity.startActivity(Intent(activity, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            }
        }
        apps.setOnClickListener {
            if (activity !is AppsActivity) activity.startActivity(Intent(activity, AppsActivity::class.java))
        }
        activity.findViewById<ImageButton>(R.id.volume_down).setOnClickListener { volume.step(-1); refreshVolume() }
        activity.findViewById<ImageButton>(R.id.volume_up).setOnClickListener { volume.step(+1); refreshVolume() }
        activity.findViewById<View>(R.id.volume_value_area).setOnClickListener { toggle(::volumePopover) }
        activity.findViewById<ImageButton>(R.id.brightness).setOnClickListener { toggle(::brightnessPopover) }
    }

    private fun toggle(make: () -> Popover) {
        val open = popover
        popover = null
        if (open != null) { open.dismiss(); return }
        popover = make().also { it.onDismiss = { popover = null; refreshVolume() }; it.show() }
    }

    private fun volumePopover(): Popover {
        val view = LayoutInflater.from(activity).inflate(R.layout.popover_volume, null)
        val slider = view.findViewById<SeekBar>(R.id.volume_slider)
        slider.max = volume.max
        slider.progress = volume.current
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                if (fromUser) { volume.set(value); refreshVolume() }
            }
            override fun onStartTrackingTouch(bar: SeekBar) = Unit
            override fun onStopTrackingTouch(bar: SeekBar) = Unit
        })
        view.findViewById<View>(R.id.volume_mute).setOnClickListener {
            volume.toggleMute()
            slider.progress = volume.current
            refreshVolume()
        }
        return Popover(activity, activity.findViewById(R.id.volume_value_area), view)
    }

    private fun brightnessPopover(): Popover {
        val view = LayoutInflater.from(activity).inflate(R.layout.popover_brightness, null)
        val slider = view.findViewById<SeekBar>(R.id.brightness_slider)
        val modes = mapOf(R.id.mode_auto to "auto", R.id.mode_day to "day", R.id.mode_night to "night")
        // Worked out once: it scans the sun's path, too slow for every drag step.
        val isDay = brightness.sun().isDay
        fun render() {
            val sun = brightness.sun()
            val now = brightness.current
            view.findViewById<TextView>(R.id.brightness_context).text = now.context(sun, ZoneId.systemDefault())
            slider.progress = now.level(sun.isDay)
            modes.forEach { (id, mode) ->
                val chosen = mode == now.mode
                view.findViewById<TextView>(id).apply {
                    setBackgroundResource(if (chosen) R.drawable.bg_segment_chosen else 0)
                    setTextColor(activity.getColor(if (chosen) R.color.text_on_light else R.color.text_primary))
                }
            }
        }
        modes.forEach { (id, mode) ->
            view.findViewById<View>(id).setOnClickListener { brightness.current = brightness.current.copy(mode = mode); render() }
        }
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            // The screen follows the finger; the last value is sent on release.
            override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                if (fromUser) brightness.setLive(brightness.current.withLevel(value, isDay))
            }
            override fun onStartTrackingTouch(bar: SeekBar) = Unit
            override fun onStopTrackingTouch(bar: SeekBar) {
                brightness.current = brightness.current.withLevel(bar.progress, isDay)
                render()
            }
        })
        render()
        return Popover(activity, activity.findViewById(R.id.brightness), view)
    }

    fun resume() {
        visible++
        activity.registerReceiver(events, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(VOLUME_CHANGED)
        })
        refreshClock()
        refreshVolume()
        refreshUpdate()
        updater.onReady = ::refreshUpdate
    }

    fun pause() {
        visible = (visible - 1).coerceAtLeast(0)
        updater.onReady = null
        popover?.dismiss()
        popover = null
        activity.unregisterReceiver(events)
    }

    /** Orange download glyph in the status strip while an update waits for Install. */
    private fun refreshUpdate() {
        activity.findViewById<View>(R.id.status_update)?.visibility = if (updater.ready != null) View.VISIBLE else View.GONE
    }

    /** Muted: crossed speaker, the level from before greyed out, on a #262626 pill. */
    private fun refreshVolume() {
        val muted = volume.muted
        activity.findViewById<TextView>(R.id.volume_value).apply {
            text = (if (muted && volume.remembered > 0) volume.remembered else volume.current).toString()
            setTextColor(activity.getColor(if (muted) R.color.text_secondary else R.color.text_primary))
        }
        activity.findViewById<ImageView>(R.id.volume_glyph)
            .setImageResource(if (muted) R.drawable.g_speaker_muted else R.drawable.g_speaker)
        // The open volume popover owns this background until it closes.
        if (popover == null) activity.findViewById<View>(R.id.volume_value_area)
            .setBackgroundResource(if (muted) R.drawable.bg_volume_muted else R.drawable.bg_bar_button)
    }

    private fun refreshClock() {
        val now = LocalDateTime.now()
        activity.findViewById<TextView>(R.id.clock).text = TimeFormat.clock(now, android.text.format.DateFormat.is24HourFormat(activity))
        activity.findViewById<TextView>(R.id.date).text = TimeFormat.date(now)
    }

    companion object {
        /** How many of our screens with the bar are showing; the volume keys use it to skip their own card. */
        @Volatile private var visible = 0
        val onScreen: Boolean get() = visible > 0

        private const val VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
    }
}
