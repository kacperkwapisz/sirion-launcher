package pl.perier.sirion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Receiving the broadcast is enough: it starts [SirionApp], which starts everything else. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}
