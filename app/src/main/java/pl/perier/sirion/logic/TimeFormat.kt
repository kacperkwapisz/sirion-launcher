package pl.perier.sirion.logic

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Text for the status strip and the music scrubber. */
object TimeFormat {
    private val dateFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)
    private const val MINUS = '−'

    /** 24-hour times are padded (07:12). The status strip is too quiet for "PM"; a 12-hour clock just shows 9:48. */
    fun clock(now: LocalDateTime, h24: Boolean = true): String =
        if (h24) "%02d:%02d".format(now.hour, now.minute) else "%d:%02d".format((now.hour + 11) % 12 + 1, now.minute)

    /** A call's running time reads like a clock: 04:12. */
    fun callTimer(ms: Long): String {
        val total = ms.coerceAtLeast(0) / 1000
        val hours = total / 3600
        return if (hours > 0) elapsed(ms) else "%02d:%02d".format(total / 60, total % 60)
    }

    fun date(now: LocalDateTime): String = dateFormat.format(now)

    fun elapsed(ms: Long): String {
        val total = ms.coerceAtLeast(0) / 1000
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val seconds = total % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
    }

    fun remaining(positionMs: Long, durationMs: Long): String =
        MINUS + elapsed((durationMs - positionMs).coerceAtLeast(0))
}
