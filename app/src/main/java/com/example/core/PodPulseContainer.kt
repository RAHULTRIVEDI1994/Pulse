package com.example.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.bluetooth.AirPodsBleScanner
import com.example.bluetooth.AirPodsModel
import com.example.bluetooth.AirPodsTelemetry
import com.example.bluetooth.BluetoothDeviceMonitor
import com.example.bluetooth.PodBatteryState
import com.example.bluetooth.PodSide
import com.example.bluetooth.TelemetrySource
import com.example.data.PreferencesRepository
import com.example.features.AcousticEqualizerEngine
import com.example.features.FindMyAudioEngine
import com.example.gesture.AirPodsGestureDispatcher
import com.example.gesture.SystemIntentExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Application-scoped dependency container shared across Activity, ViewModel,
 * BroadcastReceiver, and Foreground Monitor Service.
 */
class PodPulseContainer private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val preferencesRepository = PreferencesRepository(appContext)
    val deviceMonitor = BluetoothDeviceMonitor(appContext)
    val bleScanner = AirPodsBleScanner(
        context = appContext,
        isClassicConnectedProvider = {
            deviceMonitor.connectedDevices.value.any { it.isLikelyAirPods } ||
                deviceMonitor.isAirPodsAudioRouted.value
        }
    )
    val equalizerEngine = AcousticEqualizerEngine(appContext)
    val findMyEngine = FindMyAudioEngine()

    /**
     * Combines live BLE 0x004C telemetry with Classic Bluetooth HFP battery level so
     * out-of-case AirPods still display battery telemetry when BLE 0x07 frames pause.
     */
    val effectiveTelemetry: StateFlow<AirPodsTelemetry?> = combine(
        bleScanner.telemetry,
        deviceMonitor.classicBatteryPercent,
        deviceMonitor.connectedDevices,
        deviceMonitor.isAirPodsAudioRouted
    ) { ble, classicPct, connected, audioRouted ->
        if (ble != null) {
            ble
        } else if (classicPct != null && (connected.isNotEmpty() || audioRouted)) {
            val device = connected.firstOrNull { it.isLikelyAirPods } ?: connected.firstOrNull()
            AirPodsTelemetry(
                model = inferModelFromName(device?.name),
                deviceAddress = device?.address ?: "Classic-BT",
                rssi = -55,
                leftPod = PodBatteryState(
                    percentage = classicPct,
                    isCharging = false,
                    isInEar = audioRouted
                ),
                rightPod = PodBatteryState(
                    percentage = classicPct,
                    isCharging = false,
                    isInEar = audioRouted
                ),
                caseBattery = PodBatteryState(
                    percentage = null,
                    isCharging = false,
                    isInEar = false
                ),
                isLidOpen = false,
                lidOpenCount = 0,
                primaryPod = PodSide.LEFT,
                rawHex = "Classic BT HFP ($classicPct%)",
                source = TelemetrySource.CLASSIC_BT
            )
        } else {
            null
        }
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = null
    )

    var onSuppressMediaSessionForDispatch: ((Long) -> Unit)? = null

    lateinit var gestureDispatcher: AirPodsGestureDispatcher
        private set

    val intentExecutor: SystemIntentExecutor = SystemIntentExecutor(
        context = appContext,
        onSpeakBatteryRequested = { speakCurrentBattery() },
        onToggleBassBoostRequested = {
            val enabled = equalizerEngine.toggleBassBoost()
            scope.launch {
                preferencesRepository.updateCompanionSettings { it.copy(bassBoostEnabled = enabled) }
            }
            enabled
        },
        onInternalKeyDispatch = { active ->
            if (active && ::gestureDispatcher.isInitialized) {
                gestureDispatcher.armTimedDispatchGuard(450L)
                onSuppressMediaSessionForDispatch?.invoke(450L)
            }
        }
    )

    init {
        gestureDispatcher = AirPodsGestureDispatcher(
            executor = intentExecutor,
            isAirPodsConnectedProvider = {
                effectiveTelemetry.value != null ||
                    deviceMonitor.connectedDevices.value.any { it.isLikelyAirPods } ||
                    deviceMonitor.isAirPodsAudioRouted.value
            }
        )

        deviceMonitor.onBluetoothTurnedOn = {
            if (hasScanPermission()) {
                bleScanner.startScanning(true)
            }
        }

        scope.launch {
            preferencesRepository.gestureConfigFlow.collectLatest { config ->
                gestureDispatcher.updateConfig(config)
            }
        }
    }

    fun speakCurrentBattery() {
        val t = effectiveTelemetry.value
        val message = if (t == null) {
            "No AirPods battery telemetry detected yet. Make sure your AirPods are connected or open the case nearby."
        } else {
            val left = t.leftPod.percentage?.let { "$it percent" } ?: "disconnected"
            val right = t.rightPod.percentage?.let { "$it percent" } ?: "disconnected"
            val caseStr = t.caseBattery.percentage?.let { ", case $it percent" } ?: ""
            "Left AirPod $left, Right AirPod $right$caseStr."
        }
        equalizerEngine.speakText(message)
    }

    private fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun inferModelFromName(name: String?): AirPodsModel {
        val lower = name?.lowercase().orEmpty()
        return when {
            lower.contains("pro") -> AirPodsModel.AIRPODS_PRO
            lower.contains("max") -> AirPodsModel.AIRPODS_MAX
            lower.contains("beats") -> AirPodsModel.BEATS_GENERIC
            else -> AirPodsModel.AIRPODS_1
        }
    }

    companion object {
        @Volatile
        private var instance: PodPulseContainer? = null

        fun get(context: Context): PodPulseContainer {
            return instance ?: synchronized(this) {
                instance ?: PodPulseContainer(context).also { instance = it }
            }
        }
    }
}
