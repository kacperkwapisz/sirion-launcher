package pl.perier.sirion.logic

import java.io.DataInputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.PrivateKey

/** One shell command over the ADB protocol (see [AdbProtocol]), in plain JVM code. */
object AdbShell {
    sealed class Result {
        data class Output(val text: String) : Result()
        /** adbd didn't accept our key (it asked again after our signature). */
        object Rejected : Result()
        data class Failed(val reason: String) : Result()
    }

    /**
     * [readTimeoutMs] 0 waits as long as the command runs, for a command whose
     * lifetime is the session's (the root helper).
     */
    fun run(host: String, port: Int, key: PrivateKey, command: String, timeoutMs: Int = 8_000, readTimeoutMs: Int = timeoutMs): Result = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            socket.soTimeout = timeoutMs
            val input = DataInputStream(socket.getInputStream())
            val output = socket.getOutputStream()
            fun send(m: AdbProtocol.Message) = output.write(AdbProtocol.encode(m)).also { output.flush() }
            fun receive(): Pair<AdbProtocol.Header, ByteArray> {
                val header = AdbProtocol.readHeader(ByteArray(24).also(input::readFully))
                return header to ByteArray(header.length).also(input::readFully)
            }

            send(AdbProtocol.connect())
            var (header, payload) = receive()
            var signed = false
            while (header.command == AdbProtocol.AUTH) {
                // A second token means our key isn't authorised; don't offer it (that would pop a prompt on screen).
                if (signed) return Result.Rejected
                send(AdbProtocol.signature(payload, key))
                signed = true
                val next = receive()
                header = next.first
                payload = next.second
            }
            if (header.command != AdbProtocol.CNXN) return Result.Failed("expected CNXN, got 0x%08x".format(header.command))

            send(AdbProtocol.openShell(LOCAL_ID, command))
            socket.soTimeout = readTimeoutMs
            val text = StringBuilder()
            var closed = false
            while (!closed) {
                val (h, data) = receive()
                when (h.command) {
                    AdbProtocol.WRTE -> {
                        text.append(String(data))
                        send(AdbProtocol.Message(AdbProtocol.OKAY, LOCAL_ID, h.arg0))
                    }
                    AdbProtocol.CLSE -> closed = true
                }
            }
            Result.Output(text.toString())
        }
    } catch (e: Exception) {
        Result.Failed(e.toString())
    }

    private const val LOCAL_ID = 1
}
