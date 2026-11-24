package com.example.wifi

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class JarvisWifiUiState(
    val radioState: WifiRadioState = WifiRadioState.Disabled,
    val scanState: WifiScanState = WifiScanState.Idle,
    val networks: List<JarvisWifiNetwork> = emptyList(),
    val activeLink: ActiveWifiLinkInfo? = null,
    val logs: List<WifiLogEntry> = emptyList(),
    val selectedFilter: WifiUiFilter = WifiUiFilter.ALL,
    val hasPermissions: Boolean = false,
    val statusNotice: String? = null
) {
    val filteredNetworks: List<JarvisWifiNetwork>
        get() = when (selectedFilter) {
            WifiUiFilter.ALL -> networks
            WifiUiFilter.SAVED -> networks.filter { it.isSavedProfile || it.connectionStatus == WifiConnectionStatus.CONNECTED }
            WifiUiFilter.SECURED -> networks.filter { !it.isSecurityWarning }
            WifiUiFilter.OPEN -> networks.filter { it.isSecurityWarning }
            WifiUiFilter.BAND_5GHZ -> networks.filter { it.bandLabel == "5 GHz" || it.bandLabel == "6 GHz" }
        }

    val savedCount: Int
        get() = networks.count { it.isSavedProfile }
}

class JarvisWifiViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = JarvisWifiManager(application)

    private val _selectedFilter = MutableStateFlow(WifiUiFilter.ALL)
    private val _hasPermissions = MutableStateFlow(manager.hasScanPermission())
    private val _statusNotice = MutableStateFlow<String?>(null)

    val uiState: StateFlow<JarvisWifiUiState> = combine(
        manager.radioState,
        manager.scanState,
        manager.networks,
        manager.activeLink,
        manager.logs,
        combine(_selectedFilter, _hasPermissions, _statusNotice) { f, p, n -> Triple(f, p, n) }
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val radio = values[0] as WifiRadioState
        val scan = values[1] as WifiScanState
        @Suppress("UNCHECKED_CAST")
        val nets = values[2] as List<JarvisWifiNetwork>
        val link = values[3] as ActiveWifiLinkInfo?
        @Suppress("UNCHECKED_CAST")
        val logs = values[4] as List<WifiLogEntry>
        @Suppress("UNCHECKED_CAST")
        val meta = values[5] as Triple<WifiUiFilter, Boolean, String?>
        JarvisWifiUiState(
            radioState = radio,
            scanState = scan,
            networks = nets,
            activeLink = link,
            logs = logs,
            selectedFilter = meta.first,
            hasPermissions = meta.second,
            statusNotice = meta.third
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = JarvisWifiUiState(
            radioState = manager.radioState.value,
            hasPermissions = manager.hasScanPermission()
        )
    )

    fun requiredPermissions(): Array<String> = manager.requiredPermissions()

    fun onPermissionsResult() {
        val granted = manager.hasScanPermission()
        _hasPermissions.value = granted
        manager.refreshRadioState()
        manager.refreshActiveConnection()
        if (granted) {
            _statusNotice.value = null
            manager.startScan()
        } else {
            _statusNotice.value = "Wi-Fi scan permission is needed to detect nearby networks."
        }
    }

    fun onRadioPanelClosed() {
        manager.refreshRadioState()
        manager.refreshActiveConnection()
        if (manager.isWifiEnabled() && manager.hasScanPermission()) {
            manager.startScan()
        }
    }

    fun selectFilter(filter: WifiUiFilter) {
        _selectedFilter.value = filter
    }

    fun startScan() {
        val result = manager.startScan()
        _statusNotice.value = result.exceptionOrNull()?.localizedMessage
    }

    fun connect(
        ssid: String,
        passphrase: String,
        securityType: WifiSecurityType,
        mode: WifiConnectionMode
    ) {
        val result = manager.connectToNetwork(ssid, passphrase, securityType, mode)
        _statusNotice.value = result.fold(
            onSuccess = { it },
            onFailure = { it.localizedMessage ?: "Failed to connect to $ssid." }
        )
    }

    fun forgetProfile(ssid: String) {
        manager.removeSavedSuggestion(ssid)
        _statusNotice.value = "Removed saved Wi-Fi profile '$ssid'."
    }

    fun disconnectLocalLink() {
        manager.disconnectDirectSpecifier()
        _statusNotice.value = "Disconnected direct Wi-Fi link."
    }

    fun clearLogs() {
        manager.clearLogs()
    }

    fun dismissNotice() {
        _statusNotice.value = null
    }

    override fun onCleared() {
        manager.release()
        super.onCleared()
    }
}
