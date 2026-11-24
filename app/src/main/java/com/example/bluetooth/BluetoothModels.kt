package com.example.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.os.Build

enum class BondStatus {
    NONE,
    BONDING,
    BONDED
}

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    FAILED
}

enum class TransportType {
    CLASSIC,
    BLE,
    DUAL
}

enum class DeviceCategory(val label: String) {
    AUDIO("Audio / Headset"),
    PHONE("Phone"),
    COMPUTER("Computer"),
    WEARABLE("Wearable"),
    PERIPHERAL("Peripheral / HID"),
    IOT_HEALTH("Smart / Health"),
    UNKNOWN("Bluetooth Device")
}

enum class BluetoothUiFilter(val label: String) {
    ALL("All"),
    PAIRED("Paired"),
    BLE("BLE"),
    CLASSIC("Classic")
}

data class JarvisBluetoothDevice(
    val address: String,
    val name: String,
    val rssi: Int? = null,
    val majorDeviceClass: Int = BluetoothClass.Device.Major.MISC,
    val category: DeviceCategory = DeviceCategory.UNKNOWN,
    val transportType: TransportType = TransportType.CLASSIC,
    val bondStatus: BondStatus = BondStatus.NONE,
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val servicesCount: Int = 0,
    val lastSeenMillis: Long = System.currentTimeMillis()
)

sealed interface BluetoothAdapterState {
    data object Unsupported : BluetoothAdapterState
    data object Disabled : BluetoothAdapterState
    data object TurningOn : BluetoothAdapterState
    data object Enabled : BluetoothAdapterState
    data object TurningOff : BluetoothAdapterState
}

sealed interface DiscoveryState {
    data object Idle : DiscoveryState
    data class Scanning(
        val classicActive: Boolean,
        val bleActive: Boolean
    ) : DiscoveryState
    data class Error(val message: String) : DiscoveryState
}

data class BluetoothLogEntry(
    val id: Long,
    val timestampMillis: Long,
    val message: String,
    val isAlert: Boolean = false
)

object BluetoothClassifier {

    fun requiredBluetoothPermissions(sdkInt: Int = Build.VERSION.SDK_INT): Array<String> =
        com.example.permissions.PermissionHelper.bluetoothPermissions(sdkInt)

    fun classifyMajorDeviceClass(majorClass: Int): DeviceCategory {
        return when (majorClass) {
            BluetoothClass.Device.Major.AUDIO_VIDEO -> DeviceCategory.AUDIO
            BluetoothClass.Device.Major.PHONE -> DeviceCategory.PHONE
            BluetoothClass.Device.Major.COMPUTER -> DeviceCategory.COMPUTER
            BluetoothClass.Device.Major.WEARABLE -> DeviceCategory.WEARABLE
            BluetoothClass.Device.Major.PERIPHERAL -> DeviceCategory.PERIPHERAL
            BluetoothClass.Device.Major.HEALTH,
            BluetoothClass.Device.Major.TOY,
            BluetoothClass.Device.Major.IMAGING -> DeviceCategory.IOT_HEALTH
            else -> DeviceCategory.UNKNOWN
        }
    }

    fun mapBondState(bondState: Int): BondStatus {
        return when (bondState) {
            BluetoothDevice.BOND_BONDED -> BondStatus.BONDED
            BluetoothDevice.BOND_BONDING -> BondStatus.BONDING
            else -> BondStatus.NONE
        }
    }

    fun mapAdapterState(state: Int): BluetoothAdapterState {
        return when (state) {
            BluetoothAdapter.STATE_ON -> BluetoothAdapterState.Enabled
            BluetoothAdapter.STATE_TURNING_ON -> BluetoothAdapterState.TurningOn
            BluetoothAdapter.STATE_TURNING_OFF -> BluetoothAdapterState.TurningOff
            else -> BluetoothAdapterState.Disabled
        }
    }

    fun mergeTransportType(existing: TransportType?, incoming: TransportType): TransportType {
        if (existing == null || existing == incoming) return incoming
        return TransportType.DUAL
    }
}
