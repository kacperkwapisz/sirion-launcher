package pl.perier.sirion.data

import android.content.Context
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.util.Log
import pl.perier.sirion.helper.Helper
import pl.perier.sirion.logic.HelperCommand
import java.io.PrintWriter
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * Talks to our root helper over its local socket, starting it the first
 * time through the unit's own adbd (see [LocalAdb]). All work happens off
 * the main thread.
 */
class RootHelper(
    private val context: Context,
    private val onCarPlayFront: (Boolean) -> Unit = {},
    private val onEvent: (String) -> Unit,
) {
    private val worker = Executors.newSingleThreadExecutor()
    private val adb = LocalAdb(context)
    private var socket: LocalSocket? = null
    private var out: PrintWriter? = null

    fun send(command: HelperCommand) = worker.execute {
        val writer = out ?: connect() ?: return@execute
        writer.println(command.line)
        if (writer.checkError()) {
            close()
            connect()?.println(command.line)
        }
    }

    fun close() {
        runCatching { socket?.close() }
        socket = null
        out = null
    }

    private fun connect(): PrintWriter? {
        tryConnect()?.let { return it }
        startHelper()
        // app_process takes a few seconds on this unit.
        repeat(60) {
            Thread.sleep(200)
            tryConnect()?.let { return it }
        }
        Log.w(TAG, "Root helper did not start")
        return null
    }

    private fun tryConnect(): PrintWriter? = runCatching {
        val s = LocalSocket().apply { connect(LocalSocketAddress(Helper.SOCKET)) }
        socket = s
        Thread(::readEvents, "helper-events").apply { isDaemon = true }.start()
        PrintWriter(s.outputStream, true).also { out = it }
    }.getOrNull()

    private fun readEvents() {
        val s = socket ?: return
        runCatching {
            s.inputStream.bufferedReader().forEachLine { line ->
                when {
                    line.startsWith("event ") -> onEvent(line.removePrefix("event "))
                    line == "carplay front" -> onCarPlayFront(true)
                    line == "carplay back" -> onCarPlayFront(false)
                    else -> Log.i(TAG, line)
                }
            }
        }
    }

    /** The local adb session the helper runs in; open as long as the helper runs. */
    @Volatile private var holding = false

    private fun startHelper() {
        // Run from a copy, so installing an update doesn't pull the helper's
        // own code out from under it.
        val apk = context.applicationInfo.sourceDir
        val copy = "/data/local/tmp/sirion-helper.apk"
        val run = "cp $apk $copy && CLASSPATH=$copy exec app_process /system/bin ${Helper::class.java.name} </dev/null >/dev/null 2>&1"
        if (adb.hasKey) {
            // The unit's su only lets the shell user in, so go through its own adbd. The helper
            // runs in that session's foreground and ends with it, so we hold the session open
            // and start it again if it ever closes.
            if (holding) return
            holding = true
            thread(isDaemon = true, name = "helper-session") {
                adb.hold("su 0 sh -c '$run'")
                Log.w(TAG, "Root helper session ended")
                holding = false
                close()
            }
            return
        }
        // Fallback for units where apps may use su directly.
        runCatching { Runtime.getRuntime().exec(arrayOf("su", "0", "sh", "-c", "$run &")).waitFor() }
            .onFailure { Log.w(TAG, "Could not start root helper", it) }
    }

    private companion object {
        const val TAG = "SirionHelper"
    }
}
