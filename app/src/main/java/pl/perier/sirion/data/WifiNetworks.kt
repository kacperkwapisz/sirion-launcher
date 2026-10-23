package pl.perier.sirion.data

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import pl.perier.sirion.logic.SettingsText

/** Saved and nearby Wi-Fi networks, straight from Android's WifiManager. */
@SuppressLint("MissingPermission")
class WifiNetworks(context: Context) {
    data class Saved(val id: Int, val name: String, val connected: Boolean)
    data class Nearby(val name: String, val secured: Boolean, val level: Int)

    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)

    var isOn: Boolean
        get() = wifi.isWifiEnabled
        set(value) { wifi.isWifiEnabled = value }

    fun saved(): List<Saved> {
        val current = wifi.connectionInfo?.networkId ?: -1
        return wifi.configuredNetworks.orEmpty()
            .mapNotNull { c -> SettingsText.ssid(c.SSID)?.let { Saved(c.networkId, it, c.networkId == current) } }
            .sortedWith(compareByDescending<Saved> { it.connected }.thenBy { it.name.lowercase() })
    }

    fun scan() = wifi.startScan()

    /** Strongest first, one row per name, saved networks left out. */
    fun nearby(): List<Nearby> {
        val saved = saved().map { it.name }.toSet()
        return wifi.scanResults.orEmpty()
            .mapNotNull { r -> SettingsText.ssid(r.SSID)?.let { Nearby(it, isSecured(r), r.level) } }
            .filter { it.name !in saved }
            .groupBy { it.name }.map { (_, same) -> same.maxBy { it.level } }
            .sortedByDescending { it.level }
    }

    fun join(id: Int) = wifi.enableNetwork(id, true).also { wifi.reconnect() }

    fun disconnect() = wifi.disconnect()

    /** Saves a new network and joins it. An empty password means an open network. */
    fun join(name: String, password: String): Boolean {
        val config = WifiConfiguration().apply {
            SSID = "\"$name\""
            if (password.isEmpty()) allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
            else preSharedKey = "\"$password\""
        }
        val id = wifi.addNetwork(config)
        if (id < 0) return false
        @Suppress("DEPRECATION") wifi.saveConfiguration()
        return join(id)
    }

    private fun isSecured(r: ScanResult) = listOf("WPA", "WEP", "PSK", "EAP").any { it in r.capabilities }
}
