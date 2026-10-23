package pl.perier.sirion.logic

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Talks to a real adbd. Skipped unless SIRION_ADB_HOST is set, e.g.
 * SIRION_ADB_HOST=<unit-ip> ./gradlew testDebugUnitTest --tests '*AdbShellUnitTest*'
 * SIRION_ADB_COMMAND overrides the command. Uses the launcher's key from ~/.android/sirion-launcher-adbkey.
 */
class AdbShellUnitTest {
    @Test
    fun runsIdOnTheUnit() {
        val host = System.getenv("SIRION_ADB_HOST")
        assumeTrue(host != null)
        val key = AdbProtocol.privateKey(File(System.getProperty("user.home"), ".android/sirion-launcher-adbkey").readText())
        val command = System.getenv("SIRION_ADB_COMMAND") ?: "id; su 0 id"
        val port = System.getenv("SIRION_ADB_PORT")?.toInt() ?: 5555
        val result = AdbShell.run(host!!, port, key, command)
        println("adb result: $result")
        check(result is AdbShell.Result.Output) { "adb result: $result" }
    }
}
