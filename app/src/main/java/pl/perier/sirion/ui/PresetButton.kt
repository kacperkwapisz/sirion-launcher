package pl.perier.sirion.ui

import android.content.res.ColorStateList
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.TextView
import pl.perier.sirion.R
import pl.perier.sirion.logic.RadioPreset

/**
 * Paints one preset button: playing (white fill), default, or an empty slot
 * ("+ Hold to save" on one line, dashed outline). Same piece on Home and Radio.
 */
fun LinearLayout.showPreset(preset: RadioPreset?, playing: Boolean) {
    val frequency = findViewById<TextView>(R.id.preset_frequency)
    val name = findViewById<TextView>(R.id.preset_name)
    val secondary = context.getColor(R.color.text_secondary)
    if (preset == null) {
        setBackgroundResource(R.drawable.bg_preset_empty)
        orientation = LinearLayout.HORIZONTAL
        frequency.visibility = android.view.View.GONE
        name.text = "Hold to save"
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18f)
        name.setTextColor(secondary)
        name.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.g_plus_22, 0, 0, 0)
        name.compoundDrawableTintList = ColorStateList.valueOf(secondary)
        name.compoundDrawablePadding = (10 * resources.displayMetrics.density).toInt()
        (name.layoutParams as LinearLayout.LayoutParams).topMargin = 0
        return
    }
    orientation = LinearLayout.VERTICAL
    frequency.visibility = android.view.View.VISIBLE
    name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
    name.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
    (name.layoutParams as LinearLayout.LayoutParams).topMargin = 2
    setBackgroundResource(if (playing) R.drawable.bg_preset_playing else R.drawable.bg_inner)
    frequency.text = preset.label
    frequency.setTextColor(context.getColor(if (playing) R.color.text_on_light else R.color.text_primary))
    name.text = preset.name
    name.setTextColor(context.getColor(if (playing) R.color.surface_track else R.color.text_secondary))
}
