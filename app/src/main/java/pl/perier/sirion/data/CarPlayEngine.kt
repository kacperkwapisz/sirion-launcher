package pl.perier.sirion.data

import android.content.Context
import android.content.Intent
import pl.perier.sirion.SirionApp
import pl.perier.sirion.logic.CarPlayStatus

/**
 * The CarPlay engine is the one piece we can't replace yet. ZLink (the
 * unit's licensed receiver) or DiPlay (open source) runs the session; we
 * read its state and open it, and with DiPlay also its song.
 */
object CarPlayEngine {
    val engines = listOf("com.shihab.diplay", "com.zjinnova.zlink")

    /** [diPlaySession]: DiPlay has its CarPlay media session (see [CarPlayMusic]). */
    fun status(diPlaySession: Boolean = false): CarPlayStatus = if (Demo.carPlay) CarPlayStatus.Connected(Demo.PHONE) else CarPlayStatus.from(
        connected = SystemProps.get("rw.zlink.connected"),
        phoneType = SystemProps.get("rw.zlink.phone.type"),
        bluetoothPhone = BluetoothPhones.connectedName(),
        diPlaySession = diPlaySession,
    )

    /** Opens whichever engine is installed and enabled. Returns false if none is. */
    fun open(context: Context): Boolean {
        val intent = engines.firstNotNullOfOrNull { context.packageManager.getLaunchIntentForPackage(it) } ?: return false
        // The driver asked for it, so the focus guard lets it through.
        (context.applicationContext as? SirionApp)?.carPlayFocus?.driverOpened()
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }
}
