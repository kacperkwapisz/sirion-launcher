package pl.perier.sirion.data

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice

/** Phones as Android's own Bluetooth stack sees them. No vendor app involved. */
object BluetoothPhones {
    private val isConnected by lazy { BluetoothDevice::class.java.getMethod("isConnected") }

    /** Name of the first bonded device that is connected right now, or null. */
    fun connectedName(): String? {
        if (Demo.on) return Demo.PHONE
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return null
        if (!adapter.isEnabled) return null
        return adapter.bondedDevices.orEmpty()
            .firstOrNull { device -> runCatching { isConnected.invoke(device) as Boolean }.getOrDefault(false) }
            ?.name
    }

    fun isOn(): Boolean = BluetoothAdapter.getDefaultAdapter()?.isEnabled == true
}
