package pl.perier.sirion.logic

import java.io.ByteArrayOutputStream

/**
 * Just enough OBEX to pull the phonebook from a phone (PBAP): connect to the
 * phonebook service, GET one object in chunks, disconnect. The unit's own
 * Bluetooth app pulls names and numbers only; this asks for photos too.
 */
object Obex {
    const val CONTINUE = 0x90
    const val SUCCESS = 0xA0

    /** The whole phonebook as one vCard file. */
    const val PHONEBOOK = "telecom/pb.vcf"
    /** The phone's favourites (PBAP 1.2), shared when the driver allows it on the iPhone. */
    const val FAVOURITES = "telecom/fav.vcf"
    const val PHONEBOOK_TYPE = "x-bt/phonebook"

    private const val OP_CONNECT = 0x80
    private const val OP_DISCONNECT = 0x81
    private const val OP_GET_FINAL = 0x83
    private const val HI_NAME = 0x01
    private const val HI_TYPE = 0x42
    private const val HI_TARGET = 0x46
    private const val HI_BODY = 0x48
    private const val HI_END_OF_BODY = 0x49
    private const val HI_APP_PARAMS = 0x4C
    private const val HI_CONNECTION_ID = 0xCB
    private const val MAX_PACKET = 0xFFFE

    private val PBAP_TARGET = byteArrayOf(
        0x79, 0x61, 0x35, 0xF0.toByte(), 0xF0.toByte(), 0xC5.toByte(), 0x11, 0xD8.toByte(),
        0x09, 0x66, 0x08, 0x00, 0x20, 0x0C, 0x9A.toByte(), 0x66,
    )

    data class Response(val code: Int, val connectionId: Int?, val body: ByteArray)

    fun connect(): ByteArray = packet(OP_CONNECT, byteArrayOf(0x10, 0x00, (MAX_PACKET shr 8).toByte(), MAX_PACKET.toByte()) + bytesHeader(HI_TARGET, PBAP_TARGET))

    /** The first GET names the object; the ones after it only ask for the next chunk. */
    fun get(connectionId: Int?, name: String? = null, type: String? = null, appParams: ByteArray? = null): ByteArray {
        val headers = ByteArrayOutputStream()
        connectionId?.let { headers.write(connectionHeader(it)) }
        name?.let { headers.write(bytesHeader(HI_NAME, it.toByteArray(Charsets.UTF_16BE) + byteArrayOf(0, 0))) }
        type?.let { headers.write(bytesHeader(HI_TYPE, it.toByteArray(Charsets.US_ASCII) + byteArrayOf(0))) }
        appParams?.let { headers.write(bytesHeader(HI_APP_PARAMS, it)) }
        return packet(OP_GET_FINAL, headers.toByteArray())
    }

    fun disconnect(connectionId: Int?): ByteArray = packet(OP_DISCONNECT, connectionId?.let(::connectionHeader) ?: ByteArray(0))

    /** Names, numbers and (unless [photos] is false) photos, as vCard 3.0. */
    fun phonebookParams(photos: Boolean = true): ByteArray {
        val filter = FILTER_VERSION or FILTER_FN or FILTER_N or FILTER_TEL or (if (photos) FILTER_PHOTO else 0L)
        return byteArrayOf(0x06, 0x08) + ByteArray(8) { (filter shr (8 * (7 - it))).toByte() } + byteArrayOf(0x07, 0x01, 0x01)
    }

    /** [connect]: a CONNECT reply has 4 more bytes (version, flags, max size) before its headers. */
    fun parse(packet: ByteArray, connect: Boolean = false): Response {
        val code = packet[0].toInt() and 0xFF
        var connectionId: Int? = null
        val body = ByteArrayOutputStream()
        var i = if (connect) 7 else 3
        while (i < packet.size) {
            val hi = packet[i].toInt() and 0xFF
            when (hi shr 6) {
                0, 1 -> {
                    val length = u16(packet, i + 1)
                    if (hi == HI_BODY || hi == HI_END_OF_BODY) body.write(packet, i + 3, length - 3)
                    i += length
                }
                2 -> i += 2
                else -> {
                    if (hi == HI_CONNECTION_ID) connectionId = u32(packet, i + 1)
                    i += 5
                }
            }
        }
        return Response(code, connectionId, body.toByteArray())
    }

    /** Total packet length from the 3-byte packet start. */
    fun length(start: ByteArray) = u16(start, 1)

    private fun packet(op: Int, rest: ByteArray): ByteArray {
        val size = 3 + rest.size
        return byteArrayOf(op.toByte(), (size shr 8).toByte(), size.toByte()) + rest
    }

    private fun bytesHeader(hi: Int, value: ByteArray): ByteArray {
        val size = 3 + value.size
        return byteArrayOf(hi.toByte(), (size shr 8).toByte(), size.toByte()) + value
    }

    private fun connectionHeader(id: Int) =
        byteArrayOf(HI_CONNECTION_ID.toByte(), (id shr 24).toByte(), (id shr 16).toByte(), (id shr 8).toByte(), id.toByte())

    private fun u16(b: ByteArray, at: Int) = ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)

    private fun u32(b: ByteArray, at: Int) = (u16(b, at) shl 16) or u16(b, at + 2)

    private const val FILTER_VERSION = 1L
    private const val FILTER_FN = 2L
    private const val FILTER_N = 4L
    private const val FILTER_PHOTO = 8L
    private const val FILTER_TEL = 128L
}
