package com.example.smarthome

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Smart Home & Google Home Bridge for JARVIS.
 * Supports:
 * - Direct Google Home app launch, System Device Controls, & Google Assistant voice relay.
 * - Real local Wi-Fi mDNS / NSD discovery for Google Cast, Matter, and Home Assistant hubs.
 * - Optional real Home Assistant / Local LAN REST webhook execution via OkHttp.
 * - Persistent Smart Appliance profiles (Washing Machine, AC, TV, Refrigerator, Lights, Fans, Locks).
 */
class JarvisSmartHomeManager(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as? NsdManager

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val _devices = MutableStateFlow(loadDevices())
    val devices: StateFlow<List<SmartHomeDevice>> = _devices.asStateFlow()

    private val _hubConfig = MutableStateFlow(loadHubConfig())
    val hubConfig: StateFlow<SmartHubConfig> = _hubConfig.asStateFlow()

    private val _lanNodes = MutableStateFlow<List<LanSmartNode>>(emptyList())
    val lanNodes: StateFlow<List<LanSmartNode>> = _lanNodes.asStateFlow()

    private val _isScanningLan = MutableStateFlow(false)
    val isScanningLan: StateFlow<Boolean> = _isScanningLan.asStateFlow()

    private val activeDiscoveryListeners = mutableListOf<NsdManager.DiscoveryListener>()

    fun isGoogleHomeInstalled(): Boolean {
        val pm = appContext.packageManager
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(GOOGLE_HOME_PACKAGE, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(GOOGLE_HOME_PACKAGE, 0)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun openGoogleHomeApp(): Result<String> {
        val pm = appContext.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(GOOGLE_HOME_PACKAGE)
        return try {
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                appContext.startActivity(launchIntent)
                Result.success("Opened Google Home app.")
            } else {
                val storeIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$GOOGLE_HOME_PACKAGE")
                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                appContext.startActivity(storeIntent)
                Result.success("Google Home not installed — opened Play Store.")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun openSystemDeviceControls(): Result<String> {
        return try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent("android.settings.DEVICE_CONTROLS_SETTINGS")
            } else {
                Intent(Settings.ACTION_SETTINGS)
            }.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            appContext.startActivity(intent)
            Result.success("Opened Android Device Controls.")
        } catch (_: Exception) {
            try {
                val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(fallback)
                Result.success("Opened System Settings.")
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    fun relayCommandToGoogleAssistant(commandPhrase: String): Result<String> {
        val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("JARVIS Google Home Command", commandPhrase))
        return try {
            val intent = Intent(Intent.ACTION_VOICE_COMMAND).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
            Result.success("Opened Google Assistant & copied: \"$commandPhrase\"")
        } catch (_: Exception) {
            openGoogleHomeApp()
        }
    }

    fun saveHubConfig(baseUrl: String, bearerToken: String) {
        val cleanUrl = baseUrl.trim().trimEnd('/')
        val cleanToken = bearerToken.trim()
        prefs.edit()
            .putString(KEY_HUB_URL, cleanUrl)
            .putString(KEY_HUB_TOKEN, cleanToken)
            .apply()
        _hubConfig.value = SmartHubConfig(cleanUrl, cleanToken)
    }

    fun addCustomDevice(
        name: String,
        room: String,
        type: SmartDeviceType,
        haEntityId: String = ""
    ): SmartHomeDevice {
        val defaults = defaultValuesFor(type)
        val created = defaults.copy(
            id = "dev_${UUID.randomUUID().toString().take(8)}",
            name = name.trim().ifBlank { type.displayLabel },
            room = room.trim().ifBlank { "Home" },
            haEntityId = haEntityId.trim()
        )
        val updated = _devices.value + created
        persistDevices(updated)
        _devices.value = updated
        return created
    }

    fun removeDevice(deviceId: String) {
        val updated = _devices.value.filterNot { it.id == deviceId }
        persistDevices(updated)
        _devices.value = updated
    }

    fun updateEntityId(deviceId: String, entityId: String) {
        updateDevice(deviceId) { it.copy(haEntityId = entityId.trim()) }
    }

    suspend fun togglePower(deviceId: String): SmartCommandResult {
        val dev = _devices.value.firstOrNull { it.id == deviceId }
            ?: return SmartCommandResult(false, "Device not found.")
        val nextOn = !dev.isPoweredOn
        val nextActive = if (!nextOn) false else dev.isActiveCycle
        val statusText = dispatchToHubOrBridge(
            device = dev,
            serviceDomain = domainFor(dev.type),
            serviceName = if (nextOn) "turn_on" else "turn_off",
            extraPayload = emptyMap(),
            assistantPhrase = "${if (nextOn) "Turn on" else "Turn off"} ${dev.name}"
        )
        updateDevice(deviceId) {
            it.copy(isPoweredOn = nextOn, isActiveCycle = nextActive, lastSyncedStatus = statusText.first)
        }
        return SmartCommandResult(
            success = true,
            summary = "${dev.name} turned ${if (nextOn) "ON" else "OFF"} (${statusText.first}).",
            googleAssistantPrompt = statusText.second
        )
    }

    suspend fun setWashingCycle(deviceId: String, mode: String, toggleRun: Boolean = false): SmartCommandResult {
        val dev = _devices.value.firstOrNull { it.id == deviceId }
            ?: return SmartCommandResult(false, "Washing machine not found.")
        val nextRun = if (toggleRun) !dev.isActiveCycle else dev.isActiveCycle
        val nextPower = if (nextRun) true else dev.isPoweredOn
        val statusText = dispatchToHubOrBridge(
            device = dev,
            serviceDomain = "switch",
            serviceName = if (nextPower) "turn_on" else "turn_off",
            extraPayload = mapOf("mode" to mode, "running" to nextRun),
            assistantPhrase = "Set ${dev.name} to $mode cycle"
        )
        updateDevice(deviceId) {
            it.copy(
                isPoweredOn = nextPower,
                isActiveCycle = nextRun,
                mode = mode,
                lastSyncedStatus = statusText.first
            )
        }
        val stateWord = if (nextRun) "running" else "ready"
        return SmartCommandResult(
            success = true,
            summary = "${dev.name}: $mode mode ($stateWord, ${dev.primaryValue} RPM).",
            googleAssistantPrompt = statusText.second
        )
    }

    suspend fun adjustWashingSpinRpm(deviceId: String, rpm: Int): SmartCommandResult {
        val dev = _devices.value.firstOrNull { it.id == deviceId }
            ?: return SmartCommandResult(false, "Washing machine not found.")
        val clamped = rpm.coerceIn(600, 1400)
        val statusText = dispatchToHubOrBridge(
            device = dev,
            serviceDomain = "number",
            serviceName = "set_value",
            extraPayload = mapOf("value" to clamped),
            assistantPhrase = "Set ${dev.name} spin speed to $clamped RPM"
        )
        updateDevice(deviceId) { it.copy(primaryValue = clamped, lastSyncedStatus = statusText.first) }
        return SmartCommandResult(true, "${dev.name} spin speed set to $clamped RPM.", googleAssistantPrompt = statusText.second)
    }

    suspend fun adjustTemperature(deviceId: String, targetCelsius: Int, isFreezer: Boolean = false): SmartCommandResult {
        val dev = _devices.value.firstOrNull { it.id == deviceId }
            ?: return SmartCommandResult(false, "Device not found.")
        val clamped = when {
            dev.type == SmartDeviceType.AIR_CONDITIONER -> targetCelsius.coerceIn(16, 30)
            dev.type == SmartDeviceType.REFRIGERATOR && isFreezer -> targetCelsius.coerceIn(-24, -15)
            dev.type == SmartDeviceType.REFRIGERATOR -> targetCelsius.coerceIn(1, 7)
            else -> targetCelsius.coerceIn(0, 100)
        }
        val statusText = dispatchToHubOrBridge(
            device = dev,
            serviceDomain = "climate",
            serviceName = "set_temperature",
            extraPayload = mapOf("temperature" to clamped),
            assistantPhrase = "Set ${dev.name} temperature to $clamped degrees"
        )
        updateDevice(deviceId) {
            if (isFreezer) {
                it.copy(secondaryValue = clamped, lastSyncedStatus = statusText.first)
            } else {
                it.copy(isPoweredOn = true, primaryValue = clamped, lastSyncedStatus = statusText.first)
            }
        }
        return SmartCommandResult(
            success = true,
            summary = "${dev.name} ${if (isFreezer) "freezer " else ""}temperature set to $clamped°C.",
            googleAssistantPrompt = statusText.second
        )
    }

    suspend fun setDeviceMode(deviceId: String, mode: String): SmartCommandResult {
        val dev = _devices.value.firstOrNull { it.id == deviceId }
            ?: return SmartCommandResult(false, "Device not found.")
        val statusText = dispatchToHubOrBridge(
            device = dev,
            serviceDomain = domainFor(dev.type),
            serviceName = "set_hvac_mode",
            extraPayload = mapOf("hvac_mode" to mode.lowercase(Locale.US)),
            assistantPhrase = "Set ${dev.name} to $mode mode"
        )
        updateDevice(deviceId) {
            it.copy(isPoweredOn = true, mode = mode, lastSyncedStatus = statusText.first)
        }
        return SmartCommandResult(true, "${dev.name} mode set to $mode.", googleAssistantPrompt = statusText.second)
    }

    suspend fun adjustTvVolume(deviceId: String, delta: Int, toggleMute: Boolean = false): SmartCommandResult {
        val dev = _devices.value.firstOrNull { it.id == deviceId }
            ?: return SmartCommandResult(false, "TV not found.")
        val nextMute = if (toggleMute) !dev.isMuted else dev.isMuted
        val nextVol = (dev.primaryValue + delta).coerceIn(0, 100)
        val phrase = if (toggleMute) {
            if (nextMute) "Mute ${dev.name}" else "Unmute ${dev.name}"
        } else {
            "Set ${dev.name} volume to $nextVol percent"
        }
        val statusText = dispatchToHubOrBridge(
            device = dev,
            serviceDomain = "media_player",
            serviceName = if (toggleMute) "volume_mute" else "volume_set",
            extraPayload = mapOf("volume_level" to (nextVol / 100.0), "is_volume_muted" to nextMute),
            assistantPhrase = phrase
        )
        updateDevice(deviceId) {
            it.copy(
                isPoweredOn = true,
                primaryValue = nextVol,
                isMuted = nextMute,
                lastSyncedStatus = statusText.first
            )
        }
        return SmartCommandResult(
            true,
            if (toggleMute) "${dev.name} ${if (nextMute) "muted" else "unmuted"}." else "${dev.name} volume set to $nextVol%.",
            googleAssistantPrompt = statusText.second
        )
    }

    suspend fun adjustLevelOrSpeed(deviceId: String, value: Int): SmartCommandResult {
        val dev = _devices.value.firstOrNull { it.id == deviceId }
            ?: return SmartCommandResult(false, "Device not found.")
        val max = if (dev.type == SmartDeviceType.CEILING_FAN) 5 else 100
        val min = if (dev.type == SmartDeviceType.CEILING_FAN) 1 else 0
        val clamped = value.coerceIn(min, max)
        val statusText = dispatchToHubOrBridge(
            device = dev,
            serviceDomain = domainFor(dev.type),
            serviceName = "turn_on",
            extraPayload = mapOf("level" to clamped),
            assistantPhrase = "Set ${dev.name} to $clamped"
        )
        updateDevice(deviceId) {
            it.copy(isPoweredOn = clamped > 0, primaryValue = clamped, lastSyncedStatus = statusText.first)
        }
        return SmartCommandResult(true, "${dev.name} level set to $clamped.", googleAssistantPrompt = statusText.second)
    }

    suspend fun toggleDoorLock(deviceId: String): SmartCommandResult {
        val dev = _devices.value.firstOrNull { it.id == deviceId }
            ?: return SmartCommandResult(false, "Lock not found.")
        val nextLocked = !dev.isLocked
        val statusText = dispatchToHubOrBridge(
            device = dev,
            serviceDomain = "lock",
            serviceName = if (nextLocked) "lock" else "unlock",
            extraPayload = emptyMap(),
            assistantPhrase = "${if (nextLocked) "Lock" else "Unlock"} ${dev.name}"
        )
        updateDevice(deviceId) {
            it.copy(isLocked = nextLocked, lastSyncedStatus = statusText.first)
        }
        return SmartCommandResult(
            true,
            "${dev.name} is now ${if (nextLocked) "LOCKED" else "UNLOCKED"}.",
            googleAssistantPrompt = statusText.second
        )
    }

    /**
     * Executes a natural-language command against the matching smart device.
     */
    suspend fun executeVoiceOrTextCommand(prompt: String): SmartCommandResult? {
        val lower = prompt.lowercase(Locale.getDefault())
        val targetDevice = findMatchingDevice(lower) ?: return null

        return when (targetDevice.type) {
            SmartDeviceType.WASHING_MACHINE -> {
                val cycle = when {
                    lower.contains("quick") || lower.contains("fast") -> "Quick 15m"
                    lower.contains("heavy") || lower.contains("deep") -> "Heavy"
                    lower.contains("delicate") -> "Delicate"
                    lower.contains("eco") -> "Eco"
                    lower.contains("spin") -> "Spin Only"
                    else -> targetDevice.mode.ifBlank { "Normal" }
                }
                when {
                    Regex("(off|stop|pause|বন্ধ|থামাও)").containsMatchIn(lower) -> {
                        if (targetDevice.isActiveCycle) setWashingCycle(targetDevice.id, cycle, toggleRun = true)
                        else if (targetDevice.isPoweredOn) togglePower(targetDevice.id)
                        else SmartCommandResult(true, "${targetDevice.name} is already paused/off.")
                    }
                    Regex("(start|wash|run|on|চালু|শুরু)").containsMatchIn(lower) -> {
                        if (!targetDevice.isActiveCycle) setWashingCycle(targetDevice.id, cycle, toggleRun = true)
                        else setWashingCycle(targetDevice.id, cycle, toggleRun = false)
                    }
                    else -> setWashingCycle(targetDevice.id, cycle, toggleRun = false)
                }
            }

            SmartDeviceType.AIR_CONDITIONER -> {
                val tempMatch = Regex("(\\d{2})").find(lower)?.groupValues?.get(1)?.toIntOrNull()
                when {
                    tempMatch != null -> adjustTemperature(targetDevice.id, tempMatch)
                    Regex("(cool|dry|fan|auto|heat)").containsMatchIn(lower) -> {
                        val mode = Regex("(cool|dry|fan|auto|heat)").find(lower)!!.value
                            .replaceFirstChar { it.uppercase() }
                        setDeviceMode(targetDevice.id, mode)
                    }
                    Regex("(off|বন্ধ)").containsMatchIn(lower) -> {
                        if (targetDevice.isPoweredOn) togglePower(targetDevice.id)
                        else SmartCommandResult(true, "${targetDevice.name} is already OFF.")
                    }
                    Regex("(on|চালু)").containsMatchIn(lower) -> {
                        if (!targetDevice.isPoweredOn) togglePower(targetDevice.id)
                        else SmartCommandResult(true, "${targetDevice.name} is already ON at ${targetDevice.primaryValue}°C.")
                    }
                    else -> togglePower(targetDevice.id)
                }
            }

            SmartDeviceType.SMART_TV -> {
                when {
                    lower.contains("mute") || lower.contains("মিউট") -> adjustTvVolume(targetDevice.id, 0, toggleMute = true)
                    Regex("(volume up|louder|বাড়াও|বাড়াও)").containsMatchIn(lower) -> adjustTvVolume(targetDevice.id, +10)
                    Regex("(volume down|lower|কমাও)").containsMatchIn(lower) -> adjustTvVolume(targetDevice.id, -10)
                    lower.contains("hdmi") -> {
                        val hdmi = if (lower.contains("2")) "HDMI 2" else "HDMI 1"
                        setDeviceMode(targetDevice.id, hdmi)
                    }
                    Regex("(off|বন্ধ)").containsMatchIn(lower) -> {
                        if (targetDevice.isPoweredOn) togglePower(targetDevice.id)
                        else SmartCommandResult(true, "${targetDevice.name} is already OFF.")
                    }
                    Regex("(on|চালু)").containsMatchIn(lower) -> {
                        if (!targetDevice.isPoweredOn) togglePower(targetDevice.id)
                        else SmartCommandResult(true, "${targetDevice.name} is already ON.")
                    }
                    else -> togglePower(targetDevice.id)
                }
            }

            SmartDeviceType.REFRIGERATOR -> {
                val tempMatch = Regex("(-?\\d+)").find(lower)?.groupValues?.get(1)?.toIntOrNull()
                when {
                    tempMatch != null -> adjustTemperature(
                        targetDevice.id,
                        tempMatch,
                        isFreezer = lower.contains("freezer") || tempMatch < 0
                    )
                    lower.contains("eco") -> setDeviceMode(targetDevice.id, "Eco")
                    lower.contains("super") -> setDeviceMode(targetDevice.id, "Super Cool")
                    else -> SmartCommandResult(
                        true,
                        "${targetDevice.name}: Fridge ${targetDevice.primaryValue}°C, Freezer ${targetDevice.secondaryValue}°C (${targetDevice.mode})."
                    )
                }
            }

            SmartDeviceType.DOOR_LOCK -> toggleDoorLock(targetDevice.id)
            else -> togglePower(targetDevice.id)
        }
    }

    private fun findMatchingDevice(lower: String): SmartHomeDevice? {
        val list = _devices.value
        return when {
            Regex("(washing|washer|laundry|ওয়াশিং|ওয়াশিং)").containsMatchIn(lower) ->
                list.firstOrNull { it.type == SmartDeviceType.WASHING_MACHINE }
            Regex("(\\bac\\b|air cond|aircon|এসি)").containsMatchIn(lower) ->
                list.firstOrNull { it.type == SmartDeviceType.AIR_CONDITIONER }
            Regex("(\\btv\\b|television|টিভি)").containsMatchIn(lower) ->
                list.firstOrNull { it.type == SmartDeviceType.SMART_TV }
            Regex("(fridge|refrigerator|freezer|ফ্রিজ)").containsMatchIn(lower) ->
                list.firstOrNull { it.type == SmartDeviceType.REFRIGERATOR }
            Regex("(light|lamp|bulb|লাইট|বাতি)").containsMatchIn(lower) ->
                list.firstOrNull { it.type == SmartDeviceType.SMART_LIGHT }
            Regex("(fan|ফ্যান|পাখা)").containsMatchIn(lower) ->
                list.firstOrNull { it.type == SmartDeviceType.CEILING_FAN }
            Regex("(plug|socket|প্লাগ)").containsMatchIn(lower) ->
                list.firstOrNull { it.type == SmartDeviceType.SMART_PLUG }
            Regex("(lock|door|দরজা|লক)").containsMatchIn(lower) ->
                list.firstOrNull { it.type == SmartDeviceType.DOOR_LOCK }
            else -> list.firstOrNull { lower.contains(it.name.lowercase(Locale.getDefault())) }
        }
    }

    /**
     * Real local Wi-Fi mDNS discovery via Android NsdManager for Chromecast / Google Home,
     * Matter hubs, and Home Assistant servers.
     */
    fun startLanDiscovery() {
        val nsd = nsdManager ?: return
        stopLanDiscovery()
        _lanNodes.value = emptyList()
        _isScanningLan.value = true

        val serviceTypes = listOf(
            "_googlecast._tcp.",
            "_matter._tcp.",
            "_home-assistant._tcp."
        )

        serviceTypes.forEach { serviceType ->
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {}
                override fun onServiceFound(service: NsdServiceInfo) {
                    @Suppress("DEPRECATION")
                    nsd.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            @Suppress("DEPRECATION")
                            val host = serviceInfo.host?.hostAddress ?: return
                            val node = LanSmartNode(
                                serviceName = serviceInfo.serviceName,
                                serviceType = serviceInfo.serviceType,
                                hostAddress = host,
                                port = serviceInfo.port
                            )
                            _lanNodes.update { existing ->
                                if (existing.any { it.serviceName == node.serviceName && it.hostAddress == node.hostAddress }) {
                                    existing
                                } else {
                                    existing + node
                                }
                            }
                        }
                    })
                }
                override fun onServiceLost(service: NsdServiceInfo) {}
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.w(TAG, "NSD start failed for $serviceType: $errorCode")
                }
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            }
            runCatching {
                nsd.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
                activeDiscoveryListeners.add(listener)
            }
        }
    }

    fun stopLanDiscovery() {
        val nsd = nsdManager ?: return
        activeDiscoveryListeners.forEach { listener ->
            runCatching { nsd.stopServiceDiscovery(listener) }
        }
        activeDiscoveryListeners.clear()
        _isScanningLan.value = false
    }

    private suspend fun dispatchToHubOrBridge(
        device: SmartHomeDevice,
        serviceDomain: String,
        serviceName: String,
        extraPayload: Map<String, Any>,
        assistantPhrase: String
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        val cfg = _hubConfig.value
        if (cfg.hubBaseUrl.isNotBlank() && device.haEntityId.isNotBlank()) {
            return@withContext try {
                val json = JSONObject().apply {
                    put("entity_id", device.haEntityId)
                    extraPayload.forEach { (k, v) -> put(k, v) }
                }
                val endpoint = "${cfg.hubBaseUrl}/api/services/$serviceDomain/$serviceName"
                val builder = Request.Builder()
                    .url(endpoint)
                    .post(json.toString().toRequestBody("application/json".toMediaType()))
                if (cfg.bearerToken.isNotBlank()) {
                    builder.header("Authorization", "Bearer ${cfg.bearerToken}")
                }
                httpClient.newCall(builder.build()).execute().use { resp ->
                    if (resp.isSuccessful) {
                        "Synced via LAN Hub (HTTP ${resp.code})" to assistantPhrase
                    } else {
                        "LAN Hub HTTP ${resp.code} — Tap Assistant Relay" to assistantPhrase
                    }
                }
            } catch (e: Exception) {
                "Hub unreachable (${e.javaClass.simpleName}) — Google Home Relay Ready" to assistantPhrase
            }
        }

        val bridgeLabel = if (isGoogleHomeInstalled()) {
            "Google Home App Connected"
        } else {
            "Google Home Relay Ready"
        }
        bridgeLabel to assistantPhrase
    }

    private fun domainFor(type: SmartDeviceType): String = when (type) {
        SmartDeviceType.AIR_CONDITIONER, SmartDeviceType.REFRIGERATOR -> "climate"
        SmartDeviceType.SMART_TV -> "media_player"
        SmartDeviceType.SMART_LIGHT -> "light"
        SmartDeviceType.CEILING_FAN -> "fan"
        SmartDeviceType.DOOR_LOCK -> "lock"
        else -> "switch"
    }

    private fun updateDevice(id: String, transform: (SmartHomeDevice) -> SmartHomeDevice) {
        val updated = _devices.value.map { if (it.id == id) transform(it) else it }
        persistDevices(updated)
        _devices.value = updated
    }

    private fun loadHubConfig(): SmartHubConfig = SmartHubConfig(
        hubBaseUrl = prefs.getString(KEY_HUB_URL, "").orEmpty(),
        bearerToken = prefs.getString(KEY_HUB_TOKEN, "").orEmpty()
    )

    private fun loadDevices(): List<SmartHomeDevice> {
        val raw = prefs.getString(KEY_DEVICES_JSON, null) ?: return DEFAULT_DEVICES
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val obj = arr.getJSONObject(i)
                val type = SmartDeviceType.entries.firstOrNull { it.name == obj.optString("type") }
                    ?: SmartDeviceType.SMART_PLUG
                SmartHomeDevice(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    room = obj.optString("room", "Home"),
                    type = type,
                    isPoweredOn = obj.optBoolean("isPoweredOn", false),
                    mode = obj.optString("mode", ""),
                    primaryValue = obj.optInt("primaryValue", 0),
                    secondaryValue = obj.optInt("secondaryValue", 0),
                    isActiveCycle = obj.optBoolean("isActiveCycle", false),
                    isMuted = obj.optBoolean("isMuted", false),
                    isLocked = obj.optBoolean("isLocked", true),
                    haEntityId = obj.optString("haEntityId", ""),
                    lastSyncedStatus = obj.optString("lastSyncedStatus", "Google Home Relay Ready")
                )
            }.ifEmpty { DEFAULT_DEVICES }
        } catch (_: Exception) {
            DEFAULT_DEVICES
        }
    }

    private fun persistDevices(list: List<SmartHomeDevice>) {
        val arr = JSONArray()
        list.forEach { d ->
            arr.put(JSONObject().apply {
                put("id", d.id)
                put("name", d.name)
                put("room", d.room)
                put("type", d.type.name)
                put("isPoweredOn", d.isPoweredOn)
                put("mode", d.mode)
                put("primaryValue", d.primaryValue)
                put("secondaryValue", d.secondaryValue)
                put("isActiveCycle", d.isActiveCycle)
                put("isMuted", d.isMuted)
                put("isLocked", d.isLocked)
                put("haEntityId", d.haEntityId)
                put("lastSyncedStatus", d.lastSyncedStatus)
            })
        }
        prefs.edit().putString(KEY_DEVICES_JSON, arr.toString()).apply()
    }

    fun release() {
        stopLanDiscovery()
    }

    companion object {
        private const val TAG = "JarvisSmartHomeMgr"
        private const val PREFS_NAME = "jarvis_smart_home_prefs"
        private const val KEY_DEVICES_JSON = "devices_json"
        private const val KEY_HUB_URL = "hub_url"
        private const val KEY_HUB_TOKEN = "hub_token"
        const val GOOGLE_HOME_PACKAGE = "com.google.android.apps.chromecast.app"

        fun defaultValuesFor(type: SmartDeviceType): SmartHomeDevice = when (type) {
            SmartDeviceType.WASHING_MACHINE -> SmartHomeDevice(
                id = "washer_1",
                name = "Smart Washing Machine",
                room = "Laundry",
                type = type,
                mode = "Normal",
                primaryValue = 1200
            )
            SmartDeviceType.AIR_CONDITIONER -> SmartHomeDevice(
                id = "ac_1",
                name = "Living Room AC",
                room = "Living Room",
                type = type,
                mode = "Cool",
                primaryValue = 22
            )
            SmartDeviceType.SMART_TV -> SmartHomeDevice(
                id = "tv_1",
                name = "Smart TV",
                room = "Living Room",
                type = type,
                mode = "HDMI 1",
                primaryValue = 35
            )
            SmartDeviceType.REFRIGERATOR -> SmartHomeDevice(
                id = "fridge_1",
                name = "Smart Refrigerator",
                room = "Kitchen",
                type = type,
                isPoweredOn = true,
                mode = "Eco",
                primaryValue = 4,
                secondaryValue = -18
            )
            SmartDeviceType.SMART_LIGHT -> SmartHomeDevice(
                id = "light_1",
                name = "Studio Smart Light",
                room = "Bedroom",
                type = type,
                mode = "Daylight",
                primaryValue = 80
            )
            SmartDeviceType.CEILING_FAN -> SmartHomeDevice(
                id = "fan_1",
                name = "Bedroom Smart Fan",
                room = "Bedroom",
                type = type,
                mode = "Breeze",
                primaryValue = 3
            )
            SmartDeviceType.SMART_PLUG -> SmartHomeDevice(
                id = "plug_1",
                name = "Desk Smart Plug",
                room = "Office",
                type = type
            )
            SmartDeviceType.DOOR_LOCK -> SmartHomeDevice(
                id = "lock_1",
                name = "Front Door Lock",
                room = "Entrance",
                type = type,
                isPoweredOn = true,
                isLocked = true
            )
        }

        private val DEFAULT_DEVICES = listOf(
            defaultValuesFor(SmartDeviceType.WASHING_MACHINE),
            defaultValuesFor(SmartDeviceType.AIR_CONDITIONER),
            defaultValuesFor(SmartDeviceType.SMART_TV),
            defaultValuesFor(SmartDeviceType.REFRIGERATOR),
            defaultValuesFor(SmartDeviceType.SMART_LIGHT),
            defaultValuesFor(SmartDeviceType.CEILING_FAN)
        )
    }
}
