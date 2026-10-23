package pl.perier.sirion.data

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import pl.perier.sirion.logic.HelperCommand
import pl.perier.sirion.logic.UpdateManifest
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * Over-the-air updates. When the unit is online (home Wi-Fi, or the iPhone
 * hotspot with CarPlay off) it reads `update.json` from the latest GitHub
 * release, downloads a newer APK, checks its SHA-256, and hands it to the
 * root helper, which installs it silently and rolls back if the new version
 * never reports healthy.
 */
class Updater(private val context: Context, private val helper: RootHelper) {
    private val worker = Executors.newSingleThreadExecutor()
    private val prefs get() = context.getSharedPreferences("updater", Context.MODE_PRIVATE)

    val installedVersion: Int
        @Suppress("DEPRECATION")
        get() = context.packageManager.getPackageInfo(context.packageName, 0).versionCode

    /** Checks at most every [INTERVAL_MS] unless [force] is set. */
    fun checkSoon(force: Boolean = false) = worker.execute {
        val last = prefs.getLong(KEY_LAST_CHECK, 0)
        if (!force && System.currentTimeMillis() - last < INTERVAL_MS) return@execute
        if (!online()) return@execute
        prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
        runCatching { check() }.onFailure { Log.w(TAG, "Update check failed", it) }
    }

    private fun check() {
        val manifest = UpdateManifest.parse(fetch(MANIFEST_URL).decodeToString()) ?: return
        if (!manifest.isNewerThan(installedVersion)) return
        val dir = File(context.cacheDir, "ota").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val apk = File(dir, "sirion-${manifest.versionCode}.apk")
        apk.writeBytes(fetch(manifest.apk))
        if (!manifest.matches(sha256(apk))) {
            Log.w(TAG, "Downloaded APK does not match its hash; ignoring")
            apk.delete()
            return
        }
        Log.i(TAG, "Installing ${manifest.versionName} (${manifest.versionCode})")
        helper.send(HelperCommand.Install(apk.path))
    }

    private fun online(): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        @Suppress("DEPRECATION")
        return connectivity.activeNetworkInfo?.isConnected == true
    }

    private fun fetch(url: String): ByteArray {
        var target = URL(url)
        repeat(5) {
            val connection = target.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            when (connection.responseCode) {
                in 300..399 -> {
                    // GitHub redirects release downloads to its object storage.
                    target = URL(target, connection.getHeaderField("Location"))
                    connection.disconnect()
                }
                200 -> return connection.inputStream.use { it.readBytes() }
                else -> error("HTTP ${connection.responseCode} for $target")
            }
        }
        error("Too many redirects for $url")
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val TAG = "SirionUpdater"
        const val MANIFEST_URL = "https://github.com/kacperkwapisz/sirion-launcher/releases/latest/download/update.json"
        const val INTERVAL_MS = 6 * 60 * 60 * 1000L
        const val KEY_LAST_CHECK = "last_check"
    }
}
