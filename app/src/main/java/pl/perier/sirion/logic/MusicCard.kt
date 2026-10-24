package pl.perier.sirion.logic

data class Track(val title: String, val artist: String, val durationMs: Long)

/** What the music hero card shows. */
sealed class MusicCard {
    /** CarPlay owns the audio and we don't know its song (ZLink, or DiPlay before a song starts): the card hands over. */
    object InCarPlay : MusicCard()

    object NothingPlaying : MusicCard()

    data class Playing(
        val track: Track,
        val playing: Boolean,
        val elapsed: String,
        val remaining: String,
        val progress: Float,
        /** The song comes from CarPlay (through DiPlay), not Bluetooth. */
        val fromCarPlay: Boolean = false,
    ) : MusicCard()

    companion object {
        /** [fromCarPlay]: [track] is CarPlay's own song, which DiPlay shares; ZLink shares none. */
        fun from(carPlayConnected: Boolean, track: Track?, playing: Boolean, positionMs: Long, fromCarPlay: Boolean = false): MusicCard {
            // While CarPlay runs, a Bluetooth track is stale: the iPhone plays through CarPlay.
            if (carPlayConnected && !fromCarPlay) return InCarPlay
            if (track == null || track.title.isBlank()) return if (carPlayConnected) InCarPlay else NothingPlaying
            val known = track.durationMs > 0
            return Playing(
                track = track,
                playing = playing,
                elapsed = TimeFormat.elapsed(positionMs),
                remaining = if (known) TimeFormat.remaining(positionMs, track.durationMs) else "",
                progress = if (known) (positionMs.toFloat() / track.durationMs).coerceIn(0f, 1f) else 0f,
                fromCarPlay = fromCarPlay,
            )
        }
    }
}
