package com.example.bluetooth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class JarvisBluetoothUiState(
    val adapterState: BluetoothAdapterState = BluetoothAdapterState.Disabled,
    val discoveryState: DiscoveryState = DiscoveryState.Idle,
    val devices: List<JarvisBluetoothDevice> = emptyList(),
    val logs: List<BluetoothLogEntry> = emptyList(),
    val selectedFilter: BluetoothUiFilter = BluetoothUiFilter.ALL,
    val hasPermissions: Boolean = false,
    val statusNotice: String? = null
) {
    val filteredDevices: List<JarvisBluetoothDevice>
        get() = when (selectedFilter) {
            BluetoothUiFilter.ALL -> devices
            BluetoothUiFilter.PAIRED -> devices.filter {
                it.bondStatus == BondStatus.BONDED || it.bondStatus == BondStatus.BONDING
            }
            BluetoothUiFilter.BLE -> devices.filter {
                it.transportType == TransportType.BLE || it.transportType == TransportType.DUAL
            }
            BluetoothUiFilter.CLASSIC -> devices.filter {
                it.transportType == TransportType.CLASSIC || it.transportType == TransportType.DUAL
            }
        }

    val pairedCount: Int
        get() = devices.count { it.bondStatus == BondStatus.BONDED }

    val connectedCount: Int
        get() = devices.count { it.connectionStatus == ConnectionStatus.CONNECTED }
}

class JarvisBluetoothViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = JarvisBluetoothManager(application)

    private val _selectedFilter = MutableStateFlow(BluetoothUiFilter.ALL)
    private val _hasPermissions = MutableStateFlow(manager.hasAllRequiredPermissions())
    private val _statusNotice = MutableStateFlow<String?>(null)

    val uiState: StateFlow<JarvisBluetoothUiState> = combine(
        manager.adapterState,
        manager.discoveryState,
        manager.devices,
        manager.logs,
        combine(_selectedFilter, _hasPermissions, _statusNotice) { filter, perms, notice ->
            Triple(filter, perms, notice)
        }
    ) { adapter, discovery, devices, logs, meta ->
        JarvisBluetoothUiState(
            adapterState = adapter,
            discoveryState = discovery,
            devices = devices,
            logs = logs,
            selectedFilter = meta.first,
            hasPermissions = meta.second,
            statusNotice = meta.third
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = JarvisBluetoothUiState(
            adapterState = manager.adapterState.value,
            hasPermissions = manager.hasAllRequiredPermissions()
        )
    )

    fun requiredPermissions(): Array<String> = manager.requiredPermissions()

    fun onPermissionsResult() {
        val granted = manager.hasAllRequiredPermissions()
        _hasPermissions.value = granted
        manager.refreshAdapterState()
        if (granted) {
            _statusNotice.value = null
            manager.loadBondedDevices()
        } else {
            _statusNotice.value = "Bluetooth permissions are required to scan and pair local devices."
        }
    }

    fun onAdapterStateChanged() {
        manager.refreshAdapterState()
        if (manager.isBluetoothEnabled() && manager.hasConnectPermission()) {
            manager.loadBondedDevices()
        }
    }

    fun selectFilter(filter: BluetoothUiFilter) {
        _selectedFilter.value = filter
    }

    fun toggleDiscovery() {
        if (manager.discoveryState.value is DiscoveryState.Scanning) {
            manager.stopDiscovery()
        } else {
            val result = manager.startDiscovery(includeBle = true)
            _statusNotice.value = result.exceptionOrNull()?.localizedMessage
        }
    }

    fun pairDevice(address: String) {
        val result = manager.pairDevice(address)
        _statusNotice.value = result.exceptionOrNull()?.localizedMessage
    }

    fun unpairDevice(address: String) {
        val result = manager.unpairDevice(address)
        _statusNotice.value = result.exceptionOrNull()?.localizedMessage
    }

    fun toggleConnection(device: JarvisBluetoothDevice) {
        if (device.connectionStatus == ConnectionStatus.CONNECTED ||
            device.connectionStatus == ConnectionStatus.CONNECTING
        ) {
            manager.disconnectDevice(device.address)
            _statusNotice.value = null
        } else {
            val result = manager.connectDevice(device.address)
            _statusNotice.value = result.exceptionOrNull()?.localizedMessage
        }
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
