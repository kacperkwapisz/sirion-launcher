package pl.perier.sirion.logic

import java.util.Base64

/** Contacts and their photos out of a phonebook vCard file (2.1 or 3.0). */
object VCard {
    class Photo(val numbers: List<String>, val jpeg: ByteArray)

    /** A contact to call: their name and first number. */
    data class Card(val name: String, val number: String)

    fun photos(text: String): List<Photo> = parse(text).mapNotNull { entry ->
        val jpeg = entry.photo?.let(::decode) ?: return@mapNotNull null
        if (entry.numbers.isEmpty()) null else Photo(entry.numbers, jpeg)
    }

    /** In the phone's order (favourites keep the order set on the iPhone). */
    fun cards(text: String): List<Card> = parse(text).mapNotNull { entry ->
        val number = entry.numbers.firstOrNull() ?: return@mapNotNull null
        Card(entry.name.ifBlank { number }, number)
    }

    private class Entry(var name: String = "", val numbers: MutableList<String> = mutableListOf(), var photo: String? = null)

    private fun parse(text: String): List<Entry> {
        // Unfold: a line starting with a space or tab continues the one before.
        val lines = text.replace("\r\n", "\n").replace(Regex("\n[ \t]"), "").split("\n")
        val entries = mutableListOf<Entry>()
        var entry = Entry()
        var structured = ""
        for (line in lines) {
            val colon = line.indexOf(':')
            val key = (if (colon < 0) line else line.substring(0, colon)).substringBefore(';').uppercase()
            val value = if (colon < 0) "" else line.substring(colon + 1)
            when (key) {
                "BEGIN" -> { entry = Entry(); structured = "" }
                "FN" -> entry.name = value.trim()
                // N is Family;Given;Middle;Prefix;Suffix: used when there's no FN.
                "N" -> structured = value.split(';').let { parts -> listOf(parts.getOrNull(1), parts.getOrNull(0)) }
                    .filterNot { it.isNullOrBlank() }.joinToString(" ")
                "TEL" -> value.trim().takeIf { it.isNotEmpty() }?.let(entry.numbers::add)
                "PHOTO" -> entry.photo = value
                "END" -> {
                    if (entry.name.isBlank()) entry.name = structured
                    entries += entry
                }
            }
        }
        return entries
    }

    private fun decode(base64: String): ByteArray? =
        runCatching { Base64.getMimeDecoder().decode(base64.filterNot(Char::isWhitespace)) }
            .getOrNull()?.takeIf { it.size > 2 }
}
