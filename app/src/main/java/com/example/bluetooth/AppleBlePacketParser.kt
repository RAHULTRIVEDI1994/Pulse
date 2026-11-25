package com.example.bluetooth

/**
 * Parses Apple Continuity Manufacturer Specific Data (Company ID 0x004C)
 * Proximity Pairing packets (Message Type 0x07) broadcast by AirPods 1 and compatible models.
 */
object AppleBlePacketParser {

    const val APPLE_MANUFACTURER_ID = 0x004C
    private const val PROXIMITY_PAIRING_TYPE = 0x07
    private const val MIN_PROXIMITY_PAYLOAD_BYTES = 10
    private const val SUBTYPE_PAIRING_SETUP = 0x00

    fun parseManufacturerData(
        manufacturerData: ByteArray,
        deviceAddress: String,
        rssi: Int,
        timestampMillis: Long = System.currentTimeMillis()
    ): AirPodsTelemetry? {
        if (manufacturerData.size < MIN_PROXIMITY_PAYLOAD_BYTES) return null

        val messageType = manufacturerData[0].toInt() and 0xFF
        if (messageType != PROXIMITY_PAIRING_TYPE) return null

        // Subtype 0x00 is broadcast during Pairing/Setup button press and does not carry
        // standard paired battery/charging flags at offsets 5..8.
        val pairingSubtype = manufacturerData[2].toInt() and 0xFF
        if (pairingSubtype == SUBTYPE_PAIRING_SETUP) return null

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

        val leftPct = decodeBatteryNibble(leftNibble)
        val rightPct = decodeBatteryNibble(rightNibble)
        val casePct = decodeBatteryNibble(caseNibble)

        // In-ear detection flags live in the lower nibble of byte 5
        val primaryInEar = (statusByte and 0x02) != 0
        val secondaryInEar = (statusByte and 0x08) != 0

        val leftInEar = if (isFlipped) primaryInEar else secondaryInEar
        val rightInEar = if (isFlipped) secondaryInEar else primaryInEar

        // Charging flags live in the upper nibble of byte 7.
        // Nibble 0x0F indicates unavailable/unknown flags; ignore charging bits in that state.
        // Per Apple Continuity / OpenPods layout:
        //   when flipped:   bit 1 (0x02) = Left,  bit 0 (0x01) = Right
        //   when unflipped: bit 0 (0x01) = Left,  bit 1 (0x02) = Right
        //   bit 2 (0x04) = Case charging
        val chargeFlags = (chargeAndCaseByte ushr 4) and 0x0F
        val validChargeFlags = chargeFlags != 0x0F

        val rawLeftCharging = validChargeFlags && (chargeFlags and (if (isFlipped) 0x02 else 0x01)) != 0
        val rawRightCharging = validChargeFlags && (chargeFlags and (if (isFlipped) 0x01 else 0x02)) != 0
        val rawCaseCharging = validChargeFlags && (chargeFlags and 0x04) != 0

        // Pods in-ear or disconnected cannot be charging; disconnected case cannot be charging.
        val leftCharging = rawLeftCharging && leftPct != null && !leftInEar
        val rightCharging = rawRightCharging && rightPct != null && !rightInEar
        val caseCharging = rawCaseCharging && casePct != null

        // Lid state encoded in byte 8 (lower bits indicate lid open counter / open flag)
        val lidOpenCount = lidByte and 0x07
        val isLidOpen = (lidByte and 0x08) == 0 && casePct != null

        return AirPodsTelemetry(
            model = model,
            deviceAddress = deviceAddress,
            rssi = rssi,
            leftPod = PodBatteryState(
                percentage = leftPct,
                isCharging = leftCharging,
                isInEar = leftInEar
            ),
            rightPod = PodBatteryState(
                percentage = rightPct,
                isCharging = rightCharging,
                isInEar = rightInEar
            ),
            caseBattery = PodBatteryState(
                percentage = casePct,
                isCharging = caseCharging,
                isInEar = false
            ),
            isLidOpen = isLidOpen,
            lidOpenCount = lidOpenCount,
            primaryPod = primaryPod,
            rawHex = manufacturerData.toHexString(),
            timestampMillis = timestampMillis,
            source = TelemetrySource.BLE_CONTINUITY
        )
    }

    private fun decodeBatteryNibble(nibble: Int): Int? {
        return if (nibble in 0..10) nibble * 10 else null
    }

    fun ByteArray.toHexString(): String =
        joinToString(separator = " ") { byte -> "%02X".format(byte.toInt() and 0xFF) }
}
