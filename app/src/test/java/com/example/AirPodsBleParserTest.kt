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
        // Message type 0x07, len 0x19, state 0x01, model 0x02 0x20 (AirPods 1)
        // statusByte 0x02 -> flipped=true (Left primary), primary in-ear=true, secondary in-ear=false
        // batteryByte 0x97 -> Left=90%, Right=70%
        // chargeAndCaseByte 0x28 -> primaryCharging=false, secondaryCharging=true (Right charging), Case=80%
        // lidByte 0x03 -> lidOpenCount=3, lidOpen=true
        val payload = byteArrayOf(
            0x07, 0x19, 0x01, 0x02, 0x20,
            0x02, 0x97.toByte(), 0x28, 0x03,
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
        assertTrue(telemetry.isLidOpen)
        assertEquals(3, telemetry.lidOpenCount)
    }

    @Test
    fun `ignores non-proximity or short Apple packets`() {
        val shortPayload = byteArrayOf(0x07, 0x19, 0x01, 0x02)
        assertNull(AppleBlePacketParser.parseManufacturerData(shortPayload, "00:11", -60))

        val nonProximity = ByteArray(16) { 0x10 }
        assertNull(AppleBlePacketParser.parseManufacturerData(nonProximity, "00:11", -60))
    }
}
