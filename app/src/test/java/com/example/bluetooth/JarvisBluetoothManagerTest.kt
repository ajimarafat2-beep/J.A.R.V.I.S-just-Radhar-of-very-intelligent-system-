package com.example.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.os.Build
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class JarvisBluetoothManagerTest {

    @Test
    fun `required permissions match API level`() {
        val modern = BluetoothClassifier.requiredBluetoothPermissions(Build.VERSION_CODES.S)
        assertArrayEquals(
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT),
            modern
        )

        val legacy = BluetoothClassifier.requiredBluetoothPermissions(Build.VERSION_CODES.R)
        assertArrayEquals(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), legacy)
    }

    @Test
    fun `classify major device classes accurately`() {
        assertEquals(
            DeviceCategory.AUDIO,
            BluetoothClassifier.classifyMajorDeviceClass(BluetoothClass.Device.Major.AUDIO_VIDEO)
        )
        assertEquals(
            DeviceCategory.WEARABLE,
            BluetoothClassifier.classifyMajorDeviceClass(BluetoothClass.Device.Major.WEARABLE)
        )
        assertEquals(
            DeviceCategory.COMPUTER,
            BluetoothClassifier.classifyMajorDeviceClass(BluetoothClass.Device.Major.COMPUTER)
        )
        assertEquals(
            DeviceCategory.UNKNOWN,
            BluetoothClassifier.classifyMajorDeviceClass(BluetoothClass.Device.Major.MISC)
        )
    }

    @Test
    fun `map bond and adapter states accurately`() {
        assertEquals(BondStatus.BONDED, BluetoothClassifier.mapBondState(BluetoothDevice.BOND_BONDED))
        assertEquals(BondStatus.BONDING, BluetoothClassifier.mapBondState(BluetoothDevice.BOND_BONDING))
        assertEquals(BondStatus.NONE, BluetoothClassifier.mapBondState(BluetoothDevice.BOND_NONE))

        assertEquals(
            BluetoothAdapterState.Enabled,
            BluetoothClassifier.mapAdapterState(BluetoothAdapter.STATE_ON)
        )
        assertEquals(
            BluetoothAdapterState.Disabled,
            BluetoothClassifier.mapAdapterState(BluetoothAdapter.STATE_OFF)
        )
    }

    @Test
    fun `merge transport types resolves dual mode`() {
        assertEquals(
            TransportType.DUAL,
            BluetoothClassifier.mergeTransportType(TransportType.CLASSIC, TransportType.BLE)
        )
        assertEquals(
            TransportType.BLE,
            BluetoothClassifier.mergeTransportType(TransportType.BLE, TransportType.BLE)
        )
    }
}
