package pl.perier.sirion.ui.settings

import android.view.View
import pl.perier.sirion.logic.Brightness
import pl.perier.sirion.logic.SettingsText
import pl.perier.sirion.logic.TimeFormat
import java.time.LocalDate
import java.time.ZoneId

/** Settings · Display: backlight mode and the two levels brightnessd moves between. */
class DisplaySettings(private val host: SettingsActivity) {
    private val kit get() = host.kit
    private val store get() = host.app.brightness

    fun screen(): View {
        val brightness = store.current
        val modes = listOf("auto", "day", "night")
        val header = kit.header(
            "Display",
            brightness.context(store.sun(), ZoneId.systemDefault()),
            kit.segmented(listOf("Auto", "Day", "Night"), modes.indexOf(brightness.mode).coerceAtLeast(0)) { i ->
                store.current = store.current.copy(mode = modes[i])
                host.render()
            },
        )
        val levels = kit.card(
            level("Day level", "Sun up", brightness.day) { store.current.copy(day = it) },
            level("Night level", "Sun down", brightness.night) { store.current.copy(night = it) },
        )
        val (rise, set) = store.sunTimes()
        val sun = if (rise != null && set != null) {
            val today = LocalDate.now()
            kit.card(kit.row(
                "Sunrise ${TimeFormat.clock(rise.atDate(today))} · Sunset ${TimeFormat.clock(set.atDate(today))}",
                if (store.hasGpsFix()) "Where the car is, from GPS" else "For Szczecin until GPS finds the car",
            ))
        } else null
        return kit.column(header, levels, sun)
    }

    private fun level(label: String, line: String, value: Int, with: (Int) -> Brightness): View {
        val text = kit.valueText(SettingsText.percent(value))
        val slider = kit.seekBar(255, value) { v, done ->
            val level = v.coerceAtLeast(Brightness.MIN_LEVEL)
            text.text = SettingsText.percent(level)
            // The screen follows the finger; the last value is sent on release.
            if (done) store.current = with(level) else store.setLive(with(level))
        }
        return kit.sliderRow(label, line, slider, text)
    }
}
