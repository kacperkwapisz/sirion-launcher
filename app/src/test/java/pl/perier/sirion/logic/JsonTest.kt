package pl.perier.sirion.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JsonTest {
    @Test
    fun readsObjectsArraysStringsNumbersAndLiterals() {
        val value = Json.parse("""{"a": 1, "b": [true, false, null, "x"], "c": {"d": -2.5}}""")
        assertEquals(mapOf("a" to 1L, "b" to listOf(true, false, null, "x"), "c" to mapOf("d" to -2.5)), value)
    }

    @Test
    fun readsEscapes() {
        assertEquals("a\"b\\c\nd ż é", Json.parse("\"a\\\"b\\\\c\\nd ż \\u00e9\""))
    }

    @Test
    fun brokenTextGivesNull() {
        assertNull(Json.parse("""{"a": """))
        assertNull(Json.parse("""{"a" 1}"""))
        assertNull(Json.parse("""[1, 2] trailing"""))
    }

    @Test
    fun writesWhatItReads() {
        val value = mapOf("n" to 3L, "s" to "quote \" and \\ slash", "l" to listOf("x", "y"))
        assertEquals(value, Json.parse(Json.write(value)))
    }
}
