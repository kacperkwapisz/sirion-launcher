package pl.perier.sirion.data

/** Reads Android system properties. The API is hidden but open on Android 8.1. */
object SystemProps {
    private val getter by lazy {
        Class.forName("android.os.SystemProperties").getMethod("get", String::class.java, String::class.java)
    }

    fun get(key: String): String = runCatching { getter.invoke(null, key, "") as String }.getOrDefault("")
}
