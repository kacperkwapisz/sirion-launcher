package pl.perier.sirion.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicCardTest {
    private val track = Track(title = "Midnight City", artist = "M83", durationMs = 243_000)

    @Test
    fun carPlayTakesOverTheCard() {
        // While CarPlay runs, the iPhone sends audio through it, so a stale
        // Bluetooth track must never show as playing.
        val state = MusicCard.from(carPlayConnected = true, track = track, playing = true, positionMs = 99_000)
        assertEquals(MusicCard.InCarPlay, state)
    }

    @Test
    fun playingTrackShowsTimes() {
        val state = MusicCard.from(carPlayConnected = false, track = track, playing = true, positionMs = 99_000)
        assertEquals(MusicCard.Playing(track, playing = true, elapsed = "1:39", remaining = "−2:24", progress = 99f / 243f), state)
    }

    @Test
    fun pausedTrackStaysOnTheCard() {
        val state = MusicCard.from(carPlayConnected = false, track = track, playing = false, positionMs = 0)
        assertEquals(false, (state as MusicCard.Playing).playing)
    }

    @Test
    fun unknownDurationHidesTheScrubberProgress() {
        val noLength = track.copy(durationMs = 0)
        val state = MusicCard.from(carPlayConnected = false, track = noLength, playing = true, positionMs = 5_000)
        assertEquals(0f, (state as MusicCard.Playing).progress)
        assertEquals("", state.remaining)
    }

    @Test
    fun nothingPlayingWhenThereIsNoTitle() {
        val empty = Track(title = "", artist = "", durationMs = 0)
        assertEquals(MusicCard.NothingPlaying, MusicCard.from(carPlayConnected = false, track = empty, playing = false, positionMs = 0))
        assertEquals(MusicCard.NothingPlaying, MusicCard.from(carPlayConnected = false, track = null, playing = false, positionMs = 0))
    }

    @Test
    fun aCarPlaySongFromDiPlayShowsLikeAnyOtherSong() {
        // DiPlay hands over CarPlay's song, so the card can show it instead of handing over.
        val state = MusicCard.from(carPlayConnected = true, track = track, playing = true, positionMs = 99_000, fromCarPlay = true)
        assertEquals(MusicCard.Playing(track, playing = true, elapsed = "1:39", remaining = "−2:24", progress = 99f / 243f, fromCarPlay = true), state)
    }

    @Test
    fun carPlayWithNoSongYetStillHandsOver() {
        val empty = Track(title = "", artist = "", durationMs = 0)
        assertEquals(MusicCard.InCarPlay, MusicCard.from(carPlayConnected = true, track = empty, playing = false, positionMs = 0, fromCarPlay = true))
    }
}
