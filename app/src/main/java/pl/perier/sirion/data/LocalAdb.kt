package pl.perier.sirion.data

import android.content.Context
import android.util.Log
import pl.perier.sirion.logic.AdbProtocol
import pl.perier.sirion.logic.AdbShell
import java.io.File

/**
 * Runs one shell command through the unit's own adbd on localhost:5555, as
 * the shell user (who may use `su`). The key is put in place by
 * scripts/setup-unit.sh: its public half in /data/misc/adb/adb_keys, its
 * private half in our files dir. Loopback works even while CarPlay has
 * Wi-Fi down.
 */
class LocalAdb(private val context: Context) {
    private val keyFile get() = File(context.filesDir, KEY_FILE)

    val hasKey get() = keyFile.isFile

    /** Runs [command] and returns its output, or null (with the reason logged) if adbd wouldn't let us in. */
    fun shell(command: String): String? = run(command, readTimeoutMs = TIMEOUT_MS)

    /** Runs [command] and keeps the session open until it ends; blocks the calling thread that long. */
    fun hold(command: String): String? = run(command, readTimeoutMs = 0)

    private fun run(command: String, readTimeoutMs: Int): String? {
        val key = runCatching { AdbProtocol.privateKey(keyFile.readText()) }
            .onFailure { Log.w(TAG, "No usable key in $keyFile", it) }.getOrNull() ?: return null
        return when (val result = AdbShell.run("127.0.0.1", PORT, key, command, TIMEOUT_MS, readTimeoutMs)) {
            is AdbShell.Result.Output -> result.text
            AdbShell.Result.Rejected -> null.also { Log.w(TAG, "adbd rejected our key") }
            is AdbShell.Result.Failed -> null.also { Log.w(TAG, "Local adb failed: ${result.reason}") }
        }
    }

    companion object {
        const val KEY_FILE = "adbkey"
        private const val TAG = "SirionAdb"
        private const val PORT = 5555
        private const val TIMEOUT_MS = 8_000
    }
}
