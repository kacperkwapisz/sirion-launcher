package pl.perier.sirion.ui

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import pl.perier.sirion.R
import pl.perier.sirion.data.MediaVolume

/**
 * Takes the unit's volume and mute buttons (they're plain key events from the
 * touch panel) before Android does, so its own volume pop-up never shows.
 * Changes the volume silently, with the same mute rules as the bar. When our
 * bottom bar is on screen, the bar's value is the feedback; otherwise (CarPlay
 * full screen, say) our volume card shows at the bottom for a moment.
 *
 * Turned on by setup (an accessibility service, so it works over any app).
 */
class VolumeKeys : AccessibilityService() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var volume: MediaVolume
    private var card: View? = null
    private val hide = Runnable { hideCard() }

    override fun onServiceConnected() {
        volume = MediaVolume(this)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) Log.d(TAG, "key ${KeyEvent.keyCodeToString(event.keyCode)}")
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                // Holding a button repeats the step.
                if (event.action == KeyEvent.ACTION_DOWN) {
                    volume.step(if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) +1 else -1)
                    feedback()
                }
                return true
            }
            KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_MUTE -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    volume.toggleMute()
                    feedback()
                }
                return true
            }
        }
        return false
    }

    private fun feedback() {
        // The bar listens for volume changes itself.
        if (BottomBar.onScreen) return
        showCard()
    }

    private fun showCard() {
        val view = card ?: LayoutInflater.from(this).inflate(R.layout.volume_card, null).also { view ->
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = (BOTTOM_DP * resources.displayMetrics.density).toInt()
            }
            view.alpha = 0f
            getSystemService(WindowManager::class.java).addView(view, params)
            view.animate().alpha(1f).setDuration(SHOW_MS).start()
            card = view
        }
        val muted = volume.muted
        val level = if (muted && volume.remembered > 0) volume.remembered else volume.current
        view.findViewById<ImageView>(R.id.card_volume_glyph)
            .setImageResource(if (muted) R.drawable.g_speaker_muted else R.drawable.g_speaker)
        view.findViewById<ProgressBar>(R.id.card_volume_level).apply {
            progressDrawable = getDrawable(if (muted) R.drawable.scrubber_track_muted else R.drawable.slider_track)
            max = volume.max
            progress = level
        }
        view.findViewById<TextView>(R.id.card_volume_value).apply {
            text = level.toString()
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
        const val TAG = "SirionVolumeKeys"
        const val BOTTOM_DP = 24
        const val VISIBLE_MS = 1_500L
        const val SHOW_MS = 120L
        const val HIDE_MS = 150L
    }
}
