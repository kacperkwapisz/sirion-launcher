package pl.perier.sirion.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class SettingsTextTest {
    private val now = LocalDateTime.of(2026, 10, 22, 21, 48)

    @Test fun `last connected reads like the phone list`() {
        assertEquals("Last connected today 14:02", SettingsText.lastConnected(now.withHour(14).withMinute(2), now))
        assertEquals("Last connected Tuesday", SettingsText.lastConnected(LocalDateTime.of(2026, 10, 20, 9, 0), now))
        assertEquals("Paired", SettingsText.lastConnected(null, now))
    }

    @Test fun `levels show as percent`() {
        assertEquals("100%", SettingsText.percent(255))
        assertEquals("82%", SettingsText.percent(210))
        assertEquals("12%", SettingsText.percent(30))
    }

    @Test fun `saved network count`() {
        assertEquals("No saved networks", SettingsText.savedNetworks(0))
        assertEquals("1 saved network", SettingsText.savedNetworks(1))
        assertEquals("2 saved networks", SettingsText.savedNetworks(2))
    }

    @Test fun `ssid loses its quotes and unknown means none`() {
        assertEquals("Home Wi-Fi", SettingsText.ssid("\"Home Wi-Fi\""))
        assertNull(SettingsText.ssid("<unknown ssid>"))
        assertNull(SettingsText.ssid(""))
        assertNull(SettingsText.ssid(null))
    }

    @Test fun `sunrise and sunset in Szczecin in late October`() {
        val (rise, set) = Sun.today(LocalDate.of(2026, 10, 22), ZoneId.of("Europe/Warsaw"), 53.452, 14.212)
        assertTrue("sunrise $rise", rise!! in LocalTime.of(7, 35)..LocalTime.of(7, 55))
        assertTrue("sunset $set", set!! in LocalTime.of(17, 35)..LocalTime.of(17, 55))
    }

    @Test fun `pairing code is six digits split in two`() {
        assertEquals("482 913", SettingsText.pairingCode(482913))
        assertEquals("004 207", SettingsText.pairingCode(4207))
    }
}
