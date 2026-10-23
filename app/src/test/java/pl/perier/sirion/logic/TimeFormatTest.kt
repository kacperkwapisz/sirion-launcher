package pl.perier.sirion.logic

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class TimeFormatTest {
    @Test
    fun clockIs24HourWithPaddedMinutes() {
        assertEquals("21:48", TimeFormat.clock(LocalDateTime.of(2026, 10, 22, 21, 48)))
        assertEquals("07:05", TimeFormat.clock(LocalDateTime.of(2026, 10, 22, 7, 5)))
    }

    @Test
    fun dateIsShortDayDayMonth() {
        assertEquals("Thu 22 Oct", TimeFormat.date(LocalDateTime.of(2026, 10, 22, 21, 48)))
        assertEquals("Sat 3 Jan", TimeFormat.date(LocalDateTime.of(2026, 1, 3, 9, 0)))
    }

    @Test
    fun elapsedUsesMinutesAndSeconds() {
        assertEquals("0:00", TimeFormat.elapsed(0))
        assertEquals("1:39", TimeFormat.elapsed(99_000))
        assertEquals("1:02:03", TimeFormat.elapsed(3_723_000))
    }

    @Test
    fun remainingUsesTrueMinusSign() {
        assertEquals("−2:24", TimeFormat.remaining(positionMs = 99_000, durationMs = 243_000))
    }

    @Test
    fun remainingNeverGoesBelowZero() {
        assertEquals("−0:00", TimeFormat.remaining(positionMs = 300_000, durationMs = 243_000))
    }

    @Test
    fun callTimerIsPaddedLikeAClock() {
        assertEquals("04:12", TimeFormat.callTimer(252_000))
        assertEquals("00:02", TimeFormat.callTimer(2_400))
        assertEquals("1:02:05", TimeFormat.callTimer(3_725_000))
    }

    @Test
    fun twelveHourClockHasNoLeadingZeroAndNoAmPmLabel() {
        assertEquals("9:48", TimeFormat.clock(java.time.LocalDateTime.of(2026, 10, 22, 21, 48), h24 = false))
        assertEquals("12:05", TimeFormat.clock(java.time.LocalDateTime.of(2026, 10, 22, 0, 5), h24 = false))
        assertEquals("12:30", TimeFormat.clock(java.time.LocalDateTime.of(2026, 10, 22, 12, 30), h24 = false))
    }
}
