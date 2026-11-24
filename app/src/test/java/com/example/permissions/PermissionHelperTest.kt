package com.example.permissions

import android.Manifest
import android.os.Build
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionHelperTest {

    @Test
    fun `android 12 includes BLUETOOTH_SCAN, BLUETOOTH_CONNECT, and ACCESS_FINE_LOCATION`() {
        val all = PermissionHelper.allWirelessPermissions(Build.VERSION_CODES.S)
        assertArrayEquals(
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            ),
            all
        )
    }

    @Test
    fun `android 13 plus includes NEARBY_WIFI_DEVICES alongside BT and fine location`() {
        val all = PermissionHelper.allWirelessPermissions(Build.VERSION_CODES.TIRAMISU)
        assertArrayEquals(
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ),
            all
        )
    }

    @Test
    fun `legacy android 11 requests ACCESS_FINE_LOCATION`() {
        val all = PermissionHelper.allWirelessPermissions(Build.VERSION_CODES.R)
        assertArrayEquals(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), all)
    }

    @Test
    fun `evaluateGrantResult summarizes granted and denied permissions`() {
        val partial = PermissionHelper.evaluateGrantResult(
            mapOf(
                Manifest.permission.BLUETOOTH_SCAN to true,
                Manifest.permission.BLUETOOTH_CONNECT to false,
                Manifest.permission.ACCESS_FINE_LOCATION to true
            )
        )
        assertFalse(partial.allGranted)
        assertEquals(2, partial.granted.size)
        assertEquals(listOf(Manifest.permission.BLUETOOTH_CONNECT), partial.denied)

        val full = PermissionHelper.evaluateGrantResult(
            mapOf(
                Manifest.permission.BLUETOOTH_SCAN to true,
                Manifest.permission.BLUETOOTH_CONNECT to true
            )
        )
        assertTrue(full.allGranted)
        assertTrue(full.denied.isEmpty())
    }

    @Test
    fun `camera and microphone bundle includes CAMERA and RECORD_AUDIO with friendly labels`() {
        val sensorPerms = PermissionHelper.cameraAndMicrophonePermissions()
        assertArrayEquals(
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
            sensorPerms
        )
        assertEquals("Camera (Live Vision)", PermissionHelper.describePermission(Manifest.permission.CAMERA))
        assertEquals("Microphone (Live Voice)", PermissionHelper.describePermission(Manifest.permission.RECORD_AUDIO))
    }
}
