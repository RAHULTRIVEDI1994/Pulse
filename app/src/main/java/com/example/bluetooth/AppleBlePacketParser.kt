package com.example.bluetooth

/**
 * Parses Apple Continuity Manufacturer Specific Data (Company ID 0x004C)
 * Proximity Pairing packets (Message Type 0x07) broadcast by AirPods 1 and compatible models.
 */
object AppleBlePacketParser {

    const val APPLE_MANUFACTURER_ID = 0x004C
    private const val PROXIMITY_PAIRING_TYPE = 0x07
    private const val MIN_PROXIMITY_PAYLOAD_BYTES = 16

    fun parseManufacturerData(
        manufacturerData: ByteArray,
        deviceAddress: String,
        rssi: Int,
        timestampMillis: Long = System.currentTimeMillis()
    ): AirPodsTelemetry? {
        if (manufacturerData.size < MIN_PROXIMITY_PAYLOAD_BYTES) return null

        val messageType = manufacturerData[0].toInt() and 0xFF
        if (messageType != PROXIMITY_PAIRING_TYPE) return null

        // Bytes 3..4 hold the big-endian Apple audio product model code (e.g. 0x0220 for AirPods 1)
        val modelHigh = manufacturerData[3].toInt() and 0xFF
        val modelLow = manufacturerData[4].toInt() and 0xFF
        val modelCode = (modelHigh shl 8) or modelLow
        val model = AirPodsModel.fromModelCode(modelCode)

        // Filter out non-AirPods / unknown Apple continuity packets unless model code matches known audio hardware
        if (model == AirPodsModel.UNKNOWN_AIRPODS && modelLow != 0x20) {
            return null
        }

        val statusByte = manufacturerData[5].toInt() and 0xFF
        val batteryByte = manufacturerData[6].toInt() and 0xFF
        val chargeAndCaseByte = manufacturerData[7].toInt() and 0xFF
        val lidByte = manufacturerData[8].toInt() and 0xFF

        // Bit 5 (0x20) of statusByte determines whether Left/Right nibbles are swapped
        val isFlipped = (statusByte and 0x20) == 0
        val primaryPod = if (isFlipped) PodSide.LEFT else PodSide.RIGHT

        val upperBatteryNibble = (batteryByte ushr 4) and 0x0F
        val lowerBatteryNibble = batteryByte and 0x0F

        val leftNibble = if (isFlipped) upperBatteryNibble else lowerBatteryNibble
        val rightNibble = if (isFlipped) lowerBatteryNibble else upperBatteryNibble
        val caseNibble = chargeAndCaseByte and 0x0F

        // Charging flags live in the upper nibble of byte 7
        val chargeFlags = (chargeAndCaseByte ushr 4) and 0x0F
        val primaryCharging = (chargeFlags and 0x01) != 0
        val secondaryCharging = (chargeFlags and 0x02) != 0
        val caseCharging = (chargeFlags and 0x04) != 0

        val leftCharging = if (isFlipped) primaryCharging else secondaryCharging
        val rightCharging = if (isFlipped) secondaryCharging else primaryCharging

        // In-ear detection flags live in the lower nibble of byte 5
        val primaryInEar = (statusByte and 0x02) != 0
        val secondaryInEar = (statusByte and 0x08) != 0

        val leftInEar = if (isFlipped) primaryInEar else secondaryInEar
        val rightInEar = if (isFlipped) secondaryInEar else primaryInEar

        // Lid state encoded in byte 8 (lower bits indicate lid open counter / open flag)
        val lidOpenCount = lidByte and 0x07
        val isLidOpen = (lidByte and 0x08) == 0 && (leftCharging || rightCharging || caseNibble <= 10)

        return AirPodsTelemetry(
            model = model,
            deviceAddress = deviceAddress,
            rssi = rssi,
            leftPod = PodBatteryState(
                percentage = decodeBatteryNibble(leftNibble),
                isCharging = leftCharging,
                isInEar = leftInEar
            ),
            rightPod = PodBatteryState(
                percentage = decodeBatteryNibble(rightNibble),
                isCharging = rightCharging,
                isInEar = rightInEar
            ),
            caseBattery = PodBatteryState(
                percentage = decodeBatteryNibble(caseNibble),
                isCharging = caseCharging,
                isInEar = false
            ),
            isLidOpen = isLidOpen,
            lidOpenCount = lidOpenCount,
            primaryPod = primaryPod,
            rawHex = manufacturerData.toHexString(),
            timestampMillis = timestampMillis
        )
    }

    private fun decodeBatteryNibble(nibble: Int): Int? {
        return if (nibble in 0..10) nibble * 10 else null
    }

    fun ByteArray.toHexString(): String =
        joinToString(separator = " ") { byte -> "%02X".format(byte.toInt() and 0xFF) }
}
