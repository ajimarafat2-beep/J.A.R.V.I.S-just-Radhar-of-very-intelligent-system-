package com.example.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

data class PermissionGrantSummary(
    val allGranted: Boolean,
    val granted: List<String>,
    val denied: List<String>
)

/**
 * Centralized runtime permission helper for JARVIS AI Bluetooth and Wi-Fi scanning.
 * Handles API-level differences cleanly and ensures Android 12+ (API 31+) requests
 * BLUETOOTH_SCAN, BLUETOOTH_CONNECT, and ACCESS_FINE_LOCATION appropriately.
 */
object PermissionHelper {

    fun bluetoothPermissions(sdkInt: Int = Build.VERSION.SDK_INT): Array<String> {
        return if (sdkInt >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    fun wifiPermissions(sdkInt: Int = Build.VERSION.SDK_INT): Array<String> {
        return if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.NEARBY_WIFI_DEVICES
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    /**
     * Returns the full set of runtime permissions needed for both Bluetooth and Wi-Fi scanning.
     * On Android 12+ (API 31+), this includes BLUETOOTH_SCAN, BLUETOOTH_CONNECT, and
     * ACCESS_FINE_LOCATION (plus NEARBY_WIFI_DEVICES on Android 13+).
     */
    fun allWirelessPermissions(sdkInt: Int = Build.VERSION.SDK_INT): Array<String> {
        val combined = LinkedHashSet<String>()
        combined.addAll(bluetoothPermissions(sdkInt))
        combined.addAll(wifiPermissions(sdkInt))
        return combined.toTypedArray()
    }

    fun microphonePermissions(): Array<String> = arrayOf(Manifest.permission.RECORD_AUDIO)

    fun cameraPermissions(): Array<String> = arrayOf(Manifest.permission.CAMERA)

    fun cameraAndMicrophonePermissions(): Array<String> = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    fun hasMicrophonePermission(context: Context): Boolean =
        isPermissionGranted(context, Manifest.permission.RECORD_AUDIO)

    fun hasCameraPermission(context: Context): Boolean =
        isPermissionGranted(context, Manifest.permission.CAMERA)

    fun notificationPermissions(sdkInt: Int = Build.VERSION.SDK_INT): Array<String> {
        return if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyArray()
        }
    }

    fun hasNotificationPermission(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): Boolean {
        return if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            isPermissionGranted(context, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }
    }

    fun hasWriteSettingsPermission(context: Context): Boolean =
        Settings.System.canWrite(context)

    fun isPermissionGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun hasBluetoothScanPermission(
        context: Context,
        sdkInt: Int = Build.VERSION.SDK_INT
    ): Boolean {
        return if (sdkInt >= Build.VERSION_CODES.S) {
            isPermissionGranted(context, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            isPermissionGranted(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
                isPermissionGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    fun hasBluetoothConnectPermission(
        context: Context,
        sdkInt: Int = Build.VERSION.SDK_INT
    ): Boolean {
        return if (sdkInt >= Build.VERSION_CODES.S) {
            isPermissionGranted(context, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            true
        }
    }

    fun hasBluetoothPermissions(
        context: Context,
        sdkInt: Int = Build.VERSION.SDK_INT
    ): Boolean {
        return bluetoothPermissions(sdkInt).all { isPermissionGranted(context, it) }
    }

    fun hasWifiPermissions(
        context: Context,
        sdkInt: Int = Build.VERSION.SDK_INT
    ): Boolean {
        return if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            isPermissionGranted(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
                isPermissionGranted(context, Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            isPermissionGranted(context, Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    fun hasAllWirelessPermissions(
        context: Context,
        sdkInt: Int = Build.VERSION.SDK_INT
    ): Boolean {
        return allWirelessPermissions(sdkInt).all { isPermissionGranted(context, it) }
    }

    fun missingPermissions(
        context: Context,
        permissions: Array<String>
    ): List<String> {
        return permissions.filterNot { isPermissionGranted(context, it) }
    }

    fun missingWirelessPermissions(
        context: Context,
        sdkInt: Int = Build.VERSION.SDK_INT
    ): List<String> {
        return missingPermissions(context, allWirelessPermissions(sdkInt))
    }

    fun shouldShowRationale(activity: Activity, permissions: Array<String>): Boolean {
        return permissions.any { permission ->
            ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        }
    }

    fun evaluateGrantResult(results: Map<String, Boolean>): PermissionGrantSummary {
        val granted = results.filterValues { it }.keys.toList()
        val denied = results.filterValues { !it }.keys.toList()
        return PermissionGrantSummary(
            allGranted = denied.isEmpty() && granted.isNotEmpty(),
            granted = granted,
            denied = denied
        )
    }

    fun createAppSettingsIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun createWriteSettingsIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun createAccessibilitySettingsIntent(): Intent {
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun createDndPolicySettingsIntent(): Intent {
        return Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun describePermission(permission: String): String {
        return when (permission) {
            Manifest.permission.BLUETOOTH_SCAN -> "Bluetooth Scan"
            Manifest.permission.BLUETOOTH_CONNECT -> "Bluetooth Connect"
            Manifest.permission.ACCESS_FINE_LOCATION -> "Precise Location (Wi-Fi/BT Scan)"
            Manifest.permission.NEARBY_WIFI_DEVICES -> "Nearby Wi-Fi Devices"
            Manifest.permission.RECORD_AUDIO -> "Microphone (Live Voice)"
            Manifest.permission.CAMERA -> "Camera (Live Vision)"
            else -> permission.substringAfterLast('.')
        }
    }
}
