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

class AirPodsBleScanner(private val context: Context) {

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
    private var isScanning = false

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
        // Prefer strongest signal if multiple nearby AirPods broadcast simultaneously
        if (previous == null ||
            parsed.deviceAddress == previous.deviceAddress ||
            parsed.rssi >= previous.rssi - 8 ||
            System.currentTimeMillis() - previous.timestampMillis > 5_000L
        ) {
            _telemetry.value = parsed
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

    @SuppressLint("MissingPermission")
    fun startScanning(hasScanPermission: Boolean) {
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

        val filter = ScanFilter.Builder()
            .setManufacturerData(AppleBlePacketParser.APPLE_MANUFACTURER_ID, byteArrayOf(0x07), byteArrayOf(0xFF.toByte()))
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
                delay(5_000L)
                val current = _telemetry.value ?: continue
                if (System.currentTimeMillis() - current.timestampMillis > STALE_TIMEOUT_MS) {
                    _telemetry.value = null
                }
            }
        }
    }

    companion object {
        private const val STALE_TIMEOUT_MS = 20_000L
    }
}
