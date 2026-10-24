package pl.perier.sirion.helper

import Interface.RadioInterface
import android.bluetooth.BluetoothAdapter
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.os.Looper
import pl.perier.sirion.logic.AccessibilityServices
import pl.perier.sirion.logic.HelperCommand
import java.io.File
import java.io.PrintWriter
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * Root helper, started by the launcher with
 * `su 0 sh -c "CLASSPATH=<apk> app_process /system/bin pl.perier.sirion.helper.Helper"`.
 *
 * It runs as root outside the app sandbox, so it can load the tuner library
 * from /system/lib and open the tuner device. It listens on the abstract
 * socket `sirion-helper`, takes the few commands in [HelperCommand], and
 * sends every tuner event back as `event <json>`.
 */
object Helper {
    const val SOCKET = "sirion-helper"

    private const val PACKAGE = "pl.perier.sirion"
    private const val HOME = "$PACKAGE/.ui.HomeActivity"
    /** Turns off Android's volume pop-up (see launcher/overlays/no-volume-dialog). */
    private const val OVERLAY = "pl.perier.sirion.novolumedialog"
    private val previousApk = File("/data/local/tmp/sirion-previous.apk")
    private val stagedApk = File("/data/local/tmp/sirion-update.apk")
    private val updateScript = File("/data/local/tmp/sirion-update.sh")
    private val healthyMark = File("/data/local/tmp/sirion-healthy")

    /** How long a new version gets to report itself healthy before we roll back. */
    private const val HEALTH_WINDOW_MS = 180_000L
    private const val BRIGHTNESS_DIR = "/data/hu"
    private const val VISIBLE_SECONDS = 120
    private val CARPLAY_ENGINES = listOf("com.shihab.diplay", "com.zjinnova.zlink")
    private const val CARPLAY_CHECK_MS = 700L

    /** Arguments: new APK, previous APK, healthy mark, seconds to wait for it. */
    private val UPDATE_SCRIPT = """
        new=${'$'}1; previous=${'$'}2; mark=${'$'}3; wait=${'$'}4
        trap 'rm -f "${'$'}0"' EXIT
        result=${'$'}(pm install -r "${'$'}new" 2>&1)
        rm -f "${'$'}new"
        case "${'$'}result" in
          *Success*) ;;
          *)
            # Nothing changed: the launcher is still running and hears why.
            am broadcast -a $PACKAGE.UPDATE_FAILED -p $PACKAGE --es error "${'$'}result" >/dev/null
            exit 1 ;;
        esac
        am start -n $HOME >/dev/null
        i=0
        while [ ${'$'}i -lt ${'$'}wait ]; do
          [ -f "${'$'}mark" ] && exit 0
          sleep 1
          i=${'$'}((i + 1))
        done
        # The new version never said it was fine: put the previous one back.
        pm install -r -d "${'$'}previous" >/dev/null 2>&1
        am start -n $HOME >/dev/null
    """.trimIndent() + "\n"

    private val clients = CopyOnWriteArrayList<PrintWriter>()
    private var lastKilohertz = 92_000
    private var radioOpen = false

    private val radio by lazy {
        RadioInterface.load()
        RadioInterface { json -> broadcast("event $json") }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val server = runCatching { LocalServerSocket(SOCKET) }.getOrElse {
            // Usually another helper already owns the socket; say so either way.
            System.err.println("sirion-helper: can't listen: $it")
            return
        }
        reapplyMysplBlock()
        thread(isDaemon = true, name = "volume-card") { runCatching { ensureVolumeCard() }.onFailure { broadcast("error volume ${it.message}") } }
        // Lets the launcher read CarPlay's song from DiPlay (see CarPlayMusic).
        sh("cmd notification allow_listener $PACKAGE/$PACKAGE.data.SessionAccess")
        watchCarPlay()
        while (true) {
            val client = server.accept()
            thread(isDaemon = true) { serve(client) }
        }
    }

    private fun serve(client: LocalSocket) {
        val out = PrintWriter(client.outputStream, true)
        clients += out
        try {
            client.inputStream.bufferedReader().forEachLine { line ->
                val command = HelperCommand.parse(line) ?: return@forEachLine
                runCatching { handle(command) }.onFailure { out.println("error ${it.message}") }
            }
        } finally {
            clients -= out
            runCatching { client.close() }
        }
    }

    @Synchronized
    private fun handle(command: HelperCommand) {
        when (command) {
            is HelperCommand.Tune -> {
                ensureRadioOpen()
                lastKilohertz = command.kilohertz
                radio.setFreq(command.kilohertz)
            }
            is HelperCommand.Seek -> {
                ensureRadioOpen()
                if (command.up) radio.seekUp(lastKilohertz) else radio.seekDown(lastKilohertz)
            }
            HelperCommand.RadioOff -> if (radioOpen) {
                radio.setAudioToGps()
                radio.stopListening()
                radio.closeDevice()
                radioOpen = false
            }
            is HelperCommand.Install -> install(File(command.apk))
            HelperCommand.UnstickBluetooth -> sh("settings put system zlink_bt_off 0")
            is HelperCommand.Brightness -> {
                File(BRIGHTNESS_DIR).mkdirs()
                File("$BRIGHTNESS_DIR/brightness_settings")
                    .writeText("mode=${command.mode}\nday=${command.day}\nnight=${command.night}\n")
                ensureBrightnessTool()
            }
            is HelperCommand.Effect -> AudioChip.set(command.id, command.value)
            HelperCommand.Discoverable -> bluetooth { adapter ->
                // Hidden on 8.1 and needs a privileged permission, which root has.
                adapter.javaClass.getMethod("setScanMode", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                    .invoke(adapter, BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE, VISIBLE_SECONDS)
            }
            is HelperCommand.ConfirmPairing -> bluetooth { adapter ->
                adapter.getRemoteDevice(command.address).setPairingConfirmation(command.accept)
            }
            is HelperCommand.Clock24 -> sh("settings put system time_12_24 ${if (command.on) 24 else 12}")
            HelperCommand.Restart -> sh("reboot")
            // The script is there only while an update waits to hear from the new version.
            is HelperCommand.Healthy -> if (updateScript.isFile && !healthyMark.isFile) {
                // The new version started and ran fine: tells the update script
                // not to roll back. Exit so the next connection starts the
                // helper from the new APK.
                healthyMark.writeText("${command.versionCode}\n")
                broadcast("updated ${command.versionCode}")
                thread { Thread.sleep(500); exitProcess(0) }
            }
        }
    }

    /**
     * Installs an update with a script that runs on its own: replacing the
     * package kills the launcher, and with it the adb session this helper
     * lives in. The script installs, opens the launcher again, and puts the
     * previous version back if the new one never reports healthy.
     */
    private fun install(apk: File) {
        if (!apk.isFile) return
        val current = sh("pm path $PACKAGE").removePrefix("package:").trim()
        if (current.isNotEmpty()) File(current).copyTo(previousApk, overwrite = true)
        apk.copyTo(stagedApk, overwrite = true)
        healthyMark.delete()
        updateScript.writeText(UPDATE_SCRIPT)
        val run = "sh ${updateScript.path} ${stagedApk.path} ${previousApk.path} ${healthyMark.path} ${HEALTH_WINDOW_MS / 1000}"
        // Its own session and no pipes back to us, so it outlives this process.
        sh("if command -v setsid >/dev/null; then setsid $run; else nohup $run; fi </dev/null >/dev/null 2>&1 &")
    }

    /**
     * Our volume card instead of Android's pop-up, set up on its own so it
     * comes with an update: the card's accessibility service on, and the
     * overlay (shipped in our assets) installed and enabled. Android only
     * notices a new overlay at boot, so the pop-up goes after the next restart.
     */
    private fun ensureVolumeCard() {
        val services = sh("settings get secure enabled_accessibility_services").trim()
        val wanted = AccessibilityServices.withVolumePopup(services)
        if (wanted != services) sh("settings put secure enabled_accessibility_services '$wanted'")
        sh("settings put secure accessibility_enabled 1")

        if (sh("pm path $OVERLAY").isBlank()) {
            val apk = System.getenv("CLASSPATH") ?: return
            val copy = File("/data/local/tmp/sirion-no-volume-dialog.apk")
            java.util.zip.ZipFile(apk).use { zip ->
                val entry = zip.getEntry("assets/no-volume-dialog.apk") ?: return
                zip.getInputStream(entry).use { input -> copy.outputStream().use(input::copyTo) }
            }
            sh("pm install -r ${copy.path}")
            copy.delete()
        }
        val overlays = sh("cmd overlay list")
        if ("[ ] $OVERLAY" in overlays) {
            sh("cmd overlay enable $OVERLAY")
            // The system UI reads it when it starts; the screen blinks once.
            sh("pkill -f com.android.systemui")
        }
    }

    /**
     * Starts our brightness tool if it isn't running. Its binary ships inside
     * the APK (assets/hu-brightnessd); the helper copies it out, since only
     * root may run it and nothing may live in /system.
     */
    private fun ensureBrightnessTool() {
        if (sh("pidof hu-brightnessd").isNotBlank()) return
        val apk = System.getenv("CLASSPATH") ?: return
        val binary = File("$BRIGHTNESS_DIR/hu-brightnessd")
        runCatching {
            java.util.zip.ZipFile(apk).use { zip ->
                val entry = zip.getEntry("assets/hu-brightnessd") ?: return
                zip.getInputStream(entry).use { input -> binary.outputStream().use(input::copyTo) }
            }
            binary.setExecutable(true, false)
            sh("${binary.path} >/dev/null 2>&1 &")
        }.onFailure { broadcast("error brightness ${it.message}") }
    }

    /**
     * The vendor's myspl tool remounts /system; scripts/block-myspl.sh can
     * replace it with a stand-in (a bind mount, gone after a reboot). While
     * the driver has it switched on, put it back at every start.
     */
    private fun reapplyMysplBlock() {
        if (!File("$BRIGHTNESS_DIR/block-myspl").isFile) return
        val apk = System.getenv("CLASSPATH") ?: return
        val guard = File("$BRIGHTNESS_DIR/myspl-guard.sh")
        runCatching {
            java.util.zip.ZipFile(apk).use { zip ->
                listOf("myspl-guard.sh", "hu-bindro").forEach { name ->
                    val entry = zip.getEntry("assets/$name") ?: return
                    val file = File("$BRIGHTNESS_DIR/$name")
                    zip.getInputStream(entry).use { input -> file.outputStream().use(input::copyTo) }
                    file.setExecutable(true, false)
                }
            }
            sh("${guard.path} apply")
        }.onFailure { broadcast("error myspl ${it.message}") }
    }

    /**
     * Tells the launcher when the CarPlay engine comes to the front or goes
     * back ("carplay front" / "carplay back"); the launcher decides what to
     * do. The foreground app is the one with oom_score_adj 0, which is one
     * small file read per check instead of a dumpsys.
     */
    private fun watchCarPlay() = thread(isDaemon = true, name = "carplay-watch") {
        var pids = emptyMap<String, Int>()
        var front = false
        while (true) {
            runCatching {
                if (pids.values.none { File("/proc/$it").exists() }) pids = findPids(CARPLAY_ENGINES)
                val now = pids.values.any { pid ->
                    runCatching { File("/proc/$pid/oom_score_adj").readText().trim() == "0" }.getOrDefault(false)
                }
                if (now != front) {
                    front = now
                    broadcast(if (now) "carplay front" else "carplay back")
                }
            }
            Thread.sleep(CARPLAY_CHECK_MS)
        }
    }

    /** Main process of each package, found by its command line. */
    private fun findPids(packages: List<String>): Map<String, Int> =
        File("/proc").listFiles().orEmpty().mapNotNull { dir ->
            val pid = dir.name.toIntOrNull() ?: return@mapNotNull null
            val name = runCatching { File(dir, "cmdline").readText().substringBefore('\u0000') }.getOrNull()
            if (name in packages) name!! to pid else null
        }.toMap()

    private fun bluetooth(action: (BluetoothAdapter) -> Unit) {
        if (Looper.myLooper() == null) Looper.prepare()
        BluetoothAdapter.getDefaultAdapter()?.let(action)
    }

    private fun sh(command: String): String = runCatching {
        val process = ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start()
        process.inputStream.bufferedReader().readText().also { process.waitFor() }
    }.getOrDefault("")

    private fun ensureRadioOpen() {
        if (radioOpen) return
        radio.listen()
        radio.openDevice()
        radio.setAudioToRadio()
        radioOpen = true
    }

    private fun broadcast(line: String) = clients.forEach { it.println(line) }
}
