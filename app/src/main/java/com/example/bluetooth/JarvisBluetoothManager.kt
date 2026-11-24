package com.example.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Manages local Bluetooth Classic & BLE device discovery, pairing (bonding),
 * and connection state tracking for the JARVIS AI suite.
 */
class JarvisBluetoothManager(context: Context) {

    private val appContext: Context = context.applicationContext
    private val bluetoothManager: BluetoothManager? =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val logSeq = AtomicLong(0L)

    private val deviceMap = ConcurrentHashMap<String, JarvisBluetoothDevice>()
    private val activeGattConnections = ConcurrentHashMap<String, BluetoothGatt>()
    private val activeClassicSockets = ConcurrentHashMap<String, BluetoothSocket>()

    private var a2dpProxy: BluetoothProfile? = null
    private var headsetProxy: BluetoothProfile? = null
    private var scanTimeoutJob: Job? = null
    private var receiverRegistered = false
    private var bleScanningActive = false
    private var classicDiscoveryActive = false

    private val _adapterState = MutableStateFlow<BluetoothAdapterState>(BluetoothAdapterState.Disabled)
    val adapterState: StateFlow<BluetoothAdapterState> = _adapterState.asStateFlow()

    private val _discoveryState = MutableStateFlow<DiscoveryState>(DiscoveryState.Idle)
    val discoveryState: StateFlow<DiscoveryState> = _discoveryState.asStateFlow()

    private val _devices = MutableStateFlow<List<JarvisBluetoothDevice>>(emptyList())
    val devices: StateFlow<List<JarvisBluetoothDevice>> = _devices.asStateFlow()

    private val _logs = MutableStateFlow<List<BluetoothLogEntry>>(emptyList())
    val logs: StateFlow<List<BluetoothLogEntry>> = _logs.asStateFlow()

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            when (profile) {
                BluetoothProfile.A2DP -> a2dpProxy = proxy
                BluetoothProfile.HEADSET -> headsetProxy = proxy
            }
            syncConnectedProfileDevices(proxy)
        }

        override fun onServiceDisconnected(profile: Int) {
            when (profile) {
                BluetoothProfile.A2DP -> a2dpProxy = null
                BluetoothProfile.HEADSET -> headsetProxy = null
            }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            when (action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    val mapped = BluetoothClassifier.mapAdapterState(state)
                    _adapterState.value = mapped
                    addLog("Bluetooth adapter state: $mapped")
                    if (mapped is BluetoothAdapterState.Enabled) {
                        loadBondedDevices()
                        initProfileProxies()
                    } else if (mapped is BluetoothAdapterState.Disabled) {
                        stopDiscovery()
                        markAllDisconnected()
                    }
                }

                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    classicDiscoveryActive = true
                    updateDiscoveryState()
                    addLog("Classic Bluetooth discovery started.")
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    classicDiscoveryActive = false
                    updateDiscoveryState()
                    addLog("Classic Bluetooth discovery finished.")
                }

                BluetoothDevice.ACTION_FOUND -> {
                    val device = IntentCompat.getParcelableExtra(
                        intent,
                        BluetoothDevice.EXTRA_DEVICE,
                        BluetoothDevice::class.java
                    ) ?: return
                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                        .takeIf { it != Short.MIN_VALUE }?.toInt()
                    upsertBluetoothDevice(device, rssi, TransportType.CLASSIC)
                }

                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device = IntentCompat.getParcelableExtra(
                        intent,
                        BluetoothDevice.EXTRA_DEVICE,
                        BluetoothDevice::class.java
                    ) ?: return
                    val bondState = intent.getIntExtra(
                        BluetoothDevice.EXTRA_BOND_STATE,
                        BluetoothDevice.BOND_NONE
                    )
                    val prevState = intent.getIntExtra(
                        BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE,
                        BluetoothDevice.BOND_NONE
                    )
                    val mappedBond = BluetoothClassifier.mapBondState(bondState)
                    upsertBluetoothDevice(device, null, TransportType.CLASSIC, overrideBond = mappedBond)
                    val label = safeDeviceName(device)
                    when (mappedBond) {
                        BondStatus.BONDED -> addLog("Paired with $label (${device.address}).")
                        BondStatus.BONDING -> addLog("Pairing in progress with $label...")
                        BondStatus.NONE -> {
                            val failed = prevState == BluetoothDevice.BOND_BONDING
                            addLog(
                                if (failed) "Pairing cancelled or failed for $label."
                                else "Unpaired $label.",
                                isAlert = failed
                            )
                        }
                    }
                }

                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val device = IntentCompat.getParcelableExtra(
                        intent,
                        BluetoothDevice.EXTRA_DEVICE,
                        BluetoothDevice::class.java
                    ) ?: return
                    upsertBluetoothDevice(
                        device,
                        null,
                        TransportType.CLASSIC,
                        overrideConnection = ConnectionStatus.CONNECTED
                    )
                    addLog("ACL connected: ${safeDeviceName(device)}.")
                }

                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device = IntentCompat.getParcelableExtra(
                        intent,
                        BluetoothDevice.EXTRA_DEVICE,
                        BluetoothDevice::class.java
                    ) ?: return
                    upsertBluetoothDevice(
                        device,
                        null,
                        TransportType.CLASSIC,
                        overrideConnection = ConnectionStatus.DISCONNECTED
                    )
                    addLog("ACL disconnected: ${safeDeviceName(device)}.")
                }
            }
        }
    }

    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            upsertBluetoothDevice(device, result.rssi, TransportType.BLE)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { result ->
                result.device?.let { upsertBluetoothDevice(it, result.rssi, TransportType.BLE) }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            bleScanningActive = false
            updateDiscoveryState()
            _discoveryState.value = DiscoveryState.Error("BLE scan failed (code $errorCode)")
            addLog("BLE scan failed with error code $errorCode.", isAlert = true)
        }
    }

    init {
        registerSystemReceiver()
        refreshAdapterState()
        if (isBluetoothEnabled() && hasConnectPermission()) {
            loadBondedDevices()
            initProfileProxies()
        }
    }

    fun requiredPermissions(): Array<String> =
        com.example.permissions.PermissionHelper.bluetoothPermissions()

    fun hasScanPermission(): Boolean =
        com.example.permissions.PermissionHelper.hasBluetoothScanPermission(appContext)

    fun hasConnectPermission(): Boolean =
        com.example.permissions.PermissionHelper.hasBluetoothConnectPermission(appContext)

    fun hasAllRequiredPermissions(): Boolean =
        com.example.permissions.PermissionHelper.hasBluetoothPermissions(appContext)

    fun isBluetoothSupported(): Boolean = bluetoothAdapter != null

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    fun refreshAdapterState() {
        val adapter = bluetoothAdapter
        _adapterState.value = when {
            adapter == null -> BluetoothAdapterState.Unsupported
            adapter.isEnabled -> BluetoothAdapterState.Enabled
            else -> BluetoothAdapterState.Disabled
        }
    }

    @SuppressLint("MissingPermission")
    fun loadBondedDevices() {
        val adapter = bluetoothAdapter ?: return
        if (!hasConnectPermission()) return
        try {
            val bonded = adapter.bondedDevices.orEmpty()
            bonded.forEach { device ->
                val inferredTransport = when (device.type) {
                    BluetoothDevice.DEVICE_TYPE_LE -> TransportType.BLE
                    BluetoothDevice.DEVICE_TYPE_DUAL -> TransportType.DUAL
                    else -> TransportType.CLASSIC
                }
                upsertBluetoothDevice(
                    device = device,
                    rssi = null,
                    transport = inferredTransport,
                    overrideBond = BondStatus.BONDED
                )
            }
            if (bonded.isNotEmpty()) {
                addLog("Loaded ${bonded.size} paired device(s).")
            }
        } catch (se: SecurityException) {
            Log.e(TAG, "Permission error loading bonded devices", se)
            addLog("Missing permission to read paired devices.", isAlert = true)
        }
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery(includeBle: Boolean = true, timeoutMs: Long = DEFAULT_SCAN_TIMEOUT_MS): Result<Unit> {
        val adapter = bluetoothAdapter
            ?: return Result.failure(IllegalStateException("Bluetooth hardware is not available."))

        if (!adapter.isEnabled) {
            _adapterState.value = BluetoothAdapterState.Disabled
            return Result.failure(IllegalStateException("Bluetooth is turned off."))
        }

        if (!hasAllRequiredPermissions()) {
            return Result.failure(SecurityException("Bluetooth scan/connect permissions are required."))
        }

        stopDiscovery()
        loadBondedDevices()
        initProfileProxies()

        var startedAny = false

        try {
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
            val classicStarted = adapter.startDiscovery()
            classicDiscoveryActive = classicStarted
            startedAny = startedAny || classicStarted
        } catch (se: SecurityException) {
            Log.e(TAG, "Classic discovery permission error", se)
            addLog("Classic scan denied by permission.", isAlert = true)
        }

        if (includeBle) {
            val scanner: BluetoothLeScanner? = adapter.bluetoothLeScanner
            if (scanner != null) {
                try {
                    val settings = ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build()
                    scanner.startScan(null, settings, bleScanCallback)
                    bleScanningActive = true
                    startedAny = true
                    addLog("BLE low-latency scan started.")
                } catch (se: SecurityException) {
                    Log.e(TAG, "BLE scan permission error", se)
                    addLog("BLE scan denied by permission.", isAlert = true)
                } catch (e: Exception) {
                    Log.e(TAG, "BLE scan failed to start", e)
                    addLog("BLE scan unavailable: ${e.localizedMessage}", isAlert = true)
                }
            }
        }

        updateDiscoveryState()

        if (!startedAny) {
            val msg = "Unable to start Bluetooth discovery."
            _discoveryState.value = DiscoveryState.Error(msg)
            return Result.failure(IllegalStateException(msg))
        }

        scanTimeoutJob?.cancel()
        scanTimeoutJob = scope.launch {
            delay(timeoutMs)
            stopDiscovery()
            addLog("Scan window completed (${timeoutMs / 1000}s).")
        }

        return Result.success(Unit)
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null

        val adapter = bluetoothAdapter
        if (adapter != null && hasScanPermission()) {
            try {
                if (adapter.isDiscovering) {
                    adapter.cancelDiscovery()
                }
            } catch (se: SecurityException) {
                Log.w(TAG, "Unable to cancel classic discovery", se)
            }

            if (bleScanningActive) {
                try {
                    adapter.bluetoothLeScanner?.stopScan(bleScanCallback)
                } catch (e: Exception) {
                    Log.w(TAG, "Unable to stop BLE scan", e)
                }
            }
        }

        classicDiscoveryActive = false
        bleScanningActive = false
        updateDiscoveryState()
    }

    @SuppressLint("MissingPermission")
    fun pairDevice(address: String): Result<Unit> {
        val adapter = bluetoothAdapter
            ?: return Result.failure(IllegalStateException("Bluetooth hardware unavailable."))
        if (!hasConnectPermission()) {
            return Result.failure(SecurityException("BLUETOOTH_CONNECT permission required."))
        }

        // Stop active discovery before initiating bonding as recommended by Android Bluetooth docs
        stopDiscovery()

        return try {
            val device = adapter.getRemoteDevice(address)
            if (device.bondState == BluetoothDevice.BOND_BONDED) {
                addLog("${safeDeviceName(device)} is already paired.")
                return Result.success(Unit)
            }
            val initiated = device.createBond()
            if (initiated) {
                upsertBluetoothDevice(
                    device,
                    null,
                    TransportType.CLASSIC,
                    overrideBond = BondStatus.BONDING
                )
                addLog("Initiated pairing with ${safeDeviceName(device)}...")
                Result.success(Unit)
            } else {
                val msg = "System rejected pairing request for ${safeDeviceName(device)}."
                addLog(msg, isAlert = true)
                Result.failure(IllegalStateException(msg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Pairing failed for $address", e)
            addLog("Pairing error: ${e.localizedMessage}", isAlert = true)
            Result.failure(e)
        }
    }

    @SuppressLint("MissingPermission")
    fun unpairDevice(address: String): Result<Unit> {
        val adapter = bluetoothAdapter
            ?: return Result.failure(IllegalStateException("Bluetooth hardware unavailable."))
        if (!hasConnectPermission()) {
            return Result.failure(SecurityException("BLUETOOTH_CONNECT permission required."))
        }

        disconnectDevice(address)

        return try {
            val device = adapter.getRemoteDevice(address)
            val removeBondMethod = device.javaClass.getMethod("removeBond")
            val removed = removeBondMethod.invoke(device) as? Boolean ?: false
            if (removed) {
                addLog("Unpairing ${safeDeviceName(device)}...")
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("Unpair via System Bluetooth Settings."))
            }
        } catch (e: Exception) {
            Log.w(TAG, "removeBond restricted on this OS version", e)
            Result.failure(IllegalStateException("Please unpair via System Bluetooth Settings."))
        }
    }

    @SuppressLint("MissingPermission")
    fun connectDevice(address: String): Result<Unit> {
        val adapter = bluetoothAdapter
            ?: return Result.failure(IllegalStateException("Bluetooth hardware unavailable."))
        if (!hasConnectPermission()) {
            return Result.failure(SecurityException("BLUETOOTH_CONNECT permission required."))
        }

        stopDiscovery()

        return try {
            val device = adapter.getRemoteDevice(address)
            val currentEntry = deviceMap[address]
            val useBleGatt = currentEntry?.transportType == TransportType.BLE ||
                device.type == BluetoothDevice.DEVICE_TYPE_LE

            upsertBluetoothDevice(
                device = device,
                rssi = null,
                transport = currentEntry?.transportType ?: TransportType.CLASSIC,
                overrideConnection = ConnectionStatus.CONNECTING
            )

            if (useBleGatt) {
                connectBleGatt(device)
            } else {
                connectClassicRfcomm(device)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Connect failed for $address", e)
            updateConnectionStatus(address, ConnectionStatus.FAILED)
            addLog("Connection error for $address: ${e.localizedMessage}", isAlert = true)
            Result.failure(e)
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnectDevice(address: String) {
        val gatt = activeGattConnections.remove(address)
        if (gatt != null) {
            updateConnectionStatus(address, ConnectionStatus.DISCONNECTING)
            try {
                gatt.disconnect()
                gatt.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing GATT for $address", e)
            }
        }

        val socket = activeClassicSockets.remove(address)
        if (socket != null) {
            try {
                socket.close()
            } catch (e: IOException) {
                Log.w(TAG, "Error closing Classic socket for $address", e)
            }
        }

        updateConnectionStatus(address, ConnectionStatus.DISCONNECTED)
        addLog("Disconnected $address.")
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    @SuppressLint("MissingPermission")
    fun release() {
        stopDiscovery()
        if (receiverRegistered) {
            try {
                appContext.unregisterReceiver(receiver)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Receiver already unregistered", e)
            }
            receiverRegistered = false
        }

        activeGattConnections.values.forEach { gatt ->
            try {
                gatt.disconnect()
                gatt.close()
            } catch (_: Exception) {
            }
        }
        activeGattConnections.clear()

        activeClassicSockets.values.forEach { socket ->
            try {
                socket.close()
            } catch (_: IOException) {
            }
        }
        activeClassicSockets.clear()

        val adapter = bluetoothAdapter
        if (adapter != null) {
            a2dpProxy?.let { adapter.closeProfileProxy(BluetoothProfile.A2DP, it) }
            headsetProxy?.let { adapter.closeProfileProxy(BluetoothProfile.HEADSET, it) }
        }
        a2dpProxy = null
        headsetProxy = null
        scope.cancel()
    }

    @SuppressLint("MissingPermission")
    private fun connectBleGatt(device: BluetoothDevice) {
        activeGattConnections.remove(device.address)?.let { oldGatt ->
            try {
                oldGatt.close()
            } catch (_: Exception) {
            }
        }

        addLog("Connecting BLE GATT to ${safeDeviceName(device)}...")
        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                val addr = gatt.device.address
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    activeGattConnections.remove(addr)
                    try {
                        gatt.close()
                    } catch (_: Exception) {
                    }
                    updateConnectionStatus(addr, ConnectionStatus.FAILED)
                    addLog("GATT connection failed ($status) for $addr.", isAlert = true)
                    return
                }

                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        updateConnectionStatus(addr, ConnectionStatus.CONNECTED)
                        addLog("BLE GATT connected to ${safeDeviceName(gatt.device)}. Discovering services...")
                        try {
                            gatt.discoverServices()
                        } catch (se: SecurityException) {
                            Log.w(TAG, "Cannot discover GATT services", se)
                        }
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {
                        activeGattConnections.remove(addr)
                        try {
                            gatt.close()
                        } catch (_: Exception) {
                        }
                        updateConnectionStatus(addr, ConnectionStatus.DISCONNECTED)
                        addLog("BLE GATT disconnected from ${safeDeviceName(gatt.device)}.")
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val count = gatt.services.size
                    val addr = gatt.device.address
                    deviceMap.computeIfPresent(addr) { _, existing ->
                        existing.copy(servicesCount = count)
                    }
                    publishDevices()
                    addLog("Discovered $count GATT service(s) on ${safeDeviceName(gatt.device)}.")
                }
            }
        }

        val gatt = device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        if (gatt != null) {
            activeGattConnections[device.address] = gatt
        } else {
            updateConnectionStatus(device.address, ConnectionStatus.FAILED)
            addLog("Failed to create GATT client for ${device.address}.", isAlert = true)
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectClassicRfcomm(device: BluetoothDevice) {
        val name = safeDeviceName(device)
        addLog("Connecting Classic RFCOMM link to $name...")
        scope.launch(Dispatchers.IO) {
            var socket: BluetoothSocket? = null
            try {
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket.connect()
                activeClassicSockets[device.address] = socket
                updateConnectionStatus(device.address, ConnectionStatus.CONNECTED)
                addLog("Connected Classic SPP channel to $name.")
            } catch (e: Exception) {
                try {
                    socket?.close()
                } catch (_: IOException) {
                }
                // Check if device is already connected via A2DP / Headset system profile
                val profileConnected = isProfileConnected(device.address)
                if (profileConnected) {
                    updateConnectionStatus(device.address, ConnectionStatus.CONNECTED)
                    addLog("$name is active via system Audio/Headset profile.")
                } else {
                    updateConnectionStatus(device.address, ConnectionStatus.FAILED)
                    addLog(
                        "RFCOMM unavailable for $name (audio/HID devices connect via System BT Settings).",
                        isAlert = true
                    )
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun initProfileProxies() {
        val adapter = bluetoothAdapter ?: return
        if (!hasConnectPermission()) return
        try {
            if (a2dpProxy == null) {
                adapter.getProfileProxy(appContext, profileListener, BluetoothProfile.A2DP)
            }
            if (headsetProxy == null) {
                adapter.getProfileProxy(appContext, profileListener, BluetoothProfile.HEADSET)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Profile proxy init error", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun syncConnectedProfileDevices(proxy: BluetoothProfile) {
        if (!hasConnectPermission()) return
        try {
            proxy.connectedDevices.forEach { device ->
                upsertBluetoothDevice(
                    device = device,
                    rssi = null,
                    transport = TransportType.CLASSIC,
                    overrideBond = BondStatus.BONDED,
                    overrideConnection = ConnectionStatus.CONNECTED
                )
            }
        } catch (se: SecurityException) {
            Log.w(TAG, "Unable to query connected profile devices", se)
        }
    }

    @SuppressLint("MissingPermission")
    private fun isProfileConnected(address: String): Boolean {
        if (!hasConnectPermission()) return false
        return try {
            val inA2dp = a2dpProxy?.connectedDevices?.any { it.address == address } == true
            val inHeadset = headsetProxy?.connectedDevices?.any { it.address == address } == true
            inA2dp || inHeadset
        } catch (_: SecurityException) {
            false
        }
    }

    private fun registerSystemReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
        receiverRegistered = true
    }

    @SuppressLint("MissingPermission")
    private fun upsertBluetoothDevice(
        device: BluetoothDevice,
        rssi: Int?,
        transport: TransportType,
        overrideBond: BondStatus? = null,
        overrideConnection: ConnectionStatus? = null
    ) {
        val address = device.address ?: return
        val resolvedName = safeDeviceName(device)
        val majorClass = if (hasConnectPermission()) {
            try {
                device.bluetoothClass?.majorDeviceClass ?: BluetoothClass.Device.Major.MISC
            } catch (_: SecurityException) {
                BluetoothClass.Device.Major.MISC
            }
        } else {
            BluetoothClass.Device.Major.MISC
        }

        val bondStatus = overrideBond ?: if (hasConnectPermission()) {
            try {
                BluetoothClassifier.mapBondState(device.bondState)
            } catch (_: SecurityException) {
                BondStatus.NONE
            }
        } else {
            BondStatus.NONE
        }

        deviceMap.compute(address) { _, existing ->
            val mergedTransport = BluetoothClassifier.mergeTransportType(existing?.transportType, transport)
            val bestName = if (resolvedName != UNKNOWN_DEVICE_NAME) {
                resolvedName
            } else {
                existing?.name ?: UNKNOWN_DEVICE_NAME
            }
            val connectionStatus = overrideConnection
                ?: existing?.connectionStatus
                ?: if (isProfileConnected(address)) ConnectionStatus.CONNECTED else ConnectionStatus.DISCONNECTED

            JarvisBluetoothDevice(
                address = address,
                name = bestName,
                rssi = rssi ?: existing?.rssi,
                majorDeviceClass = if (majorClass != BluetoothClass.Device.Major.MISC) {
                    majorClass
                } else {
                    existing?.majorDeviceClass ?: majorClass
                },
                category = BluetoothClassifier.classifyMajorDeviceClass(
                    if (majorClass != BluetoothClass.Device.Major.MISC) majorClass
                    else existing?.majorDeviceClass ?: majorClass
                ),
                transportType = mergedTransport,
                bondStatus = bondStatus,
                connectionStatus = connectionStatus,
                servicesCount = existing?.servicesCount ?: 0,
                lastSeenMillis = System.currentTimeMillis()
            )
        }
        publishDevices()
    }

    private fun updateConnectionStatus(address: String, status: ConnectionStatus) {
        deviceMap.computeIfPresent(address) { _, existing ->
            existing.copy(connectionStatus = status, lastSeenMillis = System.currentTimeMillis())
        }
        publishDevices()
    }

    private fun markAllDisconnected() {
        deviceMap.keys.forEach { addr ->
            deviceMap.computeIfPresent(addr) { _, existing ->
                existing.copy(connectionStatus = ConnectionStatus.DISCONNECTED)
            }
        }
        publishDevices()
    }

    private fun publishDevices() {
        _devices.value = deviceMap.values.sortedWith(
            compareByDescending<JarvisBluetoothDevice> { it.connectionStatus == ConnectionStatus.CONNECTED }
                .thenByDescending { it.bondStatus == BondStatus.BONDED }
                .thenByDescending { it.rssi ?: Int.MIN_VALUE }
                .thenBy { it.name.lowercase() }
        )
    }

    private fun updateDiscoveryState() {
        _discoveryState.value = if (classicDiscoveryActive || bleScanningActive) {
            DiscoveryState.Scanning(
                classicActive = classicDiscoveryActive,
                bleActive = bleScanningActive
            )
        } else {
            DiscoveryState.Idle
        }
    }

    @SuppressLint("MissingPermission")
    private fun safeDeviceName(device: BluetoothDevice): String {
        if (!hasConnectPermission()) return UNKNOWN_DEVICE_NAME
        return try {
            device.name?.takeIf { it.isNotBlank() } ?: UNKNOWN_DEVICE_NAME
        } catch (_: SecurityException) {
            UNKNOWN_DEVICE_NAME
        }
    }

    private fun addLog(message: String, isAlert: Boolean = false) {
        val entry = BluetoothLogEntry(
            id = logSeq.incrementAndGet(),
            timestampMillis = System.currentTimeMillis(),
            message = message,
            isAlert = isAlert
        )
        _logs.update { current -> (listOf(entry) + current).take(40) }
    }

    companion object {
        private const val TAG = "JarvisBluetoothManager"
        private const val DEFAULT_SCAN_TIMEOUT_MS = 15_000L
        private const val UNKNOWN_DEVICE_NAME = "Unnamed Peripheral"
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
