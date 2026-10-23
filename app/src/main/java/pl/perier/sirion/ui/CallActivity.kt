package pl.perier.sirion.ui

import android.app.Activity
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import pl.perier.sirion.R
import pl.perier.sirion.SirionApp
import pl.perier.sirion.data.BluetoothPhones
import pl.perier.sirion.data.Call
import pl.perier.sirion.data.PhoneBook
import pl.perier.sirion.logic.CallScreen
import kotlin.concurrent.thread

/** Call · Incoming / Calling / In call, in place of the vendor's call popup. */
class CallActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private val calls get() = (application as SirionApp).calls
    private lateinit var bar: BottomBar
    private lateinit var audio: AudioManager
    private var connectedAt = 0L
    private var name: String? = null

    private val onCall: (Call?) -> Unit = { call -> main.post { show(call) } }

    private val timer = object : Runnable {
        override fun run() {
            show(calls.current)
            main.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call)
        bar = BottomBar(this, BottomBar.Nav.HOME).also { it.bind() }
        audio = getSystemService(AudioManager::class.java)
        findViewById<TextView>(R.id.call_context).text = BluetoothPhones.connectedName().orEmpty()

        findViewById<View>(R.id.call_answer).setOnClickListener { calls.answer() }
        findViewById<View>(R.id.call_decline).setOnClickListener { calls.decline() }
        findViewById<View>(R.id.call_end).setOnClickListener { calls.end() }
        findViewById<View>(R.id.call_use_phone).setOnClickListener { calls.moveToPhone(); finish() }
        findViewById<View>(R.id.call_keypad).setOnClickListener {
            startActivity(Intent(this, KeypadActivity::class.java).putExtra(KeypadActivity.EXTRA_IN_CALL, true))
        }
        listOf(R.id.call_decline, R.id.call_mute, R.id.call_keypad, R.id.call_use_phone, R.id.call_end, R.id.call_answer)
            .forEach { findViewById<TextView>(it).centreGlyph() }
        findViewById<View>(R.id.call_mute).setOnClickListener {
            audio.isMicrophoneMute = !audio.isMicrophoneMute
            show(calls.current)
        }
        lookUpName(calls.current?.number.orEmpty())
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        bar.resume()
        calls.listen(onCall)
        main.post(timer)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onPause() {
        super.onPause()
        bar.pause()
        calls.stopListening(onCall)
        main.removeCallbacks(timer)
    }

    override fun onDestroy() {
        audio.isMicrophoneMute = false
        super.onDestroy()
    }

    private fun lookUpName(number: String) {
        if (number.isEmpty()) return
        thread {
            val match = PhoneBook(this).contacts().firstOrNull { CallScreen.sameNumber(it.number, number) }?.name
            main.post { name = match; show(calls.current) }
        }
    }

    private fun show(call: Call?) {
        if (call == null) {
            finish()
            return
        }
        if (call.screen == CallScreen.InCall && connectedAt == 0L) connectedAt = SystemClock.elapsedRealtime()
        val shown = name ?: call.number.ifEmpty { getString(R.string.unknown_caller) }
        findViewById<TextView>(R.id.call_name).showName(shown)
        findViewById<TextView>(R.id.call_initials).showInitials(shown)
        val elapsed = if (connectedAt == 0L) 0 else SystemClock.elapsedRealtime() - connectedAt
        findViewById<TextView>(R.id.call_line).text = call.screen.line(elapsed)

        fun visible(id: Int, on: Boolean) { findViewById<View>(id).visibility = if (on) View.VISIBLE else View.GONE }
        visible(R.id.call_answer, call.screen == CallScreen.Incoming)
        visible(R.id.call_decline, call.screen == CallScreen.Incoming)
        visible(R.id.call_mute, call.screen != CallScreen.Incoming)
        visible(R.id.call_use_phone, call.screen == CallScreen.InCall)
        visible(R.id.call_keypad, call.screen == CallScreen.InCall)
        visible(R.id.call_end, call.screen != CallScreen.Incoming)
        findViewById<TextView>(R.id.call_end).setText(if (call.screen == CallScreen.Calling) R.string.cancel else R.string.end)
        findViewById<TextView>(R.id.call_mute).apply {
            setText(if (audio.isMicrophoneMute) R.string.unmute else R.string.mute)
            setCompoundDrawablesRelativeWithIntrinsicBounds(if (audio.isMicrophoneMute) R.drawable.g_mic_off else R.drawable.g_mic, 0, 0, 0)
        }
    }
}
