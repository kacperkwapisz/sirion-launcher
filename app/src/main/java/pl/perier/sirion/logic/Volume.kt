package pl.perier.sirion.logic

object Volume {
    /** Unmuting with no level to go back to starts quiet, not loud. */
    const val UNMUTE_DEFAULT = 6

    data class Mute(val volume: Int, val remembered: Int)

    fun step(current: Int, delta: Int, max: Int): Int = (current + delta).coerceIn(0, max)

    /** − or + while muted unmutes and carries on from the level before the mute. */
    fun stepFrom(current: Int, remembered: Int, delta: Int, max: Int): Int =
        step(if (current == 0 && remembered > 0) remembered else current, delta, max)

    /**
     * Mute goes to 0 and remembers the level; unmute brings it back. Done by
     * hand because the unit's audio ignores Android's own mute toggle.
     */
    fun toggleMute(current: Int, remembered: Int, max: Int): Mute =
        if (current > 0) Mute(volume = 0, remembered = current)
        else Mute(volume = (if (remembered > 0) remembered else UNMUTE_DEFAULT).coerceAtMost(max), remembered = remembered)
}
