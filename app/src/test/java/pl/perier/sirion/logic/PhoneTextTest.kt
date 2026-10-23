package pl.perier.sirion.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class PhoneTextTest {
    private val now = LocalDateTime.of(2026, 10, 22, 21, 48) // a Thursday

    @Test
    fun initialsUseTheFirstTwoWords() {
        assertEquals("AK", PhoneText.initials("Ania Kowalska"))
        assertEquals("A", PhoneText.initials("Ania"))
        assertEquals("WS", PhoneText.initials("Warsztat Sirion Auto"))
    }

    @Test
    fun numbersWithoutANameShowTheirStart() {
        assertEquals("+48", PhoneText.initials("+48 512 300 118"))
    }

    @Test
    fun readsTheBluetoothCallTimeFormat() {
        assertEquals(LocalDateTime.of(2026, 10, 22, 14, 2, 0), PhoneText.parseCallTime("20261022T140200"))
        assertNull(PhoneText.parseCallTime(""))
        assertNull(PhoneText.parseCallTime("garbage"))
    }

    @Test
    fun whenReadsLikeAPersonWouldSayIt() {
        assertEquals("today 14:02", PhoneText.whenText(LocalDateTime.of(2026, 10, 22, 14, 2), now))
        assertEquals("yesterday", PhoneText.whenText(LocalDateTime.of(2026, 10, 21, 18, 10), now))
        assertEquals("Monday", PhoneText.whenText(LocalDateTime.of(2026, 10, 19, 9, 0), now))
        assertEquals("12 Oct", PhoneText.whenText(LocalDateTime.of(2026, 10, 12, 9, 0), now))
    }

    @Test
    fun recentLineJoinsTypeAndWhenWithOneDot() {
        assertEquals("Missed · today 14:02", PhoneText.recentLine("missed", LocalDateTime.of(2026, 10, 22, 14, 2), now))
        assertEquals("Outgoing · yesterday", PhoneText.recentLine("outgoing", LocalDateTime.of(2026, 10, 21, 18, 10), now))
        assertEquals("Incoming", PhoneText.recentLine("incoming", null, now))
    }

    @Test
    fun missedCallsTodayNameTheLatestCaller() {
        val now = java.time.LocalDateTime.of(2026, 10, 22, 21, 48)
        val calls = listOf(
            PhoneText.Call("missed", now.withHour(20), "Ania"),
            PhoneText.Call("incoming", now.withHour(19), "Tomek"),
            PhoneText.Call("missed", now.withHour(9), "Mama"),
            PhoneText.Call("missed", now.minusDays(1), "Old"),
        )
        assertEquals("2 missed calls · Ania", PhoneText.missedToday(calls, now))
        assertEquals("1 missed call · Mama", PhoneText.missedToday(calls.drop(1), now))
        assertEquals(null, PhoneText.missedToday(calls.drop(3), now))
    }

    @Test
    fun typedNumbersGroupInThrees() {
        assertEquals("512 300 1", PhoneText.groupDigits("5123001"))
        assertEquals("+48 512 300 118", PhoneText.groupDigits("+48512300118"))
        assertEquals("", PhoneText.groupDigits(""))
        assertEquals("*21#", PhoneText.groupDigits("*21#"))
    }

    @Test
    fun keypadLineNamesTheContactOrSaysThereIsNone() {
        val book = listOf("Ania" to "+48 601 234 567", "Mama" to "602345678")
        assertEquals("Ania", PhoneText.keypadMatch("601234567", book))
        assertEquals("Mama", PhoneText.keypadMatch("+48602345678", book))
        assertEquals(null, PhoneText.keypadMatch("5123001", book))
        assertEquals(null, PhoneText.keypadMatch("", book))
    }

    @Test
    fun initialsSkipEmojiAndNeverCutOneInHalf() {
        assertEquals("A", PhoneText.initials("Ania \uD83C\uDF38"))            // Ania 🌸
        assertEquals("T", PhoneText.initials("\uD83D\uDE97 Tomek"))           // 🚗 Tomek
        assertEquals("MT", PhoneText.initials("Mama \u2764\uFE0F Tata"))      // Mama ❤️ Tata
        assertEquals("ŁŻ", PhoneText.initials("łukasz żak"))
        assertEquals("", PhoneText.initials("\uD83D\uDE00"))                 // only an emoji: no letters to show
    }
}
