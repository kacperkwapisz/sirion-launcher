package pl.perier.sirion.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import android.widget.Toast
import pl.perier.sirion.R
import pl.perier.sirion.SirionApp

/**
 * Covers the screen while an update installs, in the startup look, so the
 * driver sees what's happening instead of a frozen or dark screen. Installing
 * replaces the launcher, which ends this screen; Home's starting window
 * shows the same D until the new version is up. If the install fails, the
 * update script says so and this screen goes back.
 */
class UpdatingActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private var slide: ObjectAnimator? = null

    private val failed = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.w(TAG, "Update didn't install: ${intent.getStringExtra("error")}")
            giveUp()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_updating)
        val version = intent.getStringExtra(EXTRA_VERSION).orEmpty()
        findViewById<TextView>(R.id.updating_line).text = "Updating to Sirion $version"
        registerReceiver(failed, IntentFilter(UPDATE_FAILED))
        if (!(application as SirionApp).updater.install()) {
            giveUp()
            return
        }
        // Still here long after the install should have finished: something's stuck.
        main.postDelayed(::giveUp, GIVE_UP_MS)
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        startSlide()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onPause() {
        super.onPause()
        slide?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        main.removeCallbacksAndMessages(null)
        unregisterReceiver(failed)
    }

    /** The install has no progress to read, so the fill slides along the track. */
    private fun startSlide() {
        val track = findViewById<View>(R.id.updating_track)
        val fill = findViewById<View>(R.id.updating_fill)
        track.post {
            slide = ObjectAnimator.ofFloat(fill, View.TRANSLATION_X, 0f, (track.width - fill.width).toFloat()).apply {
                duration = SLIDE_MS
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
        }
    }

    private fun giveUp() {
        if (isFinishing) return
        Toast.makeText(this, R.string.update_failed, Toast.LENGTH_LONG).show()
        finish()
    }

    @Deprecated("Nothing to go back to while the update installs.")
    override fun onBackPressed() = Unit

    companion object {
        const val EXTRA_VERSION = "version"
        const val UPDATE_FAILED = "pl.perier.sirion.UPDATE_FAILED"
        private const val TAG = "SirionUpdating"
        private const val SLIDE_MS = 1_200L
        private const val GIVE_UP_MS = 5 * 60_000L
    }
}
