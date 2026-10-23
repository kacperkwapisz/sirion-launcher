package pl.perier.sirion.logic

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.KeyPairGenerator
import javax.crypto.Cipher

class AdbProtocolTest {
    @Test
    fun messagesRoundTripWithChecksumAndMagic() {
        val payload = "host::\u0000".toByteArray()
        val bytes = AdbProtocol.encode(AdbProtocol.Message(AdbProtocol.CNXN, AdbProtocol.VERSION, AdbProtocol.MAX_DATA, payload))
        assertEquals(24 + payload.size, bytes.size)
        // Little-endian command, then the magic is its complement.
        assertEquals(0x43, bytes[0].toInt() and 0xff)
        val header = AdbProtocol.readHeader(bytes.copyOfRange(0, 24))
        assertEquals(AdbProtocol.CNXN, header.command)
        assertEquals(payload.size, header.length)
        assertEquals(payload.sumOf { it.toInt() and 0xff }, header.checksum)
        assertEquals(AdbProtocol.CNXN.inv(), header.magic)
    }

    @Test
    fun shellOpenAsksForTheCommand() {
        val open = AdbProtocol.openShell(localId = 1, command = "id")
        assertEquals(AdbProtocol.OPEN, open.command)
        assertEquals(1, open.arg0)
        assertEquals("shell:id\u0000", String(open.payload))
    }

    @Test
    fun tokenIsSignedAsAnSha1DigestLikeAdbDoes() {
        val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val token = ByteArray(20) { it.toByte() }
        val signature = AdbProtocol.sign(token, keys.private)
        // Decrypting with the public key gives the SHA-1 DigestInfo prefix and the token.
        val plain = Cipher.getInstance("RSA/ECB/PKCS1Padding").apply { init(Cipher.DECRYPT_MODE, keys.public) }.doFinal(signature)
        assertArrayEquals(AdbProtocol.SHA1_DIGEST_INFO + token, plain)
    }

    @Test
    fun pemPrivateKeyFromAdbKeygenLoads() {
        val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val pem = "-----BEGIN PRIVATE KEY-----\n" +
            java.util.Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(keys.private.encoded) +
            "\n-----END PRIVATE KEY-----\n"
        assertArrayEquals(keys.private.encoded, AdbProtocol.privateKey(pem).encoded)
    }
}
