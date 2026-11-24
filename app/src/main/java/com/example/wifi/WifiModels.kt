package com.example.wifi

import android.Manifest
import android.os.Build

enum class WifiSecurityType(val label: String) {
    OPEN("Open"),
    WEP("WEP"),
    WPA2("WPA/WPA2"),
    WPA3("WPA3"),
    EAP("Enterprise (EAP)")
}

enum class WifiConnectionMode(val label: String, val description: String) {
    INTERNET_SUGGESTION(
        label = "Internet (Multi-Profile)",
        description = "Registers network credentials via Android Wi-Fi Suggestion API for full internet access & auto-switching."
    ),
    LOCAL_PEER_SPECIFIER(
        label = "Direct Local / IoT Link",
        description = "Connects directly via WifiNetworkSpecifier for local routers, IoT hardware, or offline LAN devices."
    )
}

enum class WifiConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    FAILED
}

enum class WifiUiFilter(val label: String) {
    ALL("All"),
    SAVED("Saved"),
    SECURED("Secured"),
    OPEN("Open"),
    BAND_5GHZ("5/6 GHz")
}

data class JarvisWifiNetwork(
    val ssid: String,
    val bssid: String = "",
    val rssi: Int = -100,
    val frequencyMhz: Int = 2412,
    val bandLabel: String = "2.4 GHz",
    val signalPercent: Int = 0,
    val securityType: WifiSecurityType = WifiSecurityType.WPA2,
    val isSecurityWarning: Boolean = false,
    val isSavedProfile: Boolean = false,
    val connectionStatus: WifiConnectionStatus = WifiConnectionStatus.DISCONNECTED
)

data class ActiveWifiLinkInfo(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val linkSpeedMbps: Int,
    val frequencyMhz: Int,
    val hasInternet: Boolean
)

sealed interface WifiRadioState {
    data object Unsupported : WifiRadioState
    data object Disabled : WifiRadioState
    data object Enabled : WifiRadioState
}

sealed interface WifiScanState {
    data object Idle : WifiScanState
    data object Scanning : WifiScanState
    data class Error(val message: String) : WifiScanState
}

data class WifiLogEntry(
    val id: Long,
    val timestampMillis: Long,
    val message: String,
    val isAlert: Boolean = false
)

object WifiClassifier {

    fun requiredWifiPermissions(sdkInt: Int = Build.VERSION.SDK_INT): Array<String> =
        com.example.permissions.PermissionHelper.wifiPermissions(sdkInt)

    fun parseSecurityType(capabilities: String?): WifiSecurityType {
        val caps = capabilities.orEmpty().uppercase()
        return when {
            caps.contains("SAE") || caps.contains("WPA3") -> WifiSecurityType.WPA3
            caps.contains("EAP") -> WifiSecurityType.EAP
            caps.contains("WPA") || caps.contains("PSK") || caps.contains("RSN") -> WifiSecurityType.WPA2
            caps.contains("WEP") -> WifiSecurityType.WEP
            else -> WifiSecurityType.OPEN
        }
    }

    fun isPotentialSecurityRisk(securityType: WifiSecurityType): Boolean {
        return securityType == WifiSecurityType.OPEN || securityType == WifiSecurityType.WEP
    }

    fun classifyFrequencyBand(frequencyMhz: Int): String {
        return when {
            frequencyMhz in 5925..7125 -> "6 GHz"
            frequencyMhz in 4900..5924 -> "5 GHz"
            else -> "2.4 GHz"
        }
    }

    fun calculateSignalPercent(rssi: Int): Int {
        if (rssi <= -100) return 0
        if (rssi >= -50) return 100
        return ((rssi + 100) * 2).coerceIn(0, 100)
    }

    fun sanitizeSsid(rawSsid: String?): String {
        return rawSsid.orEmpty().trim().removeSurrounding("\"")
    }
}
