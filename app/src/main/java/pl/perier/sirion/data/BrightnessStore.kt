package pl.perier.sirion.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import pl.perier.sirion.logic.Brightness
import pl.perier.sirion.logic.Sun
import java.time.Instant

/**
 * The brightness the user chose, kept on our side and handed to brightnessd
 * (through the root helper) whenever it changes and at every start.
 */
class BrightnessStore(private val context: Context, private val helper: RootHelper) {
    private val prefs = context.getSharedPreferences("brightness", Context.MODE_PRIVATE)

    var current: Brightness
        get() = Brightness(
            mode = prefs.getString("mode", "auto") ?: "auto",
            day = prefs.getInt("day", 210),
            night = prefs.getInt("night", 30),
        )
        set(value) {
            prefs.edit().putString("mode", value.mode).putInt("day", value.day).putInt("night", value.night).apply()
            helper.send(value.command)
        }

    private var lastLiveAt = 0L

    /**
     * While a slider is dragged: send at most every [LIVE_MS], so the screen
     * follows the finger (brightnessd jumps straight to what we send).
     */
    fun setLive(value: Brightness) {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastLiveAt < LIVE_MS) return
        lastLiveAt = now
        current = value
    }

    /** Sends the saved settings, which also starts brightnessd after a boot. */
    fun apply() = helper.send(current.command)

    fun sun(now: Instant = Instant.now()): Sun.Change {
        val (lat, lon) = location()
        return Sun.nextChange(now, lat, lon)
    }

    /** Today's sunrise and sunset where the car is. */
    fun sunTimes(): Pair<java.time.LocalTime?, java.time.LocalTime?> {
        val (lat, lon) = location()
        return Sun.today(java.time.LocalDate.now(), java.time.ZoneId.systemDefault(), lat, lon)
    }

    fun hasGpsFix() = gpsFix() != null

    private fun location(): Pair<Double, Double> = gpsFix()?.let { it.latitude to it.longitude } ?: SZCZECIN

    @SuppressLint("MissingPermission")
    private fun gpsFix() = runCatching {
        context.getSystemService(LocationManager::class.java).getLastKnownLocation(LocationManager.GPS_PROVIDER)
    }.getOrNull()

    private companion object {
        val SZCZECIN = 53.452 to 14.212
        const val LIVE_MS = 120L
    }
}
