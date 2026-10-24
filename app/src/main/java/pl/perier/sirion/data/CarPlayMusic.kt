package pl.perier.sirion.data

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import pl.perier.sirion.data.MusicSource.Mode
import pl.perier.sirion.logic.Track

/**
 * CarPlay's own song, with its album art, from DiPlay. DiPlay keeps a media
 * session ("DiPlay CarPlay") for as long as CarPlay runs, and sends play,
 * pause, next and previous on to the iPhone. Reading another app's session
 * needs notification access, which the root helper grants ([SessionAccess]).
 * ZLink shares nothing like this.
 */
class CarPlayMusic(private val context: Context, private val onChange: () -> Unit) : MusicSource {
    private val sessions = context.getSystemService(MediaSessionManager::class.java)
    private val listener = ComponentName(context, SessionAccess::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var controller: MediaController? = null
    private var placeholder: Bitmap? = null

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = onChange()
        override fun onPlaybackStateChanged(state: PlaybackState?) = onChange()
        override fun onSessionDestroyed() = pick(emptyList())
    }

    private val changed = MediaSessionManager.OnActiveSessionsChangedListener { list -> pick(list.orEmpty()) }

    /** DiPlay has a CarPlay session right now. */
    val active: Boolean get() = controller != null

    private var started = false
    private val retry = Runnable { listen() }

    override fun start() {
        started = true
        listen()
    }

    /** The helper grants notification access a moment after the launcher starts: until then, try again. */
    private fun listen() {
        if (!started) return
        val ok = runCatching {
            sessions.addOnActiveSessionsChangedListener(changed, listener, main)
            pick(sessions.getActiveSessions(listener))
        }.isSuccess
        if (!ok) main.postDelayed(retry, RETRY_MS)
    }

    override fun stop() {
        started = false
        main.removeCallbacks(retry)
        runCatching { sessions.removeOnActiveSessionsChangedListener(changed) }
        controller?.unregisterCallback(callback)
        controller = null
    }

    private fun pick(list: List<MediaController>) {
        val next = list.firstOrNull { it.packageName == DIPLAY }
        if (next?.sessionToken == controller?.sessionToken) return
        controller?.unregisterCallback(callback)
        controller = next?.also { it.registerCallback(callback, main) }
        onChange()
    }

    override val track: Track?
        get() {
            val metadata = controller?.metadata ?: return null
            return Track(
                title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
                artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
                durationMs = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION),
            )
        }

    override val album: String get() = controller?.metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty()

    /** DiPlay sends its own stand-in picture until the iPhone sends the album art; that one doesn't count. */
    override val artwork: Bitmap?
        get() {
            val art = controller?.metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: return null
            return art.takeUnless { isPlaceholder(it) }
        }

    override val playing: Boolean get() = controller?.playbackState?.state == PlaybackState.STATE_PLAYING

    /** The iPhone sends the position on play, pause or seek; DiPlay stamps it, so it runs on from there. */
    override val positionMs: Long
        get() {
            val state = controller?.playbackState ?: return 0
            if (state.position < 0) return 0
            if (state.state != PlaybackState.STATE_PLAYING) return state.position
            val since = SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
            return state.position + (since * state.playbackSpeed).toLong()
        }

    /** CarPlay takes no seek from the car. */
    override val canSeek: Boolean get() = false
    override val shuffle: Mode get() = Mode.UNAVAILABLE
    override val repeat: Mode get() = Mode.UNAVAILABLE

    override fun play() { controller?.transportControls?.play() }

    override fun playPause() {
        val controls = controller?.transportControls ?: return
        if (playing) controls.pause() else controls.play()
    }

    override fun next() { controller?.transportControls?.skipToNext() }
    override fun previous() { controller?.transportControls?.skipToPrevious() }
    override fun seekTo(ms: Long) = Unit
    override fun toggleShuffle() = Unit
    override fun toggleRepeat() = Unit

    private fun isPlaceholder(art: Bitmap): Boolean {
        val stand = placeholder ?: diPlayPlaceholder(art.width, art.height)?.also { placeholder = it } ?: return false
        return stand.width == art.width && stand.height == art.height && stand.sameAs(art.copy(stand.config ?: Bitmap.Config.ARGB_8888, false))
    }

    /** DiPlay's own stand-in, drawn the way DiPlay draws it, to compare against. */
    private fun diPlayPlaceholder(width: Int, height: Int): Bitmap? = runCatching {
        val resources = context.packageManager.getResourcesForApplication(DIPLAY)
        val id = resources.getIdentifier("art_now_playing_placeholder", "drawable", DIPLAY)
        if (id == 0) return null
        val drawable = resources.getDrawable(id, null)
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            drawable.setBounds(0, 0, width, height)
            drawable.draw(Canvas(bitmap))
        }
    }.getOrNull()

    companion object {
        const val DIPLAY = "com.shihab.diplay"
        private const val RETRY_MS = 5_000L
    }
}
