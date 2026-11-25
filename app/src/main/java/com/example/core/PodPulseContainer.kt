package com.example.core

import android.content.Context
import com.example.bluetooth.AirPodsBleScanner
import com.example.bluetooth.BluetoothDeviceMonitor
import com.example.data.PreferencesRepository
import com.example.features.AcousticEqualizerEngine
import com.example.features.FindMyAudioEngine
import com.example.gesture.AirPodsGestureDispatcher
import com.example.gesture.SystemIntentExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Application-scoped dependency container shared across Activity, ViewModel,
 * BroadcastReceiver, and Foreground Monitor Service.
 */
class PodPulseContainer private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val preferencesRepository = PreferencesRepository(appContext)
    val bleScanner = AirPodsBleScanner(appContext)
    val deviceMonitor = BluetoothDeviceMonitor(appContext)
    val equalizerEngine = AcousticEqualizerEngine(appContext)
    val findMyEngine = FindMyAudioEngine()

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
            if (::gestureDispatcher.isInitialized) {
                gestureDispatcher.setInternalDispatchGuard(active)
            }
        }
    )

    init {
        gestureDispatcher = AirPodsGestureDispatcher(
            executor = intentExecutor,
            isAirPodsConnectedProvider = {
                bleScanner.telemetry.value != null ||
                    deviceMonitor.connectedDevices.value.any { it.isLikelyAirPods } ||
                    deviceMonitor.isAirPodsAudioRouted.value
            }
        )

        scope.launch {
            preferencesRepository.gestureConfigFlow.collectLatest { config ->
                gestureDispatcher.updateConfig(config)
            }
        }
    }

    fun speakCurrentBattery() {
        val t = bleScanner.telemetry.value
        val message = if (t == null) {
            "No AirPods battery telemetry detected yet. Open your AirPods case nearby to scan."
        } else {
            val left = t.leftPod.percentage?.let { "$it percent" } ?: "disconnected"
            val right = t.rightPod.percentage?.let { "$it percent" } ?: "disconnected"
            val caseStr = t.caseBattery.percentage?.let { ", case $it percent" } ?: ""
            "Left AirPod $left, Right AirPod $right$caseStr."
        }
        equalizerEngine.speakText(message)
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
