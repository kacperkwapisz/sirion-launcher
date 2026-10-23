package pl.perier.sirion.logic

/**
 * Small JSON reader and writer for `update.json`: objects become maps,
 * arrays lists, numbers Long or Double. (Android's org.json isn't there in
 * plain JVM unit tests.)
 */
object Json {
    /** The value, or null when the text isn't valid JSON. */
    fun parse(text: String): Any? = runCatching {
        Reader(text).run { value().also { skipSpace(); check(at == text.length) } }
    }.getOrNull()

    fun write(value: Any?): String = when (value) {
        null -> "null"
        is String -> quote(value)
        is Number, is Boolean -> value.toString()
        is Map<*, *> -> value.entries.joinToString(", ", "{", "}") { quote(it.key.toString()) + ": " + write(it.value) }
        is List<*> -> value.joinToString(", ", "[", "]") { write(it) }
        else -> error("Can't write ${value::class}")
    }

    private fun quote(s: String) = buildString {
        append('"')
        for (c in s) when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
        }
        append('"')
    }

    private class Reader(val text: String) {
        var at = 0

        fun value(): Any? {
            skipSpace()
            return when (val c = text[at]) {
                '{' -> obj()
                '[' -> list()
                '"' -> string()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) number() else error("Unexpected $c")
            }
        }

        private fun obj(): Map<String, Any?> {
            val map = LinkedHashMap<String, Any?>()
            at++
            skipSpace()
            if (text[at] == '}') { at++; return map }
            while (true) {
                skipSpace()
                val key = string()
                skipSpace()
                expect(':')
                map[key] = value()
                skipSpace()
                if (text[at++] == '}') return map
                check(text[at - 1] == ',')
            }
        }

        private fun list(): List<Any?> {
            val list = mutableListOf<Any?>()
            at++
            skipSpace()
            if (text[at] == ']') { at++; return list }
            while (true) {
                list += value()
                skipSpace()
                if (text[at++] == ']') return list
                check(text[at - 1] == ',')
            }
        }

        private fun string(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                when (val c = text[at++]) {
                    '"' -> return sb.toString()
                    '\\' -> when (val e = text[at++]) {
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'u' -> { sb.append(text.substring(at, at + 4).toInt(16).toChar()); at += 4 }
                        else -> sb.append(e)
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun number(): Number {
            val start = at
            while (at < text.length && (text[at].isDigit() || text[at] in "+-.eE")) at++
            val s = text.substring(start, at)
            return s.toLongOrNull() ?: s.toDouble()
        }

        private fun literal(word: String, value: Any?): Any? {
            check(text.startsWith(word, at))
            at += word.length
            return value
        }

        private fun expect(c: Char) = check(text[at++] == c)

        fun skipSpace() {
            while (at < text.length && text[at].isWhitespace()) at++
        }
    }
}
