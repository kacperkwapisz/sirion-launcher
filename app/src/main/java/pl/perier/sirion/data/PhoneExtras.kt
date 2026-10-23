package pl.perier.sirion.data

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.util.Log
import android.util.LruCache
import pl.perier.sirion.logic.Obex
import pl.perier.sirion.logic.PhoneText
import pl.perier.sirion.logic.VCard
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.concurrent.thread

/**
 * What the unit's own phonebook sync leaves out, pulled from the phone
 * ourselves over Bluetooth (PBAP) in one session: contact photos, kept as
 * round PNGs named by number so any screen with a number finds its photo,
 * and the phone's favourites, when the driver shares them on the iPhone.
 */
class PhoneExtras(context: Context) {
    private val dir = File(context.filesDir, "photos")
    private val book = PhoneBook(context)
    private val cache = LruCache<String, Bitmap>(CACHE_SIZE)
    @Volatile private var saved: Set<String> = names(dir)
    @Volatile private var running = false

    /** The round photo for a number, or null. Main thread. */
    fun photo(number: String): Bitmap? {
        val key = PhoneText.numberKey(number)
        if (key.isEmpty() || key !in saved) return null
        return cache.get(key) ?: BitmapFactory.decodeFile(File(dir, "$key.png").path)?.also { cache.put(key, it) }
    }

    /** Downloads photos and favourites and replaces the saved ones. Returns at once. */
    fun download(device: BluetoothDevice, done: (Boolean) -> Unit = {}) {
        if (running) return
        running = true
        thread(name = "phone-extras", isDaemon = true) {
            val ok = runCatching {
                val (phonebook, favourites) = pull(device)
                save(VCard.photos(String(phonebook, Charsets.UTF_8)))
                // Null: the phone doesn't share favourites, so the ones kept in the car stay.
                val cards = favourites?.let { VCard.cards(String(it, Charsets.UTF_8)) }
                book.savePhoneFavourites(cards?.map { Contact(it.name, it.number) })
                Log.i(TAG, "Favourites from the phone: ${cards?.size ?: "not shared"}")
            }.onFailure { Log.w(TAG, "Phone extras download failed", it) }.isSuccess
            running = false
            done(ok)
        }
    }

    /** The phonebook (with photos) and the favourites, or null favourites when the phone has none to share. */
    private fun pull(device: BluetoothDevice): Pair<ByteArray, ByteArray?> {
        BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery()
        val socket = device.createRfcommSocketToServiceRecord(PBAP_SERVER)
        // A phone that stops answering mustn't hold the socket forever.
        val watchdog = thread(isDaemon = true) {
            runCatching { Thread.sleep(TIMEOUT_MS); socket.close() }
        }
        try {
            socket.connect()
            val out = socket.outputStream
            val input = socket.inputStream
            out.write(Obex.connect())
            val connected = Obex.parse(read(input), connect = true)
            check(connected.code == Obex.SUCCESS) { "Phone refused the phonebook (${connected.code})" }
            val id = connected.connectionId
            val phonebook = checkNotNull(get(input, out, id, Obex.PHONEBOOK, Obex.phonebookParams())) { "Phone refused the phonebook" }
            Log.i(TAG, "Phonebook with photos: ${phonebook.size} bytes")
            // Photos already came with the phonebook; favourites only need names and numbers.
            val favourites = get(input, out, id, Obex.FAVOURITES, Obex.phonebookParams(photos = false))
            runCatching { out.write(Obex.disconnect(id)); read(input) }
            return phonebook to favourites
        } finally {
            watchdog.interrupt()
            close(socket)
        }
    }

    /** One phonebook object, chunk by chunk, or null when the phone says it has none. */
    private fun get(input: InputStream, out: OutputStream, id: Int?, name: String, params: ByteArray): ByteArray? {
        val body = ByteArrayOutputStream()
        out.write(Obex.get(id, name, Obex.PHONEBOOK_TYPE, params))
        while (true) {
            val part = Obex.parse(read(input))
            body.write(part.body)
            when (part.code) {
                Obex.CONTINUE -> out.write(Obex.get(id))
                Obex.SUCCESS -> return body.toByteArray()
                else -> {
                    Log.i(TAG, "$name: phone answered ${Integer.toHexString(part.code)}")
                    return null
                }
            }
        }
    }

    private fun read(input: InputStream): ByteArray {
        val start = readFully(input, 3)
        return start + readFully(input, Obex.length(start) - 3)
    }

    private fun readFully(input: InputStream, count: Int): ByteArray {
        val buffer = ByteArray(count)
        var at = 0
        while (at < count) {
            val n = input.read(buffer, at, count - at)
            check(n >= 0) { "Phone closed the connection" }
            at += n
        }
        return buffer
    }

    private fun save(photos: List<VCard.Photo>) {
        val fresh = File(dir.parentFile, "photos.new").apply { deleteRecursively(); mkdirs() }
        for (photo in photos) {
            val round = round(photo.jpeg) ?: continue
            val keys = photo.numbers.map(PhoneText::numberKey).filter { it.isNotEmpty() }.distinct()
            keys.forEach { key -> File(fresh, "$key.png").outputStream().use { round.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            round.recycle()
        }
        dir.deleteRecursively()
        fresh.renameTo(dir)
        cache.evictAll()
        saved = names(dir)
        Log.i(TAG, "Saved ${photos.size} contact photos")
    }

    /** Centre square, scaled to [SIZE], cut to a circle. */
    private fun round(jpeg: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SIZE) sample *= 2
        val source = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val side = minOf(source.width, source.height)
        val scale = SIZE.toFloat() / side
        val shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(Matrix().apply {
                setTranslate(-(source.width - side) / 2f, -(source.height - side) / 2f)
                postScale(scale, scale)
            })
        }
        val round = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        Canvas(round).drawCircle(SIZE / 2f, SIZE / 2f, SIZE / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader })
        source.recycle()
        return round
    }

    private fun close(socket: BluetoothSocket) = runCatching { socket.close() }

    private companion object {
        const val TAG = "SirionPhoneExtras"
        /** Phonebook Access server on the phone. */
        val PBAP_SERVER: UUID = UUID.fromString("0000112f-0000-1000-8000-00805f9b34fb")
        /** The call screen's avatar, the biggest one. */
        const val SIZE = 104
        const val CACHE_SIZE = 64
        const val TIMEOUT_MS = 180_000L

        fun names(dir: File) = dir.list().orEmpty().map { it.removeSuffix(".png") }.toSet()
    }
}
