package com.example.control

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import com.example.permissions.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

enum class PhoneRingerMode(val label: String, val androidMode: Int) {
    NORMAL("Ring", AudioManager.RINGER_MODE_NORMAL),
    VIBRATE("Vibrate", AudioManager.RINGER_MODE_VIBRATE),
    SILENT("Silent", AudioManager.RINGER_MODE_SILENT)
}

data class PhoneControlSnapshot(
    val cameraGranted: Boolean = false,
    val micGranted: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val writeSettingsGranted: Boolean = false,
    val dndPolicyGranted: Boolean = false,
    val torchOn: Boolean = false,
    val hasFlashHardware: Boolean = false,
    val brightnessPercent: Int = 50,
    val autoBrightness: Boolean = false,
    val mediaVolumePercent: Int = 60,
    val ringVolumePercent: Int = 60,
    val alarmVolumePercent: Int = 75,
    val ringerMode: PhoneRingerMode = PhoneRingerMode.NORMAL,
    val voiceAccessOverlayActive: Boolean = false,
    val voiceAccessTargetCount: Int = 0,
    val lastControlStatus: String? = null
)

/**
 * Full Phone Control Engine combining:
 * - Real hardware sensors & radios (Camera torch, Audio streams, Ringer mode, Screen brightness, Media keys)
 * - System-wide navigation & gesture automation via [JarvisAccessibilityService]
 * - Quick system intents (Camera, Video, Alarms, Timers, Dialer, SMS, Display, Internet, Hotspot, Battery)
 */
class JarvisPhoneControlManager(context: Context) {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val cameraManager = appContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var torchCameraId: String? = null

    private val _state = MutableStateFlow(PhoneControlSnapshot(hasFlashHardware = false))
    val state: StateFlow<PhoneControlSnapshot> = _state.asStateFlow()

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchCameraId) {
                _state.update { it.copy(torchOn = enabled) }
            }
        }
    }

    init {
        scope.launch {
            val foundTorchId = runCatching {
                cameraManager?.cameraIdList?.firstOrNull { id ->
                    cameraManager.getCameraCharacteristics(id)
                        .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                }
            }.getOrNull()
            torchCameraId = foundTorchId
            if (foundTorchId != null) {
                _state.update { it.copy(hasFlashHardware = true) }
            }
            runCatching {
                cameraManager?.registerTorchCallback(torchCallback, Handler(Looper.getMainLooper()))
            }
            refreshState()
        }
    }

    fun refreshState() {
        val brightnessRaw = runCatching {
            Settings.System.getInt(appContext.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        }.getOrDefault(128)
        val brightnessMode = runCatching {
            Settings.System.getInt(appContext.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0)
        }.getOrDefault(0)
        val dndGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            notificationManager?.isNotificationPolicyAccessGranted == true
        } else {
            true
        }
        val ringer = when (audioManager?.ringerMode) {
            AudioManager.RINGER_MODE_VIBRATE -> PhoneRingerMode.VIBRATE
            AudioManager.RINGER_MODE_SILENT -> PhoneRingerMode.SILENT
            else -> PhoneRingerMode.NORMAL
        }

        _state.update {
            it.copy(
                cameraGranted = PermissionHelper.hasCameraPermission(appContext),
                micGranted = PermissionHelper.hasMicrophonePermission(appContext),
                accessibilityEnabled = JarvisAccessibilityService.isConnected.value,
                writeSettingsGranted = PermissionHelper.hasWriteSettingsPermission(appContext),
                dndPolicyGranted = dndGranted,
                hasFlashHardware = torchCameraId != null,
                brightnessPercent = ((brightnessRaw / 255f) * 100f).toInt().coerceIn(0, 100),
                autoBrightness = brightnessMode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
                mediaVolumePercent = streamPercent(AudioManager.STREAM_MUSIC),
                ringVolumePercent = streamPercent(AudioManager.STREAM_RING),
                alarmVolumePercent = streamPercent(AudioManager.STREAM_ALARM),
                ringerMode = ringer,
                voiceAccessOverlayActive = JarvisAccessibilityService.overlayActive.value,
                voiceAccessTargetCount = JarvisAccessibilityService.numberedTargetCount.value
            )
        }
    }

    fun toggleFlashlight(enable: Boolean = !_state.value.torchOn): Result<String> {
        val id = torchCameraId
            ?: return Result.failure(IllegalStateException("No camera flash hardware on this device."))
        return try {
            cameraManager?.setTorchMode(id, enable)
            _state.update {
                it.copy(
                    torchOn = enable,
                    lastControlStatus = if (enable) "Flashlight ON" else "Flashlight OFF"
                )
            }
            Result.success(if (enable) "Flashlight turned ON, Sir." else "Flashlight turned OFF, Sir.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun setBrightnessPercent(percent: Int): Result<String> {
        val clamped = percent.coerceIn(5, 100)
        if (!PermissionHelper.hasWriteSettingsPermission(appContext)) {
            return Result.failure(IllegalStateException("Grant 'Modify System Settings' first to adjust screen brightness directly."))
        }
        return try {
            Settings.System.putInt(
                appContext.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            val raw = ((clamped / 100f) * 255f).toInt().coerceIn(10, 255)
            Settings.System.putInt(appContext.contentResolver, Settings.System.SCREEN_BRIGHTNESS, raw)
            refreshState()
            _state.update { it.copy(lastControlStatus = "Brightness set to $clamped%") }
            Result.success("Screen brightness set to $clamped%, Sir.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun setAutoBrightness(enabled: Boolean): Result<String> {
        if (!PermissionHelper.hasWriteSettingsPermission(appContext)) {
            return Result.failure(IllegalStateException("Grant 'Modify System Settings' first to toggle auto-brightness."))
        }
        return try {
            val mode = if (enabled) {
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
            } else {
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            }
            Settings.System.putInt(appContext.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, mode)
            refreshState()
            _state.update { it.copy(lastControlStatus = if (enabled) "Auto-brightness ON" else "Manual brightness") }
            Result.success(if (enabled) "Adaptive brightness enabled." else "Switched to manual brightness.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun setStreamVolumePercent(streamType: Int, percent: Int): Result<String> {
        val am = audioManager ?: return Result.failure(IllegalStateException("Audio service unavailable."))
        val clamped = percent.coerceIn(0, 100)
        return try {
            val max = am.getStreamMaxVolume(streamType).coerceAtLeast(1)
            val target = ((clamped / 100f) * max).toInt().coerceIn(0, max)
            am.setStreamVolume(streamType, target, AudioManager.FLAG_SHOW_UI)
            refreshState()
            val label = when (streamType) {
                AudioManager.STREAM_RING -> "Ringtone"
                AudioManager.STREAM_ALARM -> "Alarm"
                else -> "Media"
            }
            _state.update { it.copy(lastControlStatus = "$label volume $clamped%") }
            Result.success("$label volume set to $clamped%, Sir.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun setRingerMode(mode: PhoneRingerMode): Result<String> {
        val am = audioManager ?: return Result.failure(IllegalStateException("Audio service unavailable."))
        if (mode == PhoneRingerMode.SILENT && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            notificationManager?.isNotificationPolicyAccessGranted != true
        ) {
            return Result.failure(IllegalStateException("Grant 'Do Not Disturb' access to switch to Silent mode."))
        }
        return try {
            am.ringerMode = mode.androidMode
            refreshState()
            _state.update { it.copy(lastControlStatus = "Ringer: ${mode.label}") }
            Result.success("Phone ringer set to ${mode.label}, Sir.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun dispatchMediaKey(keyCode: Int): String {
        audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        _state.update { it.copy(lastControlStatus = "Media control dispatched") }
        return "Media command executed, Sir."
    }

    fun performGlobalNavigation(actionName: String): Result<String> {
        if (!JarvisAccessibilityService.isConnected.value) {
            return Result.failure(
                IllegalStateException("Enable 'J.A.R.V.I.S Phone Control' in Android Accessibility Settings to run system-wide actions.")
            )
        }
        val ok = when (actionName) {
            "back" -> JarvisAccessibilityService.performBack()
            "home" -> JarvisAccessibilityService.performHome()
            "recents" -> JarvisAccessibilityService.performRecents()
            "notifications" -> JarvisAccessibilityService.performNotifications()
            "quick_settings" -> JarvisAccessibilityService.performQuickSettings()
            "power" -> JarvisAccessibilityService.performPowerDialog()
            "lock" -> JarvisAccessibilityService.performLockScreen()
            "screenshot" -> JarvisAccessibilityService.performTakeScreenshot()
            "scroll_down" -> JarvisAccessibilityService.performScroll(down = true)
            "scroll_up" -> JarvisAccessibilityService.performScroll(down = false)
            "scroll_left" -> JarvisAccessibilityService.performHorizontalScroll(right = false)
            "scroll_right" -> JarvisAccessibilityService.performHorizontalScroll(right = true)
            "show_numbers" -> JarvisAccessibilityService.showNumberedTapGrid() > 0
            "hide_numbers" -> {
                JarvisAccessibilityService.hideNumberedTapGrid()
                true
            }
            else -> false
        }
        refreshState()
        val msg = when {
            actionName == "show_numbers" && ok ->
                "Voice Access numbered grid active (${JarvisAccessibilityService.numberedTargetCount.value} targets). Say 'Tap <number>'."
            actionName == "hide_numbers" -> "Voice Access numbered grid hidden."
            ok -> "Executed system action: $actionName, Sir."
            else -> "System action '$actionName' unavailable on this screen."
        }
        _state.update { it.copy(lastControlStatus = msg) }
        return if (ok) Result.success(msg) else Result.failure(IllegalStateException(msg))
    }

    fun openDialer(phoneNumber: String? = null): Result<String> {
        val uri = if (phoneNumber.isNullOrBlank()) Uri.parse("tel:") else Uri.parse("tel:${Uri.encode(phoneNumber.trim())}")
        return launchActivitySafe(Intent(Intent.ACTION_DIAL, uri), "Phone Dialer")
    }

    fun openCameraApp(videoMode: Boolean = false): Result<String> {
        val action = if (videoMode) MediaStore.ACTION_VIDEO_CAPTURE else MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA
        return launchActivitySafe(Intent(action), if (videoMode) "Video Camera" else "Camera")
    }

    fun setQuickTimer(minutes: Int, label: String = "JARVIS Timer"): Result<String> {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, (minutes.coerceAtLeast(1)) * 60)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }
        return launchActivitySafe(intent, "$minutes min Timer")
    }

    fun setQuickAlarm(hour: Int, minute: Int, label: String = "JARVIS Alarm"): Result<String> {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour.coerceIn(0, 23))
            putExtra(AlarmClock.EXTRA_MINUTES, minute.coerceIn(0, 59))
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }
        return launchActivitySafe(intent, String.format(Locale.US, "%02d:%02d Alarm", hour, minute))
    }

    fun openSystemControlPanel(panelType: String): Result<String> {
        val candidates: List<Intent> = when (panelType) {
            "internet" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                listOf(
                    Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY),
                    Intent(Settings.ACTION_WIRELESS_SETTINGS),
                    Intent(Settings.ACTION_WIFI_SETTINGS),
                    Intent(Settings.ACTION_SETTINGS)
                )
            } else {
                listOf(
                    Intent(Settings.ACTION_WIRELESS_SETTINGS),
                    Intent(Settings.ACTION_WIFI_SETTINGS),
                    Intent(Settings.ACTION_SETTINGS)
                )
            }
            "volume" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                listOf(
                    Intent(Settings.Panel.ACTION_VOLUME),
                    Intent(Settings.ACTION_SOUND_SETTINGS),
                    Intent(Settings.ACTION_SETTINGS)
                )
            } else {
                listOf(Intent(Settings.ACTION_SOUND_SETTINGS), Intent(Settings.ACTION_SETTINGS))
            }
            "display" -> listOf(Intent(Settings.ACTION_DISPLAY_SETTINGS), Intent(Settings.ACTION_SETTINGS))
            "battery" -> listOf(
                Intent(Intent.ACTION_POWER_USAGE_SUMMARY),
                Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS),
                Intent(Settings.ACTION_SETTINGS)
            )
            "hotspot" -> listOf(Intent(Settings.ACTION_WIRELESS_SETTINGS), Intent(Settings.ACTION_SETTINGS))
            "accessibility" -> listOf(PermissionHelper.createAccessibilitySettingsIntent(), Intent(Settings.ACTION_SETTINGS))
            "write_settings" -> listOf(PermissionHelper.createWriteSettingsIntent(appContext), Intent(Settings.ACTION_SETTINGS))
            "dnd" -> listOf(PermissionHelper.createDndPolicySettingsIntent(), Intent(Settings.ACTION_SETTINGS))
            "speech_packs" -> listOf(
                Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
                Intent("com.android.settings.VOICE_INPUT_SETTINGS"),
                Intent(Settings.ACTION_INPUT_METHOD_SETTINGS),
                Intent(Settings.ACTION_SETTINGS)
            )
            else -> listOf(Intent(Settings.ACTION_SETTINGS))
        }
        val label = panelType.replace('_', ' ')
        val pm = appContext.packageManager
        for (candidate in candidates) {
            if (candidate.resolveActivity(pm) != null) {
                val res = launchActivitySafe(candidate, label)
                if (res.isSuccess) return res
            }
        }
        return launchActivitySafe(Intent(Settings.ACTION_SETTINGS), label)
    }

    private fun launchActivitySafe(intent: Intent, label: String): Result<String> {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
            _state.update { it.copy(lastControlStatus = "Opened $label") }
            Result.success("Opened $label, Sir.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun streamPercent(streamType: Int): Int {
        val am = audioManager ?: return 0
        val max = am.getStreamMaxVolume(streamType).coerceAtLeast(1)
        val cur = am.getStreamVolume(streamType)
        return ((cur.toFloat() / max.toFloat()) * 100f).toInt().coerceIn(0, 100)
    }

    fun release() {
        runCatching { cameraManager?.unregisterTorchCallback(torchCallback) }
        scope.cancel()
    }
}
