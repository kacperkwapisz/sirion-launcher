package pl.perier.sirion.logic

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Text for contact and recent-call rows. */
object PhoneText {
    private val callTime = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val dayName = DateTimeFormatter.ofPattern("EEEE", Locale.UK)
    private val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.UK)

    /**
     * Up to two initials from the first letter of each word. Emoji and other
     * symbols are skipped whole (an emoji is two chars, and half of one
     * shows as "?"). A bare number keeps its start ("+48"). Empty when the
     * name has no letters; the avatar then shows a person glyph.
     */
    fun initials(name: String): String {
        val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return ""
        if (words.first().first().let { it == '+' || it.isDigit() }) return words.first().take(3)
        return words.mapNotNull { word ->
            word.codePoints().filter(Character::isLetter).findFirst().orElse(-1).takeIf { it >= 0 }
        }.take(2).joinToString("") { String(Character.toChars(it)).uppercase() }
    }

    /** Bluetooth phonebook times look like `20261022T140200`. */
    fun parseCallTime(text: String): LocalDateTime? =
        runCatching { LocalDateTime.parse(text.trim().take(15), callTime) }.getOrNull()

    fun whenText(time: LocalDateTime, now: LocalDateTime): String {
        val days = ChronoUnit.DAYS.between(time.toLocalDate(), now.toLocalDate())
        return when {
            days == 0L -> "today " + TimeFormat.clock(time)
            days == 1L -> "yesterday"
            days in 2..6 -> dayName.format(time)
            else -> shortDate.format(time)
        }
    }

    fun recentLine(type: String, time: LocalDateTime?, now: LocalDateTime): String {
        val label = type.replaceFirstChar { it.uppercase() }
        return if (time == null) label else "$label · ${whenText(time, now)}"
    }

    data class Call(val type: String, val time: LocalDateTime?, val name: String)

    /** "2 missed calls · Ania" for today's missed calls (newest first), or null if none. */
    fun missedToday(calls: List<Call>, now: LocalDateTime): String? {
        val missed = calls.filter { it.type == "missed" && it.time?.toLocalDate() == now.toLocalDate() }
        if (missed.isEmpty()) return null
        val count = if (missed.size == 1) "1 missed call" else "${missed.size} missed calls"
        return "$count · ${missed.first().name}"
    }

    /** Digits as typed on the keypad, in groups of three; "+48" stays together. Codes with * or # are left alone. */
    fun groupDigits(typed: String): String {
        if (typed.any { it == '*' || it == '#' }) return typed
        val (prefix, rest) = if (typed.startsWith("+")) typed.take(3) to typed.drop(3) else "" to typed
        val groups = rest.chunked(3)
        return (listOf(prefix).filter { it.isNotEmpty() } + groups).joinToString(" ")
    }

    /** The contact whose number matches what's typed (last 9 digits), or null. */
    fun keypadMatch(typed: String, book: List<Pair<String, String>>): String? {
        if (typed.count { it.isDigit() } < 9) return null
        return book.firstOrNull { (_, number) -> CallScreen.sameNumber(number, typed) }?.first
    }
}
