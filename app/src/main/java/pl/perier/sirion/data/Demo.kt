package pl.perier.sirion.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Bundle
import pl.perier.sirion.logic.CallScreen
import pl.perier.sirion.logic.Track
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Sample data for checking screens against the Paper designs on the
 * emulator, which has no Bluetooth. Debug builds only, and only while
 * `adb shell setprop debug.sirion.demo 1` is set; the release build never
 * turns it on. `debug.sirion.demo.call` = incoming, calling or incall shows
 * a call.
 */
object Demo {
    private var debuggable = false

    fun init(context: Context) {
        debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    }

    val on: Boolean get() = debuggable && SystemProps.get("debug.sirion.demo") == "1"

    const val PHONE = "Kacper's iPhone"

    /** `debug.sirion.demo.carplay 1` shows the screens as if CarPlay were running. */
    val carPlay: Boolean get() = on && SystemProps.get("debug.sirion.demo.carplay") == "1"
    val track = Track("Midnight City", "M83", 243_000)
    const val ALBUM = "Hurry Up, We're Dreaming"
    const val POSITION_MS = 99_000L

    val contacts = listOf(
        Contact("Ania", "+48 601 234 567"),
        Contact("Mama", "+48 602 345 678"),
        Contact("Tomek", "+48 603 456 789"),
        Contact("Warsztat Kowalski", "+48 91 433 22 11"),
    )

    fun recents(): List<RecentCall> {
        val now = LocalDateTime.now()
        val f = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
        return listOf(
            RecentCall("Ania", "+48 601 234 567", "missed", now.minusMinutes(20).format(f)),
            RecentCall("Mama", "+48 602 345 678", "incoming", now.minusHours(3).format(f)),
            RecentCall("Tomek", "+48 603 456 789", "outgoing", now.minusDays(1).format(f)),
        )
    }

    val call: Call?
        get() {
            val screen = when (SystemProps.get("debug.sirion.demo.call")) {
                "incoming" -> CallScreen.Incoming
                "calling" -> CallScreen.Calling
                "incall" -> CallScreen.InCall
                else -> return null
            }
            return Call(screen, "+48 601 234 567", Bundle())
        }
}
