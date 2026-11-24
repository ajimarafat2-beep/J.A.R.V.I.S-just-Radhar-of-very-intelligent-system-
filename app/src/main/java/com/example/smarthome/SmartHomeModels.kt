package com.example.smarthome

enum class SmartDeviceType(val displayLabel: String) {
    WASHING_MACHINE("Smart Washing Machine"),
    AIR_CONDITIONER("Smart AC"),
    SMART_TV("Smart TV"),
    REFRIGERATOR("Smart Refrigerator"),
    SMART_LIGHT("Smart Light"),
    CEILING_FAN("Smart Fan"),
    SMART_PLUG("Smart Plug"),
    DOOR_LOCK("Smart Door Lock")
}

data class SmartHomeDevice(
    val id: String,
    val name: String,
    val room: String,
    val type: SmartDeviceType,
    val isPoweredOn: Boolean = false,
    val mode: String = "",
    val primaryValue: Int = 0, // Temp °C, Volume %, Brightness %, Speed, etc.
    val secondaryValue: Int = 0, // Freezer Temp °C, Spin RPM, etc.
    val isActiveCycle: Boolean = false,
    val isMuted: Boolean = false,
    val isLocked: Boolean = true,
    val haEntityId: String = "", // Optional Home Assistant / LAN entity id
    val lastSyncedStatus: String = "Google Home Relay Ready"
)

data class LanSmartNode(
    val serviceName: String,
    val serviceType: String,
    val hostAddress: String,
    val port: Int
)

data class SmartHubConfig(
    val hubBaseUrl: String = "",
    val bearerToken: String = ""
)

data class SmartCommandResult(
    val success: Boolean,
    val summary: String,
    val queuedForNetwork: Boolean = false,
    val googleAssistantPrompt: String? = null
)
