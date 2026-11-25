package com.example.bluetooth

enum class AirPodsModel(
    val displayName: String,
    val modelCode: Int,
    val isSinglePodDevice: Boolean = false
) {
    AIRPODS_1("AirPods (1st Gen)", 0x0220),
    AIRPODS_2("AirPods (2nd Gen)", 0x0F20),
    AIRPODS_3("AirPods (3rd Gen)", 0x1320),
    AIRPODS_4("AirPods (4th Gen)", 0x1920),
    AIRPODS_4_ANC("AirPods 4 (ANC)", 0x1B20),
    AIRPODS_PRO("AirPods Pro", 0x0E20),
    AIRPODS_PRO_2("AirPods Pro (2nd Gen)", 0x1420),
    AIRPODS_PRO_2_USBC("AirPods Pro (USB-C)", 0x2420),
    AIRPODS_MAX("AirPods Max", 0x0A20, isSinglePodDevice = true),
    BEATS_GENERIC("Beats Audio", 0x0520),
    UNKNOWN_AIRPODS("Apple Audio Device", 0x0000);

    companion object {
        fun fromModelCode(code: Int): AirPodsModel {
            return entries.firstOrNull { it.modelCode == code } ?: UNKNOWN_AIRPODS
        }
    }
}

enum class PodSide {
    LEFT,
    RIGHT
}

enum class ProximityZone(val label: String) {
    IMMEDIATE("Immediate (< 0.5m)"),
    NEAR("Near (0.5m - 3m)"),
    FAR("Far (> 3m)")
}

data class PodBatteryState(
    val percentage: Int?, // 0..100 or null when disconnected / unavailable
    val isCharging: Boolean = false,
    val isInEar: Boolean = false
) {
    val isConnected: Boolean get() = percentage != null
}

data class AirPodsTelemetry(
    val model: AirPodsModel,
    val deviceAddress: String,
    val rssi: Int,
    val leftPod: PodBatteryState,
    val rightPod: PodBatteryState,
    val caseBattery: PodBatteryState,
    val isLidOpen: Boolean,
    val lidOpenCount: Int,
    val primaryPod: PodSide,
    val rawHex: String,
    val timestampMillis: Long = System.currentTimeMillis()
) {
    val proximityZone: ProximityZone
        get() = when {
            rssi >= -55 -> ProximityZone.IMMEDIATE
            rssi >= -75 -> ProximityZone.NEAR
            else -> ProximityZone.FAR
        }

    val averagePodBattery: Int?
        get() {
            val values = listOfNotNull(leftPod.percentage, rightPod.percentage)
            return if (values.isEmpty()) null else values.average().toInt()
        }

    val estimatedListeningMinutes: Int?
        get() = averagePodBattery?.let { pct ->
            // AirPods 1st Gen rated up to 5 hours (300 minutes) per charge
            (pct * 3.0f).toInt()
        }
}

data class BlePacketLog(
    val timestampMillis: Long,
    val address: String,
    val rssi: Int,
    val modelName: String,
    val rawHex: String
)

data class ConnectedAudioDevice(
    val name: String,
    val address: String,
    val isA2dpConnected: Boolean,
    val isHeadsetConnected: Boolean,
    val isLikelyAirPods: Boolean
)

data class BatterySample(
    val timestampMillis: Long,
    val leftPercent: Int?,
    val rightPercent: Int?,
    val casePercent: Int?
)
