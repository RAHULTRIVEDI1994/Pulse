package com.example

import com.example.bluetooth.AirPodsModel
import com.example.bluetooth.AppleBlePacketParser
import com.example.bluetooth.PodSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AirPodsBleParserTest {

    @Test
    fun `parses AirPods 1st Gen battery, charging, and in-ear flags`() {
        // Message type 0x07, len 0x19, subtype 0x01 (paired), model 0x02 0x20 (AirPods 1)
        // statusByte 0x02 -> flipped=true (Left primary), primary in-ear=true, secondary in-ear=false
        // batteryByte 0x97 -> Left=90%, Right=70%
        // chargeAndCaseByte 0x18 -> chargeFlags=0x01 (Right charging when flipped), Case=80%
        // lidByte 0x03 -> lidOpenCount=3, lidOpen=true
        val payload = byteArrayOf(
            0x07, 0x19, 0x01, 0x02, 0x20,
            0x02, 0x97.toByte(), 0x18, 0x03,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        )

        val telemetry = AppleBlePacketParser.parseManufacturerData(
            manufacturerData = payload,
            deviceAddress = "AA:BB:CC:DD:EE:FF",
            rssi = -52
        )

        assertNotNull(telemetry)
        telemetry!!
        assertEquals(AirPodsModel.AIRPODS_1, telemetry.model)
        assertEquals(PodSide.LEFT, telemetry.primaryPod)
        assertEquals(90, telemetry.leftPod.percentage)
        assertFalse(telemetry.leftPod.isCharging)
        assertTrue(telemetry.leftPod.isInEar)
        assertEquals(70, telemetry.rightPod.percentage)
        assertTrue(telemetry.rightPod.isCharging)
        assertFalse(telemetry.rightPod.isInEar)
        assertEquals(80, telemetry.caseBattery.percentage)
        assertFalse(telemetry.caseBattery.isCharging)
        assertTrue(telemetry.isLidOpen)
        assertEquals(3, telemetry.lidOpenCount)
    }

    @Test
    fun `does not show charging when chargeFlags nibble is 0x0F or pods are in ear`() {
        // statusByte 0x0A -> flipped=true, both pods in-ear (0x02 | 0x08)
        // chargeAndCaseByte 0xFF -> chargeFlags=0x0F (unavailable), case=0x0F (unavailable)
        val outOfCasePayload = byteArrayOf(
            0x07, 0x19, 0x01, 0x02, 0x20,
            0x0A, 0x88.toByte(), 0xFF.toByte(), 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        )

        val telemetry = AppleBlePacketParser.parseManufacturerData(
            manufacturerData = outOfCasePayload,
            deviceAddress = "AA:BB:CC:DD:EE:FF",
            rssi = -58
        )

        assertNotNull(telemetry)
        telemetry!!
        assertEquals(80, telemetry.leftPod.percentage)
        assertEquals(80, telemetry.rightPod.percentage)
        assertNull(telemetry.caseBattery.percentage)
        assertFalse(telemetry.leftPod.isCharging)
        assertFalse(telemetry.rightPod.isCharging)
        assertFalse(telemetry.caseBattery.isCharging)
        assertFalse(telemetry.isLidOpen)
    }

    @Test
    fun `ignores pairing setup packets and short or non-proximity packets`() {
        val pairingSetupPayload = byteArrayOf(
            0x07, 0x19, 0x00, 0x02, 0x20,
            0x02, 0x97.toByte(), 0x78, 0x03,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        )
        assertNull(AppleBlePacketParser.parseManufacturerData(pairingSetupPayload, "00:11", -50))

        val shortPayload = byteArrayOf(0x07, 0x19, 0x01, 0x02)
        assertNull(AppleBlePacketParser.parseManufacturerData(shortPayload, "00:11", -60))

        val nonProximity = ByteArray(16) { 0x10 }
        assertNull(AppleBlePacketParser.parseManufacturerData(nonProximity, "00:11", -60))
    }
}
