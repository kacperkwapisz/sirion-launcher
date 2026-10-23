package pl.perier.sirion.ui.settings

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import pl.perier.sirion.R
import pl.perier.sirion.logic.Sound
import pl.perier.sirion.ui.BalancePad
import pl.perier.sirion.ui.CentredSlider

/** Settings · Sound and Balance and fader. Goes straight to the TDA7729 chip through the root helper. */
class SoundSettings(private val host: SettingsActivity) {
    private val kit get() = host.kit
    private val store get() = host.app.sound

    fun screen(): View {
        val sound = store.current
        val presets = Sound.Preset.entries
        val header = kit.header("Sound", sound.line, kit.segmented(presets.map { it.label }, sound.preset.ordinal) { i ->
            store.current = store.current.withPreset(presets[i])
            host.render()
        })
        val bands = Sound.Band.entries.map { band ->
            val value = kit.valueText(Sound.decibels(sound.level(band)))
            val slider = CentredSlider(host, Sound.BAND_RANGE, sound.level(band)) { v, done ->
                store.current = store.current.withBand(band, v)
                value.text = Sound.decibels(v)
                // Moving a band makes it Custom; show that once the finger lifts.
                if (done) host.render()
            }
            kit.sliderRow(band.label, null, slider, value)
        }
        val position = kit.card(kit.row(
            "Balance and fader", "Where the sound is centred in the car",
            trailing = kit.inner(sound.positionText, chevron = true) { host.open(::balance) },
        ))
        return kit.column(header, kit.card(*bands.toTypedArray()), position)
    }

    private fun balance(): View {
        val sound = store.current
        val balanceValue = kit.text(R.style.Text_CardTitle, Sound.balanceText(sound.balance))
        val faderValue = kit.text(R.style.Text_CardTitle, Sound.faderText(sound.fader))
        val pad = BalancePad(host, Sound.POSITION_STEPS, sound.balance, sound.fader) { b, f, done ->
            store.current = store.current.withPosition(b, f)
            balanceValue.text = Sound.balanceText(b)
            faderValue.text = Sound.faderText(f)
            if (done) host.render()
        }
        val jumps = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
            Sound.Jump.entries.forEachIndexed { i, jump ->
                val chosen = jump.balance == sound.balance && jump.fader == sound.fader
                val pick = {
                    store.current = store.current.withPosition(jump.balance, jump.fader)
                    host.render()
                }
                val button = if (chosen) kit.inner(jump.label, onClick = pick).apply {
                    setBackgroundResource(R.drawable.bg_choice)
                    setTextColor(host.getColor(R.color.text_on_light))
                } else kit.inner(jump.label, onClick = pick)
                button.setPadding(kit.dp(6), 0, kit.dp(6), 0)
                button.isSingleLine = true
                addView(button, LinearLayout.LayoutParams(0, kit.dp(64), 1f).apply { if (i > 0) marginStart = kit.dp(8) })
            }
        }
        val side = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            addView(readout("Balance", balanceValue))
            addView(divider())
            addView(readout("Fader", faderValue))
            addView(divider())
            addView(View(host), LinearLayout.LayoutParams(-1, 0, 1f))
            addView(kit.text(R.style.Text_Context, "Jump to"))
            addView(jumps, LinearLayout.LayoutParams(-1, -2).apply { topMargin = kit.dp(10) })
        }
        val body = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundResource(R.drawable.bg_card)
            setPadding(kit.dp(20), kit.dp(20), kit.dp(20), kit.dp(20))
            addView(pad, LinearLayout.LayoutParams(kit.dp(346), kit.dp(338)))
            addView(side, LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = kit.dp(28) })
        }
        return kit.column(
            kit.backHeader("Balance and fader", "Drag the dot to where you want the sound centred", host::back),
            body,
        )
    }

    private fun readout(label: String, value: TextView) = LinearLayout(host).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, kit.dp(4), 0, kit.dp(14))
        addView(kit.text(R.style.Text_Context, label))
        addView(value, LinearLayout.LayoutParams(-2, -2).apply { topMargin = kit.dp(4) })
    }

    private fun divider() = View(host).apply { setBackgroundColor(host.getColor(R.color.surface_inner)) }
        .also { it.layoutParams = LinearLayout.LayoutParams(-1, kit.dp(1)).apply { bottomMargin = kit.dp(14) } }
}
