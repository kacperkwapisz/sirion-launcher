package pl.perier.sirion.logic

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Base64

class VCardTest {
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 0xFF.toByte(), 0xD9.toByte())

    private fun folded(base64: String) = base64.chunked(8).joinToString("\r\n ")

    @Test
    fun readsAPhotoFoldedOverSeveralLinesWithAllItsNumbers() {
        val text = "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Tata\r\nTEL;TYPE=CELL:+49 151 00000001\r\nTEL:22499\r\n" +
            "PHOTO;ENCODING=b;TYPE=JPEG:" + folded(Base64.getEncoder().encodeToString(jpeg)) + "\r\nEND:VCARD\r\n"
        val photos = VCard.photos(text)
        assertEquals(1, photos.size)
        assertEquals(listOf("+49 151 00000001", "22499"), photos[0].numbers)
        assertArrayEquals(jpeg, photos[0].jpeg)
    }

    @Test
    fun skipsContactsWithoutAPhotoOrANumber() {
        val text = "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:No photo\r\nTEL:1\r\nEND:VCARD\r\n" +
            "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:No number\r\nPHOTO;ENCODING=b:" + Base64.getEncoder().encodeToString(jpeg) + "\r\nEND:VCARD\r\n"
        assertEquals(emptyList<VCard.Photo>(), VCard.photos(text))
    }

    @Test
    fun readsVcard21BasePhotosEndingWithABlankLine() {
        val text = "BEGIN:VCARD\nVERSION:2.1\nTEL;CELL:500100200\nPHOTO;ENCODING=BASE64;JPEG:\n " +
            Base64.getEncoder().encodeToString(jpeg) + "\n\nEND:VCARD\n"
        val photos = VCard.photos(text)
        assertEquals(listOf("500100200"), photos.single().numbers)
        assertArrayEquals(jpeg, photos.single().jpeg)
    }

    @Test
    fun brokenBase64IsSkipped() {
        val text = "BEGIN:VCARD\nTEL:1\nPHOTO;ENCODING=b:@@@\nEND:VCARD\n"
        assertEquals(emptyList<VCard.Photo>(), VCard.photos(text))
    }

    @Test
    fun numbersMatchByTheirLastNineDigits() {
        assertEquals("500100200", PhoneText.numberKey("+48 500 100 200"))
        assertEquals("500100200", PhoneText.numberKey("500100200"))
        assertEquals("22499", PhoneText.numberKey("22499"))
        assertEquals("", PhoneText.numberKey("Unknown"))
    }

    @Test
    fun readsFavouritesWithTheirNameAndFirstNumberInTheirOrder() {
        val text = "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Tata\r\nN:;Tata;;;\r\nTEL;TYPE=CELL:+4915100000001\r\nTEL:22499\r\nEND:VCARD\r\n" +
            "BEGIN:VCARD\r\nVERSION:3.0\r\nN:Nowak;Anna;;;\r\nTEL:500100200\r\nEND:VCARD\r\n" +
            "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:No number\r\nEND:VCARD\r\n"
        assertEquals(
            listOf(VCard.Card("Tata", "+4915100000001"), VCard.Card("Anna Nowak", "500100200")),
            VCard.cards(text),
        )
    }
}
