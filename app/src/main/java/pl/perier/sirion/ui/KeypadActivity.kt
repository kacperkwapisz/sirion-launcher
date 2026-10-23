package pl.perier.sirion.ui

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import pl.perier.sirion.R
import pl.perier.sirion.SirionApp
import pl.perier.sirion.data.BluetoothPhones
import pl.perier.sirion.data.PhoneBook
import pl.perier.sirion.logic.PhoneText
import kotlin.concurrent.thread

/**
 * Phone · Keypad. Dials through the phone over Bluetooth. Opened from a call
 * ([EXTRA_IN_CALL]) it sends each key as a tone instead, for menus and PINs.
 */
class KeypadActivity : Activity() {
    private val calls get() = (application as SirionApp).calls
    private lateinit var bar: BottomBar
    private lateinit var kit: Kit
    private var typed = ""
    private var book: List<Pair<String, String>> = emptyList()
    private val inCall get() = intent.getBooleanExtra(EXTRA_IN_CALL, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_keypad)
        kit = Kit(this)
        bar = BottomBar(this, BottomBar.Nav.HOME).also { it.bind() }
        findViewById<TextView>(R.id.keypad_context).text = BluetoothPhones.connectedName().orEmpty()
        buildKeys()

        findViewById<View>(R.id.keypad_backspace).apply {
            setOnClickListener { typed = typed.dropLast(1); render() }
            setOnLongClickListener { typed = ""; render(); true }
        }
        findViewById<TextView>(R.id.keypad_call).apply {
            centreGlyph()
            if (inCall) {
                // During a call this goes back to it; the tones are already sent.
                setText(R.string.back_to_call)
                setBackgroundResource(R.drawable.bg_inner)
                setTextColor(getColor(R.color.text_primary))
                setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
                setOnClickListener { finish() }
            } else setOnClickListener {
                if (typed.isNotEmpty() && !calls.dial(typed)) Toast.makeText(this@KeypadActivity, R.string.no_phone, Toast.LENGTH_SHORT).show()
            }
        }
        thread {
            val contacts = PhoneBook(this).contacts().map { it.name to it.number }
            runOnUiThread { book = contacts; render() }
        }
        render()
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        bar.resume()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onPause() {
        super.onPause()
        bar.pause()
    }

    private fun press(key: Char) {
        typed += key
        if (inCall) calls.sendTone(key)
        render()
    }

    private fun render() {
        findViewById<TextView>(R.id.keypad_number).text = PhoneText.groupDigits(typed)
        findViewById<TextView>(R.id.keypad_line).text = when {
            inCall -> "Tones go to the call"
            typed.isEmpty() -> "Type a number"
            else -> PhoneText.keypadMatch(typed, book) ?: "No match in your contacts"
        }
        findViewById<View>(R.id.keypad_backspace).visibility = if (typed.isEmpty()) View.INVISIBLE else View.VISIBLE
        if (!inCall) findViewById<View>(R.id.keypad_call).alpha = if (typed.isEmpty()) Kit.DIMMED else 1f
    }

    /** 3 × 4 keys: digit 36px, letters 16px grey; hold 0 for +. */
    private fun buildKeys() {
        val grid = findViewById<LinearLayout>(R.id.keypad_keys)
        KEYS.chunked(3).forEachIndexed { r, row ->
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.forEachIndexed { c, (digit, letters) ->
                val key = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setBackgroundResource(R.drawable.bg_inner)
                    isClickable = true
                    addView(kit.text(R.style.Text_KeypadDigit, digit.toString()), kit.wrap())
                    addView(kit.text(R.style.Text_KeypadLetters, letters).apply { minHeight = kit.dp(20) }, kit.wrap())
                    setOnClickListener { press(digit) }
                    if (digit == '0') setOnLongClickListener { press('+'); true }
                }
                line.addView(key, LinearLayout.LayoutParams(0, -1, 1f).apply { if (c > 0) marginStart = kit.dp(6) })
            }
            grid.addView(line, LinearLayout.LayoutParams(-1, 0, 1f).apply { if (r > 0) topMargin = kit.dp(6) })
        }
    }

    companion object {
        const val EXTRA_IN_CALL = "in_call"
        private val KEYS = listOf(
            '1' to "", '2' to "ABC", '3' to "DEF",
            '4' to "GHI", '5' to "JKL", '6' to "MNO",
            '7' to "PQRS", '8' to "TUV", '9' to "WXYZ",
            '*' to "", '0' to "+", '#' to "",
        )
    }
}
