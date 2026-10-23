package pl.perier.sirion.ui

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import pl.perier.sirion.R
import pl.perier.sirion.data.MediaVolume

/**
 * Our volume card, over any app (CarPlay full screen included), whenever the
 * volume changes and our bottom bar isn't on screen to show it.
 *
 * The unit's buttons never reach apps as key presses: the vendor's code in
 * Android sets the volume itself. So this watches the volume instead, and the
 * no-volume-dialog overlay turns off Android's own pop-up. An accessibility
 * service only because that's what may draw over every app; turned on by setup.
 */
class VolumePopup : AccessibilityService() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var volume: MediaVolume
    private lateinit var audio: AudioManager
    private var card: View? = null
    private val hide = Runnable { hideCard() }

    private val changes = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val stream = intent.getIntExtra(EXTRA_STREAM, -1)
            if (stream != AudioManager.STREAM_MUSIC && stream != AudioManager.STREAM_VOICE_CALL) return
            val level = intent.getIntExtra(EXTRA_VALUE, -1)
            if (level < 0 || level == intent.getIntExtra(EXTRA_PREVIOUS, -1)) return
            // The bar listens for volume changes itself.
            if (BottomBar.onScreen) return
            showCard(stream, level)
        }
    }

    override fun onServiceConnected() {
        volume = MediaVolume(this)
        audio = getSystemService(AudioManager::class.java)
        registerReceiver(changes, IntentFilter(VOLUME_CHANGED))
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(changes) }
        hideCard()
        super.onDestroy()
    }

    private fun showCard(stream: Int, level: Int) {
        val view = card ?: LayoutInflater.from(this).inflate(R.layout.volume_card, null).also { view ->
            // The layout's own 440×88 is lost without a parent, so the window carries it.
            val density = resources.displayMetrics.density
            val params = WindowManager.LayoutParams(
                (WIDTH_DP * density).toInt(),
                (HEIGHT_DP * density).toInt(),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = (BOTTOM_DP * density).toInt()
            }
            view.alpha = 0f
            getSystemService(WindowManager::class.java).addView(view, params)
            view.animate().alpha(1f).setDuration(SHOW_MS).start()
            card = view
        }
        // Muted music shows the level it comes back to, greyed, as on the bar.
        val muted = stream == AudioManager.STREAM_MUSIC && volume.muted
        val shown = if (muted && volume.remembered > 0) volume.remembered else level
        view.findViewById<ImageView>(R.id.card_volume_glyph)
            .setImageResource(if (muted) R.drawable.g_speaker_muted else R.drawable.g_speaker)
        view.findViewById<ProgressBar>(R.id.card_volume_level).apply {
            progressDrawable = getDrawable(if (muted) R.drawable.scrubber_track_muted else R.drawable.slider_track)
            max = audio.getStreamMaxVolume(stream)
            progress = shown
        }
        view.findViewById<TextView>(R.id.card_volume_value).apply {
            text = shown.toString()
            setTextColor(getColor(if (muted) R.color.text_secondary else R.color.text_primary))
        }
        main.removeCallbacks(hide)
        main.postDelayed(hide, VISIBLE_MS)
    }

    private fun hideCard() {
        val view = card ?: return
        card = null
        view.animate().alpha(0f).setDuration(HIDE_MS).withEndAction {
            runCatching { getSystemService(WindowManager::class.java).removeView(view) }
        }.start()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) = Unit

    override fun onInterrupt() = Unit

    private companion object {
        const val VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
        const val EXTRA_STREAM = "android.media.EXTRA_VOLUME_STREAM_TYPE"
        const val EXTRA_VALUE = "android.media.EXTRA_VOLUME_STREAM_VALUE"
        const val EXTRA_PREVIOUS = "android.media.EXTRA_PREV_VOLUME_STREAM_VALUE"
        const val BOTTOM_DP = 24
        const val WIDTH_DP = 440
        const val HEIGHT_DP = 88
        const val VISIBLE_MS = 1_500L
        const val SHOW_MS = 120L
        const val HIDE_MS = 150L
    }
}
