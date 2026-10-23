package pl.perier.sirion.logic

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObexTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun connectAsksForThePhonebookService() {
        val target = bytes(0x79, 0x61, 0x35, 0xF0, 0xF0, 0xC5, 0x11, 0xD8, 0x09, 0x66, 0x08, 0x00, 0x20, 0x0C, 0x9A, 0x66)
        assertArrayEquals(
            bytes(0x80, 0x00, 0x1A, 0x10, 0x00, 0xFF, 0xFE, 0x46, 0x00, 0x13) + target,
            Obex.connect(),
        )
    }

    @Test
    fun firstGetCarriesConnectionNameTypeAndParams() {
        val packet = Obex.get(connectionId = 1, name = "a", type = "t", appParams = bytes(0x07, 0x01, 0x01))
        assertArrayEquals(
            bytes(
                0x83, 0x00, 0x1A,
                0xCB, 0x00, 0x00, 0x00, 0x01,
                0x01, 0x00, 0x07, 0x00, 0x61, 0x00, 0x00,
                0x42, 0x00, 0x05, 0x74, 0x00,
                0x4C, 0x00, 0x06, 0x07, 0x01, 0x01,
            ),
            packet,
        )
    }

    @Test
    fun laterGetsCarryOnlyTheConnection() {
        assertArrayEquals(bytes(0x83, 0x00, 0x08, 0xCB, 0x00, 0x00, 0x00, 0x07), Obex.get(connectionId = 7))
    }

    @Test
    fun connectResponseGivesTheConnectionId() {
        val response = Obex.parse(bytes(0xA0, 0x00, 0x0C, 0x10, 0x00, 0x04, 0x00, 0xCB, 0x00, 0x00, 0x00, 0x2A), connect = true)
        assertEquals(Obex.SUCCESS, response.code)
        assertEquals(42, response.connectionId)
    }

    @Test
    fun bodyChunksAreCollectedAndEndOfBodyFinishes() {
        val more = Obex.parse(bytes(0x90, 0x00, 0x08, 0x48, 0x00, 0x05, 0x68, 0x69))
        assertEquals(Obex.CONTINUE, more.code)
        assertArrayEquals("hi".toByteArray(), more.body)
        val last = Obex.parse(bytes(0xA0, 0x00, 0x07, 0x49, 0x00, 0x04, 0x21))
        assertEquals(Obex.SUCCESS, last.code)
        assertArrayEquals("!".toByteArray(), last.body)
        assertNull(last.connectionId)
    }

    @Test
    fun photoRequestAsksForNamesNumbersAndPhotosAsVcard30() {
        val params = Obex.phonebookParams()
        // Filter tag 6: VERSION | FN | N | PHOTO | TEL.
        assertArrayEquals(bytes(0x06, 0x08, 0, 0, 0, 0, 0, 0, 0, 0x8F), params.copyOfRange(0, 10))
        // Format tag 7: vCard 3.0.
        assertArrayEquals(bytes(0x07, 0x01, 0x01), params.copyOfRange(10, 13))
        assertTrue(params.size == 13)
    }

    @Test
    fun favouritesAskForNamesAndNumbersOnly() {
        val params = Obex.phonebookParams(photos = false)
        assertArrayEquals(bytes(0x06, 0x08, 0, 0, 0, 0, 0, 0, 0, 0x87), params.copyOfRange(0, 10))
    }
}
