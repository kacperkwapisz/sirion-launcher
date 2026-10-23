package pl.perier.sirion.logic

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher

/**
 * Just enough of the ADB wire protocol to open one shell on the unit's own
 * adbd (localhost:5555): connect, prove our key, run a command. The unit's
 * `su` only works for the shell user, so this is how the launcher reaches
 * root without touching /system.
 */
object AdbProtocol {
    const val CNXN = 0x4e584e43
    const val AUTH = 0x48545541
    const val OPEN = 0x4e45504f
    const val OKAY = 0x59414b4f
    const val CLSE = 0x45534c43
    const val WRTE = 0x45545257

    const val VERSION = 0x01000000
    const val MAX_DATA = 4096
    const val AUTH_TOKEN = 1
    const val AUTH_SIGNATURE = 2

    /** ASN.1 DigestInfo for SHA-1; adbd treats the 20-byte token as the digest. */
    val SHA1_DIGEST_INFO = byteArrayOf(
        0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14,
    )

    class Message(val command: Int, val arg0: Int, val arg1: Int, val payload: ByteArray = ByteArray(0))

    data class Header(val command: Int, val arg0: Int, val arg1: Int, val length: Int, val checksum: Int, val magic: Int)

    fun encode(m: Message): ByteArray = ByteBuffer.allocate(24 + m.payload.size).order(ByteOrder.LITTLE_ENDIAN).apply {
        putInt(m.command)
        putInt(m.arg0)
        putInt(m.arg1)
        putInt(m.payload.size)
        putInt(m.payload.sumOf { it.toInt() and 0xff })
        putInt(m.command.inv())
        put(m.payload)
    }.array()

    fun readHeader(bytes: ByteArray): Header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).run {
        Header(int, int, int, int, int, int)
    }

    fun connect() = Message(CNXN, VERSION, MAX_DATA, "host::\u0000".toByteArray())

    fun openShell(localId: Int, command: String) = Message(OPEN, localId, 0, "shell:$command\u0000".toByteArray())

    fun signature(token: ByteArray, key: PrivateKey) = Message(AUTH, AUTH_SIGNATURE, 0, sign(token, key))

    /** RSA PKCS#1 v1.5 over DigestInfo(SHA-1) with the token as the digest, as adb's RSA_sign does. */
    fun sign(token: ByteArray, key: PrivateKey): ByteArray =
        Cipher.getInstance("RSA/ECB/PKCS1Padding").apply { init(Cipher.ENCRYPT_MODE, key) }.doFinal(SHA1_DIGEST_INFO + token)

    /** The private half of an `adb keygen` key (PEM, PKCS#8). */
    fun privateKey(pem: String): PrivateKey {
        val body = pem.lineSequence().filterNot { it.startsWith("-----") }.joinToString("")
        return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(body)))
    }
}
