package com.example.wifi

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Multi-network Wi-Fi scanner and connection manager for the JARVIS AI suite.
 * Supports:
 * - Scanning nearby 2.4 / 5 / 6 GHz networks with White-Hat encryption auditing
 * - Internet multi-network registration via WifiNetworkSuggestion (API 29+)
 * - Direct local / IoT Wi-Fi connection via WifiNetworkSpecifier (API 29+)
 * - Legacy WifiConfiguration fallback for Android 7–9 (API 24–28)
 */
class JarvisWifiManager(context: Context) {

    private val appContext: Context = context.applicationContext
    private val wifiManager: WifiManager? =
        appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val connectivityManager: ConnectivityManager? =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val logSeq = AtomicLong(0L)
    private val networkMap = ConcurrentHashMap<String, JarvisWifiNetwork>()
    private val savedSuggestions = ConcurrentHashMap<String, WifiNetworkSuggestion>()

    private var receiverRegistered = false
    private var defaultWifiCallbackRegistered = false
    private var activeSpecifierCallback: ConnectivityManager.NetworkCallback? = null
    private var activeSpecifierSsid: String? = null

    private val _radioState = MutableStateFlow<WifiRadioState>(WifiRadioState.Disabled)
    val radioState: StateFlow<WifiRadioState> = _radioState.asStateFlow()

    private val _scanState = MutableStateFlow<WifiScanState>(WifiScanState.Idle)
    val scanState: StateFlow<WifiScanState> = _scanState.asStateFlow()

    private val _networks = MutableStateFlow<List<JarvisWifiNetwork>>(emptyList())
    val networks: StateFlow<List<JarvisWifiNetwork>> = _networks.asStateFlow()

    private val _activeLink = MutableStateFlow<ActiveWifiLinkInfo?>(null)
    val activeLink: StateFlow<ActiveWifiLinkInfo?> = _activeLink.asStateFlow()

    private val _logs = MutableStateFlow<List<WifiLogEntry>>(emptyList())
    val logs: StateFlow<List<WifiLogEntry>> = _logs.asStateFlow()

    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                    refreshRadioState()
                    val state = intent.getIntExtra(
                        WifiManager.EXTRA_WIFI_STATE,
                        WifiManager.WIFI_STATE_UNKNOWN
                    )
                    if (state == WifiManager.WIFI_STATE_ENABLED) {
                        addLog("Wi-Fi radio enabled.")
                        refreshScanResults()
                        refreshActiveConnection()
                    } else if (state == WifiManager.WIFI_STATE_DISABLED) {
                        addLog("Wi-Fi radio disabled.", isAlert = true)
                        _activeLink.value = null
                        _scanState.value = WifiScanState.Idle
                    }
                }

                WifiManager.SCAN_RESULTS_AVAILABLE_ACTION -> {
                    val updated = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                    refreshScanResults()
                    _scanState.value = WifiScanState.Idle
                    addLog(
                        if (updated) "Wi-Fi spectrum scan completed."
                        else "Loaded cached Wi-Fi scan results (OS scan throttle active)."
                    )
                }

                WifiManager.ACTION_WIFI_NETWORK_SUGGESTION_POST_CONNECTION -> {
                    addLog("Connected via JARVIS Wi-Fi suggestion profile.")
                    refreshActiveConnection()
                    refreshScanResults()
                }
            }
        }
    }

    private val wifiMonitorCallback: ConnectivityManager.NetworkCallback =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
                override fun onAvailable(network: Network) {
                    refreshActiveConnection()
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    val wifiInfo = networkCapabilities.transportInfo as? WifiInfo
                    updateActiveLinkFromWifiInfo(wifiInfo, networkCapabilities)
                }

                override fun onLost(network: Network) {
                    _activeLink.value = null
                    refreshScanResults()
                    addLog("Wi-Fi connection lost.")
                }
            }
        } else {
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    refreshActiveConnection()
                }

                override fun onLost(network: Network) {
                    _activeLink.value = null
                    refreshScanResults()
                    addLog("Wi-Fi connection lost.")
                }
            }
        }

    init {
        registerReceiver()
        registerWifiMonitor()
        refreshRadioState()
        if (hasScanPermission() && isWifiEnabled()) {
            refreshScanResults()
        }
        refreshActiveConnection()
    }

    fun requiredPermissions(): Array<String> =
        com.example.permissions.PermissionHelper.wifiPermissions()

    fun hasScanPermission(): Boolean =
        com.example.permissions.PermissionHelper.hasWifiPermissions(appContext)

    fun isWifiSupported(): Boolean = wifiManager != null

    fun isWifiEnabled(): Boolean = wifiManager?.isWifiEnabled == true

    fun refreshRadioState() {
        val mgr = wifiManager
        _radioState.value = when {
            mgr == null -> WifiRadioState.Unsupported
            mgr.isWifiEnabled -> WifiRadioState.Enabled
            else -> WifiRadioState.Disabled
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    fun startScan(): Result<Unit> {
        val mgr = wifiManager
            ?: return Result.failure(IllegalStateException("Wi-Fi hardware is unavailable."))
        if (!mgr.isWifiEnabled) {
            refreshRadioState()
            return Result.failure(IllegalStateException("Wi-Fi radio is turned off."))
        }
        if (!hasScanPermission()) {
            return Result.failure(SecurityException("Wi-Fi nearby scan permission is required."))
        }

        _scanState.value = WifiScanState.Scanning
        refreshScanResults()
        refreshActiveConnection()

        return try {
            val started = mgr.startScan()
            if (!started) {
                _scanState.value = WifiScanState.Idle
                addLog("Using latest cached Wi-Fi results (Android scan rate limit).")
            } else {
                addLog("Scanning nearby 2.4 / 5 / 6 GHz access points...")
            }
            Result.success(Unit)
        } catch (se: SecurityException) {
            _scanState.value = WifiScanState.Error("Permission denied for Wi-Fi scan.")
            Result.failure(se)
        } catch (e: Exception) {
            _scanState.value = WifiScanState.Error(e.localizedMessage ?: "Wi-Fi scan failed.")
            Result.failure(e)
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshScanResults() {
        val mgr = wifiManager ?: return
        if (!hasScanPermission()) return

        try {
            val results: List<ScanResult> = mgr.scanResults.orEmpty()
            val activeSsid = _activeLink.value?.ssid
            val grouped = linkedMapOf<String, ScanResult>()

            results.forEach { sr ->
                val ssid = extractSsid(sr)
                if (ssid.isNotBlank()) {
                    val prev = grouped[ssid]
                    if (prev == null || sr.level > prev.level) {
                        grouped[ssid] = sr
                    }
                }
            }

            grouped.forEach { (ssid, sr) ->
                val sec = WifiClassifier.parseSecurityType(sr.capabilities)
                val freq = sr.frequency
                val isCurrent = activeSsid != null && activeSsid == ssid
                networkMap[ssid] = JarvisWifiNetwork(
                    ssid = ssid,
                    bssid = sr.BSSID.orEmpty(),
                    rssi = sr.level,
                    frequencyMhz = freq,
                    bandLabel = WifiClassifier.classifyFrequencyBand(freq),
                    signalPercent = WifiClassifier.calculateSignalPercent(sr.level),
                    securityType = sec,
                    isSecurityWarning = WifiClassifier.isPotentialSecurityRisk(sec),
                    isSavedProfile = savedSuggestions.containsKey(ssid),
                    connectionStatus = if (isCurrent) {
                        WifiConnectionStatus.CONNECTED
                    } else {
                        networkMap[ssid]?.connectionStatus
                            ?.takeIf { it == WifiConnectionStatus.CONNECTING }
                            ?: WifiConnectionStatus.DISCONNECTED
                    }
                )
            }

            // Keep saved profiles visible even if temporarily out of scan range
            savedSuggestions.keys.forEach { ssid ->
                if (!networkMap.containsKey(ssid)) {
                    networkMap[ssid] = JarvisWifiNetwork(
                        ssid = ssid,
                        isSavedProfile = true,
                        connectionStatus = if (activeSsid == ssid) {
                            WifiConnectionStatus.CONNECTED
                        } else {
                            WifiConnectionStatus.DISCONNECTED
                        }
                    )
                }
            }

            publishNetworks()
        } catch (se: SecurityException) {
            Log.e(TAG, "Missing permission reading Wi-Fi scan results", se)
        }
    }

    /**
     * Connects to any Wi-Fi network using either:
     * - [WifiConnectionMode.INTERNET_SUGGESTION] (recommended for multi-Wi-Fi internet switching)
     * - [WifiConnectionMode.LOCAL_PEER_SPECIFIER] (direct peer/IoT AP binding without internet)
     */
    fun connectToNetwork(
        ssid: String,
        passphrase: String,
        securityType: WifiSecurityType,
        mode: WifiConnectionMode
    ): Result<String> {
        val cleanSsid = WifiClassifier.sanitizeSsid(ssid)
        if (cleanSsid.isBlank()) {
            return Result.failure(IllegalArgumentException("SSID cannot be empty."))
        }
        val mgr = wifiManager
            ?: return Result.failure(IllegalStateException("Wi-Fi hardware unavailable."))
        if (!mgr.isWifiEnabled) {
            return Result.failure(IllegalStateException("Turn on Wi-Fi first."))
        }
        if (securityType != WifiSecurityType.OPEN &&
            securityType != WifiSecurityType.EAP &&
            passphrase.length < 8
        ) {
            return Result.failure(
                IllegalArgumentException("WPA2/WPA3 password must be at least 8 characters.")
            )
        }

        if (WifiClassifier.isPotentialSecurityRisk(securityType)) {
            addLog("Security Notice: '$cleanSsid' uses ${securityType.label} encryption.", isAlert = true)
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (mode) {
                WifiConnectionMode.INTERNET_SUGGESTION ->
                    connectWithSuggestionApi(mgr, cleanSsid, passphrase, securityType)
                WifiConnectionMode.LOCAL_PEER_SPECIFIER ->
                    connectWithSpecifierApi(cleanSsid, passphrase, securityType)
            }
        } else {
            connectLegacy(mgr, cleanSsid, passphrase, securityType)
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectWithSuggestionApi(
        mgr: WifiManager,
        ssid: String,
        passphrase: String,
        securityType: WifiSecurityType
    ): Result<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return Result.failure(IllegalStateException("Suggestion API requires Android 10+"))
        }
        return try {
            val builder = WifiNetworkSuggestion.Builder().setSsid(ssid)
            when (securityType) {
                WifiSecurityType.WPA3 -> builder.setWpa3Passphrase(passphrase)
                WifiSecurityType.WPA2 -> builder.setWpa2Passphrase(passphrase)
                WifiSecurityType.OPEN -> { /* Open network */ }
                WifiSecurityType.WEP,
                WifiSecurityType.EAP -> {
                    return Result.failure(
                        IllegalArgumentException("${securityType.label} networks should be added in System Wi-Fi Settings.")
                    )
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setIsInitialAutojoinEnabled(true)
            }
            val suggestion = builder.build()
            val status = mgr.addNetworkSuggestions(listOf(suggestion))
            if (status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS ||
                status == WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_DUPLICATE
            ) {
                savedSuggestions[ssid] = suggestion
                markNetworkState(ssid, isSaved = true, status = WifiConnectionStatus.CONNECTING)
                addLog("Registered Wi-Fi profile '$ssid' for internet auto-connect.")
                Result.success(
                    "Profile '$ssid' registered. Approve the system prompt if shown, or tap Wi-Fi Panel to switch immediately."
                )
            } else {
                val msg = "System rejected Wi-Fi suggestion (code $status)."
                addLog(msg, isAlert = true)
                Result.failure(IllegalStateException(msg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Suggestion API error for $ssid", e)
            addLog("Suggestion failed for $ssid: ${e.localizedMessage}", isAlert = true)
            Result.failure(e)
        }
    }

    private fun connectWithSpecifierApi(
        ssid: String,
        passphrase: String,
        securityType: WifiSecurityType
    ): Result<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return Result.failure(IllegalStateException("Specifier API requires Android 10+"))
        }
        val cm = connectivityManager
            ?: return Result.failure(IllegalStateException("ConnectivityManager unavailable."))

        disconnectDirectSpecifier()

        return try {
            val specBuilder = WifiNetworkSpecifier.Builder().setSsid(ssid)
            when (securityType) {
                WifiSecurityType.WPA3 -> specBuilder.setWpa3Passphrase(passphrase)
                WifiSecurityType.WPA2 -> specBuilder.setWpa2Passphrase(passphrase)
                WifiSecurityType.OPEN -> { /* Open network */ }
                WifiSecurityType.WEP,
                WifiSecurityType.EAP -> {
                    return Result.failure(
                        IllegalArgumentException("${securityType.label} is not supported by direct specifier.")
                    )
                }
            }

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specBuilder.build())
                .build()

            activeSpecifierSsid = ssid
            markNetworkState(ssid, status = WifiConnectionStatus.CONNECTING)
            addLog("Requesting direct local Wi-Fi link to '$ssid'...")

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    cm.bindProcessToNetwork(network)
                    markNetworkState(ssid, status = WifiConnectionStatus.CONNECTED)
                    refreshActiveConnection()
                    addLog("Direct local Wi-Fi connected to '$ssid'.")
                }

                override fun onUnavailable() {
                    markNetworkState(ssid, status = WifiConnectionStatus.FAILED)
                    addLog("Direct Wi-Fi link to '$ssid' declined or unavailable.", isAlert = true)
                }

                override fun onLost(network: Network) {
                    cm.bindProcessToNetwork(null)
                    markNetworkState(ssid, status = WifiConnectionStatus.DISCONNECTED)
                    addLog("Direct local Wi-Fi link to '$ssid' closed.")
                }
            }

            activeSpecifierCallback = callback
            cm.requestNetwork(request, callback)
            Result.success("Connecting directly to '$ssid'. Confirm in the system dialog.")
        } catch (e: Exception) {
            Log.e(TAG, "Specifier connection failed for $ssid", e)
            markNetworkState(ssid, status = WifiConnectionStatus.FAILED)
            Result.failure(e)
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun connectLegacy(
        mgr: WifiManager,
        ssid: String,
        passphrase: String,
        securityType: WifiSecurityType
    ): Result<String> {
        return try {
            val config = WifiConfiguration().apply {
                SSID = "\"$ssid\""
                when (securityType) {
                    WifiSecurityType.OPEN -> allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                    WifiSecurityType.WPA2,
                    WifiSecurityType.WPA3 -> preSharedKey = "\"$passphrase\""
                    WifiSecurityType.WEP -> {
                        wepKeys[0] = "\"$passphrase\""
                        wepTxKeyIndex = 0
                        allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                    }
                    WifiSecurityType.EAP -> {
                        return Result.failure(
                            IllegalArgumentException("Use System Wi-Fi Settings for EAP.")
                        )
                    }
                }
            }
            val netId = mgr.addNetwork(config)
            if (netId == -1) {
                return Result.failure(IllegalStateException("Failed to add legacy Wi-Fi profile."))
            }
            mgr.disconnect()
            mgr.enableNetwork(netId, true)
            mgr.reconnect()
            markNetworkState(ssid, isSaved = true, status = WifiConnectionStatus.CONNECTING)
            addLog("Connecting (legacy) to '$ssid'...")
            Result.success("Connecting to '$ssid'...")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun removeSavedSuggestion(ssid: String): Result<Unit> {
        val mgr = wifiManager
            ?: return Result.failure(IllegalStateException("Wi-Fi unavailable."))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val suggestion = savedSuggestions.remove(ssid)
            if (suggestion != null) {
                mgr.removeNetworkSuggestions(listOf(suggestion))
                markNetworkState(ssid, isSaved = false, status = WifiConnectionStatus.DISCONNECTED)
                addLog("Removed saved Wi-Fi profile '$ssid'.")
            }
        }
        return Result.success(Unit)
    }

    fun disconnectDirectSpecifier() {
        val cm = connectivityManager
        activeSpecifierCallback?.let { cb ->
            try {
                cm?.bindProcessToNetwork(null)
                cm?.unregisterNetworkCallback(cb)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering specifier callback", e)
            }
        }
        activeSpecifierCallback = null
        activeSpecifierSsid?.let { ssid ->
            markNetworkState(ssid, status = WifiConnectionStatus.DISCONNECTED)
        }
        activeSpecifierSsid = null
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    fun refreshActiveConnection() {
        val cm = connectivityManager ?: return
        val mgr = wifiManager ?: return
        val activeNetwork = cm.activeNetwork
        val caps = activeNetwork?.let { cm.getNetworkCapabilities(it) }

        if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
            val transportWifi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                caps.transportInfo as? WifiInfo
            } else {
                null
            }
            val info = transportWifi ?: try {
                mgr.connectionInfo
            } catch (_: SecurityException) {
                null
            }
            updateActiveLinkFromWifiInfo(info, caps)
        } else if (activeSpecifierSsid == null) {
            _activeLink.value = null
        }
    }

    private fun updateActiveLinkFromWifiInfo(info: WifiInfo?, caps: NetworkCapabilities?) {
        if (info == null) return
        val rawSsid = WifiClassifier.sanitizeSsid(info.ssid)
        val ssid = if (rawSsid.isBlank() || rawSsid == "<unknown ssid>") {
            activeSpecifierSsid ?: "Connected Wi-Fi"
        } else {
            rawSsid
        }
        val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val link = ActiveWifiLinkInfo(
            ssid = ssid,
            bssid = info.bssid.orEmpty(),
            rssi = info.rssi,
            linkSpeedMbps = info.linkSpeed,
            frequencyMhz = info.frequency,
            hasInternet = hasInternet
        )
        _activeLink.value = link

        networkMap.keys.forEach { key ->
            networkMap.computeIfPresent(key) { _, existing ->
                val isThis = existing.ssid == ssid
                existing.copy(
                    connectionStatus = if (isThis) {
                        WifiConnectionStatus.CONNECTED
                    } else if (existing.connectionStatus == WifiConnectionStatus.CONNECTED) {
                        WifiConnectionStatus.DISCONNECTED
                    } else {
                        existing.connectionStatus
                    }
                )
            }
        }
        if (!networkMap.containsKey(ssid)) {
            networkMap[ssid] = JarvisWifiNetwork(
                ssid = ssid,
                bssid = info.bssid.orEmpty(),
                rssi = info.rssi,
                frequencyMhz = info.frequency,
                bandLabel = WifiClassifier.classifyFrequencyBand(info.frequency),
                signalPercent = WifiClassifier.calculateSignalPercent(info.rssi),
                isSavedProfile = savedSuggestions.containsKey(ssid),
                connectionStatus = WifiConnectionStatus.CONNECTED
            )
        }
        publishNetworks()
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun release() {
        disconnectDirectSpecifier()
        if (receiverRegistered) {
            try {
                appContext.unregisterReceiver(wifiReceiver)
            } catch (_: IllegalArgumentException) {
            }
            receiverRegistered = false
        }
        if (defaultWifiCallbackRegistered) {
            try {
                connectivityManager?.unregisterNetworkCallback(wifiMonitorCallback)
            } catch (_: Exception) {
            }
            defaultWifiCallbackRegistered = false
        }
    }

    @Suppress("DEPRECATION")
    private fun extractSsid(sr: ScanResult): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            WifiClassifier.sanitizeSsid(sr.wifiSsid?.toString())
        } else {
            WifiClassifier.sanitizeSsid(sr.SSID)
        }
    }

    private fun markNetworkState(
        ssid: String,
        isSaved: Boolean? = null,
        status: WifiConnectionStatus? = null
    ) {
        networkMap.compute(ssid) { _, existing ->
            (existing ?: JarvisWifiNetwork(ssid = ssid)).copy(
                isSavedProfile = isSaved ?: existing?.isSavedProfile ?: false,
                connectionStatus = status ?: existing?.connectionStatus ?: WifiConnectionStatus.DISCONNECTED
            )
        }
        publishNetworks()
    }

    private fun publishNetworks() {
        _networks.value = networkMap.values.sortedWith(
            compareByDescending<JarvisWifiNetwork> { it.connectionStatus == WifiConnectionStatus.CONNECTED }
                .thenByDescending { it.isSavedProfile }
                .thenByDescending { it.rssi }
                .thenBy { it.ssid.lowercase() }
        )
    }

    private fun registerReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                addAction(WifiManager.ACTION_WIFI_NETWORK_SUGGESTION_POST_CONNECTION)
            }
        }
        ContextCompat.registerReceiver(
            appContext,
            wifiReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
        receiverRegistered = true
    }

    private fun registerWifiMonitor() {
        if (defaultWifiCallbackRegistered) return
        val cm = connectivityManager ?: return
        try {
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            cm.registerNetworkCallback(request, wifiMonitorCallback)
            defaultWifiCallbackRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "Unable to register Wi-Fi network monitor", e)
        }
    }

    private fun addLog(message: String, isAlert: Boolean = false) {
        val entry = WifiLogEntry(
            id = logSeq.incrementAndGet(),
            timestampMillis = System.currentTimeMillis(),
            message = message,
            isAlert = isAlert
        )
        _logs.update { current -> (listOf(entry) + current).take(40) }
    }

    companion object {
        private const val TAG = "JarvisWifiManager"
    }
}
