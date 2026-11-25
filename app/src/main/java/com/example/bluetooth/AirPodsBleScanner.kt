package com.example.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class BleScanStatus {
    IDLE,
    SCANNING,
    BLUETOOTH_DISABLED,
    PERMISSION_REQUIRED,
    UNSUPPORTED,
    SCAN_FAILED
}

class AirPodsBleScanner(
    private val context: Context,
    private val isClassicConnectedProvider: () -> Boolean = { false }
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter
    private val bleScanner: BluetoothLeScanner? get() = bluetoothAdapter?.bluetoothLeScanner

    private val _telemetry = MutableStateFlow<AirPodsTelemetry?>(null)
    val telemetry: StateFlow<AirPodsTelemetry?> = _telemetry.asStateFlow()

    private val _scanStatus = MutableStateFlow(BleScanStatus.IDLE)
    val scanStatus: StateFlow<BleScanStatus> = _scanStatus.asStateFlow()

    private val _packetLogs = MutableStateFlow<List<BlePacketLog>>(emptyList())
    val packetLogs: StateFlow<List<BlePacketLog>> = _packetLogs.asStateFlow()

    private val _errorDetail = MutableStateFlow<String?>(null)
    val errorDetail: StateFlow<String?> = _errorDetail.asStateFlow()

    private var staleWatcherJob: Job? = null
    private var scanRefreshJob: Job? = null
    private var isScanning = false
    private var lastScanPermissionGranted = false

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach(::handleScanResult)
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            _scanStatus.value = BleScanStatus.SCAN_FAILED
            _errorDetail.value = "BLE scan error code: $errorCode"
        }
    }

    private fun handleScanResult(result: ScanResult) {
        val record = result.scanRecord ?: return
        val manufacturerData = record.getManufacturerSpecificData(AppleBlePacketParser.APPLE_MANUFACTURER_ID)
            ?: return

        val parsed = AppleBlePacketParser.parseManufacturerData(
            manufacturerData = manufacturerData,
            deviceAddress = result.device?.address ?: "BLE-Unknown",
            rssi = result.rssi
        ) ?: return

        val previous = _telemetry.value
        val now = parsed.timestampMillis
        // Prefer strongest signal if multiple nearby AirPods broadcast simultaneously
        if (previous == null ||
            parsed.deviceAddress == previous.deviceAddress ||
            parsed.model == previous.model ||
            parsed.rssi >= previous.rssi - 8 ||
            now - previous.timestampMillis > 5_000L
        ) {
            val merged = mergePartialTelemetry(previous, parsed, now)
            _telemetry.value = merged
        }

        val logEntry = BlePacketLog(
            timestampMillis = parsed.timestampMillis,
            address = parsed.deviceAddress,
            rssi = parsed.rssi,
            modelName = parsed.model.displayName,
            rawHex = parsed.rawHex
        )
        _packetLogs.update { current ->
            (listOf(logEntry) + current).take(25)
        }
    }

    /**
     * When AirPods are out of the case and the lid closes, Apple stops broadcasting the
     * case battery nibble (0x0F -> null). Preserve recent known percentages for the same
     * device/model while reflecting live in-ear & charging flags from the new frame.
     */
    private fun mergePartialTelemetry(
        previous: AirPodsTelemetry?,
        current: AirPodsTelemetry,
        nowMillis: Long
    ): AirPodsTelemetry {
        if (previous == null ||
            previous.model != current.model ||
            nowMillis - previous.timestampMillis > PARTIAL_CARRY_WINDOW_MS
        ) {
            return current
        }

        val leftPod = if (current.leftPod.percentage == null && previous.leftPod.percentage != null) {
            current.leftPod.copy(percentage = previous.leftPod.percentage, isCharging = false)
        } else {
            current.leftPod
        }
        val rightPod = if (current.rightPod.percentage == null && previous.rightPod.percentage != null) {
            current.rightPod.copy(percentage = previous.rightPod.percentage, isCharging = false)
        } else {
            current.rightPod
        }
        val caseBattery = if (current.caseBattery.percentage == null && previous.caseBattery.percentage != null) {
            current.caseBattery.copy(percentage = previous.caseBattery.percentage, isCharging = false)
        } else {
            current.caseBattery
        }

        return current.copy(
            leftPod = leftPod,
            rightPod = rightPod,
            caseBattery = caseBattery
        )
    }

    @SuppressLint("MissingPermission")
    fun startScanning(hasScanPermission: Boolean) {
        lastScanPermissionGranted = hasScanPermission
        if (!hasScanPermission) {
            _scanStatus.value = BleScanStatus.PERMISSION_REQUIRED
            return
        }
        val adapter = bluetoothAdapter
        if (adapter == null) {
            _scanStatus.value = BleScanStatus.UNSUPPORTED
            return
        }
        if (!adapter.isEnabled) {
            _scanStatus.value = BleScanStatus.BLUETOOTH_DISABLED
            return
        }
        val scanner = bleScanner
        if (scanner == null) {
            _scanStatus.value = BleScanStatus.UNSUPPORTED
            return
        }
        if (isScanning) return

        // Filter on Apple Manufacturer ID (0x004C) without a byte-prefix mask so OEM BLE chipsets
        // never drop AirPods frames; AppleBlePacketParser validates 0x07 frames in Kotlin.
        val filter = ScanFilter.Builder()
            .setManufacturerData(AppleBlePacketParser.APPLE_MANUFACTURER_ID, byteArrayOf())
            .build()

        val settingsBuilder = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0L)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            settingsBuilder.setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
        }

        try {
            _errorDetail.value = null
            scanner.startScan(listOf(filter), settingsBuilder.build(), scanCallback)
            isScanning = true
            _scanStatus.value = BleScanStatus.SCANNING
            startStaleWatcher()
            startScanRefresher()
        } catch (se: SecurityException) {
            isScanning = false
            _scanStatus.value = BleScanStatus.PERMISSION_REQUIRED
            _errorDetail.value = se.localizedMessage
        } catch (e: Exception) {
            isScanning = false
            _scanStatus.value = BleScanStatus.SCAN_FAILED
            _errorDetail.value = e.localizedMessage
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        staleWatcherJob?.cancel()
        staleWatcherJob = null
        scanRefreshJob?.cancel()
        scanRefreshJob = null
        if (!isScanning) {
            _scanStatus.value = BleScanStatus.IDLE
            return
        }
        try {
            bleScanner?.stopScan(scanCallback)
        } catch (_: Exception) {
            // Ignore errors if Bluetooth was turned off mid-scan
        } finally {
            isScanning = false
            _scanStatus.value = BleScanStatus.IDLE
        }
    }

    fun clearTelemetry() {
        _telemetry.value = null
    }

    private fun startStaleWatcher() {
        staleWatcherJob?.cancel()
        staleWatcherJob = scope.launch {
            while (isActive) {
                delay(10_000L)
                val current = _telemetry.value ?: continue
                val age = System.currentTimeMillis() - current.timestampMillis
                // Keep telemetry alive while AirPods remain connected over Classic Bluetooth
                // (out-of-case pods throttle BLE 0x07 broadcasts significantly).
                val connected = isClassicConnectedProvider()
                val timeout = if (connected) CONNECTED_STALE_TIMEOUT_MS else DISCONNECTED_STALE_TIMEOUT_MS
                if (age > timeout) {
                    _telemetry.value = null
                }
            }
        }
    }

    private fun startScanRefresher() {
        scanRefreshJob?.cancel()
        scanRefreshJob = scope.launch {
            while (isActive) {
                delay(SCAN_REFRESH_INTERVAL_MS)
                if (isScanning && lastScanPermissionGranted && bluetoothAdapter?.isEnabled == true) {
                    runCatching { bleScanner?.stopScan(scanCallback) }
                    isScanning = false
                    startScanning(true)
                }
            }
        }
    }

    companion object {
        private const val DISCONNECTED_STALE_TIMEOUT_MS = 60_000L
        private const val CONNECTED_STALE_TIMEOUT_MS = 10 * 60_000L
        private const val PARTIAL_CARRY_WINDOW_MS = 30 * 60_000L
        private const val SCAN_REFRESH_INTERVAL_MS = 12 * 60_000L
    }
}
