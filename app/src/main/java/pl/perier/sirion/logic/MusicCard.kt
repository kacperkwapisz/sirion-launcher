package pl.perier.sirion.logic

data class Track(val title: String, val artist: String, val durationMs: Long)

/** What the music hero card shows. */
sealed class MusicCard {
    /** CarPlay owns the audio while it runs, so the card hands over. */
    object InCarPlay : MusicCard()

    object NothingPlaying : MusicCard()

    data class Playing(
        val track: Track,
        val playing: Boolean,
        val elapsed: String,
        val remaining: String,
        val progress: Float,
    ) : MusicCard()

    companion object {
        fun from(carPlayConnected: Boolean, track: Track?, playing: Boolean, positionMs: Long): MusicCard {
            if (carPlayConnected) return InCarPlay
            if (track == null || track.title.isBlank()) return NothingPlaying
            val known = track.durationMs > 0
            return Playing(
                track = track,
                playing = playing,
                elapsed = TimeFormat.elapsed(positionMs),
                remaining = if (known) TimeFormat.remaining(positionMs, track.durationMs) else "",
                progress = if (known) (positionMs.toFloat() / track.durationMs).coerceIn(0f, 1f) else 0f,
            )
        }
    }
}
