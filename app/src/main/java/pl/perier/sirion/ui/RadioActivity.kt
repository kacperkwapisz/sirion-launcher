package pl.perier.sirion.ui

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import pl.perier.sirion.R
import pl.perier.sirion.SirionApp
import pl.perier.sirion.logic.Tuner

/** Radio · Playing / Seeking. Talks to the tuner through our root helper; no vendor radio app. */
class RadioActivity : Activity() {
    private val radio get() = (application as SirionApp).radio
    private lateinit var bar: BottomBar
    private val presetViews = mutableListOf<View>()
    private val onChange: () -> Unit = { render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_radio)
        bar = BottomBar(this, BottomBar.Nav.APPS).also { it.bind() }

        findViewById<View>(R.id.seek_down).setOnClickListener { radio.seek(up = false) }
        findViewById<View>(R.id.seek_up).setOnClickListener { radio.seek(up = true) }
        findViewById<View>(R.id.step_down).setOnClickListener { radio.step(up = false) }
        findViewById<View>(R.id.step_up).setOnClickListener { radio.step(up = true) }
        buildPresetGrid()
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        bar.resume()
        radio.listen(onChange)
        if (!radio.playing) radio.tune(radio.kilohertz)
        render()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onPause() {
        super.onPause()
        bar.pause()
        radio.stopListening(onChange)
    }

    private fun buildPresetGrid() {
        val grid = findViewById<LinearLayout>(R.id.preset_grid)
        for (row in 0 until Tuner.SLOTS / 2) {
            // 76px rows spread over the card, as in the design.
            if (row > 0) grid.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            grid.addView(line, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, PRESET_HEIGHT))
            for (col in 0..1) {
                val slot = row * 2 + col
                val button = LayoutInflater.from(this).inflate(R.layout.radio_preset, line, false)
                (button.layoutParams as LinearLayout.LayoutParams).apply { if (col == 1) marginStart = GAP }
                button.setOnClickListener { radio.presets.getOrNull(slot)?.let(radio::tune) }
                button.setOnLongClickListener {
                    radio.savePreset(slot)
                    Toast.makeText(this, "Saved FM ${Tuner.label(radio.kilohertz)}", Toast.LENGTH_SHORT).show()
                    true
                }
                line.addView(button)
                presetViews += button
            }
        }
    }

    private fun render() {
        findViewById<TextView>(R.id.tuner_frequency).text = Tuner.label(radio.kilohertz)
        findViewById<TextView>(R.id.tuner_frequency)
            .setTextColor(getColor(if (radio.seeking) R.color.text_secondary else R.color.text_primary))
        findViewById<TextView>(R.id.tuner_station).text =
            if (radio.seeking) "Looking for the next station" else radio.name ?: "FM ${Tuner.label(radio.kilohertz)}"
        findViewById<TextView>(R.id.tuner_line).text = if (radio.seeking) "Tap any button to stop" else ""
        findViewById<TextView>(R.id.tuner_context).text = when {
            radio.seeking -> if (radio.seekingUp) "Searching up" else "Searching down"
            radio.stereo -> "FM stereo"
            else -> "FM"
        }
        // The seek button that's searching stays lit.
        findViewById<View>(R.id.seek_up).setBackgroundResource(if (radio.seeking && radio.seekingUp) R.drawable.bg_inner_active else R.drawable.bg_inner)
        findViewById<View>(R.id.seek_down).setBackgroundResource(if (radio.seeking && !radio.seekingUp) R.drawable.bg_inner_active else R.drawable.bg_inner)
        val presets = radio.presets
        presetViews.forEachIndexed { slot, view ->
            val preset = presets.getOrNull(slot)
            (view as LinearLayout).showPreset(preset, playing = preset != null && Tuner.kilohertz(preset) == radio.kilohertz && !radio.seeking)
        }
    }

    private companion object {
        const val GAP = 6
        const val PRESET_HEIGHT = 76
    }
}
