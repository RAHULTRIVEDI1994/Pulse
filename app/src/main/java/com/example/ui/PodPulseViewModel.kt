package com.example.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bluetooth.AirPodsTelemetry
import com.example.bluetooth.BatterySample
import com.example.bluetooth.BlePacketLog
import com.example.bluetooth.BleScanStatus
import com.example.bluetooth.ConnectedAudioDevice
import com.example.core.PodPulseContainer
import com.example.data.CompanionSettings
import com.example.features.EqPreset
import com.example.features.EqualizerState
import com.example.features.FindMyChannel
import com.example.gesture.AirPodsInputGesture
import com.example.gesture.GestureEventRecord
import com.example.gesture.GestureMappingConfig
import com.example.gesture.SystemIntentAction
import com.example.service.AirPodsMonitorService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PermissionStatus(
    val hasBluetoothScan: Boolean = false,
    val hasBluetoothConnect: Boolean = false,
    val hasNotifications: Boolean = true
) {
    val allBluetoothGranted: Boolean get() = hasBluetoothScan && hasBluetoothConnect
}

data class PodPulseUiState(
    val telemetry: AirPodsTelemetry? = null,
    val scanStatus: BleScanStatus = BleScanStatus.IDLE,
    val scanErrorDetail: String? = null,
    val isBluetoothEnabled: Boolean = false,
    val connectedDevices: List<ConnectedAudioDevice> = emptyList(),
    val bondedDevices: List<ConnectedAudioDevice> = emptyList(),
    val isAirPodsAudioRouted: Boolean = false,
    val packetLogs: List<BlePacketLog> = emptyList(),
    val batteryHistory: List<BatterySample> = emptyList(),
    val gestureConfig: GestureMappingConfig = GestureMappingConfig(),
    val gestureEventLog: List<GestureEventRecord> = emptyList(),
    val lastRecognizedGesture: GestureEventRecord? = null,
    val equalizerState: EqualizerState = EqualizerState(),
    val findMyChannel: FindMyChannel? = null,
    val companionSettings: CompanionSettings = CompanionSettings(),
    val musicVolume: Int = 8,
    val musicMaxVolume: Int = 15,
    val showIosPopupSheet: Boolean = false,
    val permissionStatus: PermissionStatus = PermissionStatus()
)

class PodPulseViewModel(application: Application) : AndroidViewModel(application) {

    private val container = PodPulseContainer.get(application)

    private val _batteryHistory = MutableStateFlow<List<BatterySample>>(emptyList())
    private val _showIosPopupSheet = MutableStateFlow(false)
    private val _musicVolume = MutableStateFlow(container.equalizerEngine.getCurrentMusicVolume())
    private val _permissionStatus = MutableStateFlow(readPermissionStatus())

    private var lastLidOpenState = false
    private var lastVoiceAnnouncementTimestamp = 0L

    private val bluetoothSnapshotFlow = combine(
        container.effectiveTelemetry,
        container.bleScanner.scanStatus,
        container.bleScanner.errorDetail,
        container.deviceMonitor.isBluetoothEnabled,
        container.deviceMonitor.connectedDevices
    ) { telemetry, scanStatus, errorDetail, btEnabled, connected ->
        BluetoothSnapshot(telemetry, scanStatus, errorDetail, btEnabled, connected)
    }

    private val deviceAndLogFlow = combine(
        container.deviceMonitor.bondedAudioDevices,
        container.deviceMonitor.isAirPodsAudioRouted,
        container.bleScanner.packetLogs,
        _batteryHistory
    ) { bonded, routed, logs, history ->
        DeviceLogSnapshot(bonded, routed, logs, history)
    }

    private val gestureAndAudioFlow = combine(
        container.gestureDispatcher.config,
        container.gestureDispatcher.eventLog,
        container.gestureDispatcher.lastRecognized,
        container.equalizerEngine.state,
        container.findMyEngine.activeChannel
    ) { gestureCfg, eventLog, lastGesture, eqState, findMy ->
        GestureAudioSnapshot(gestureCfg, eventLog, lastGesture, eqState, findMy)
    }

    private val localUiFlow = combine(
        container.preferencesRepository.companionSettingsFlow,
        _musicVolume,
        _showIosPopupSheet,
        _permissionStatus
    ) { settings, volPair, showPopup, perms ->
        LocalUiSnapshot(settings, volPair.first, volPair.second, showPopup, perms)
    }

    val uiState: StateFlow<PodPulseUiState> = combine(
        bluetoothSnapshotFlow,
        deviceAndLogFlow,
        gestureAndAudioFlow,
        localUiFlow
    ) { bt, devLog, gestAudio, local ->
        PodPulseUiState(
            telemetry = bt.telemetry,
            scanStatus = bt.scanStatus,
            scanErrorDetail = bt.errorDetail,
            isBluetoothEnabled = bt.isBluetoothEnabled,
            connectedDevices = bt.connectedDevices,
            bondedDevices = devLog.bondedDevices,
            isAirPodsAudioRouted = devLog.isAudioRouted,
            packetLogs = devLog.packetLogs,
            batteryHistory = devLog.batteryHistory,
            gestureConfig = gestAudio.gestureConfig,
            gestureEventLog = gestAudio.eventLog,
            lastRecognizedGesture = gestAudio.lastRecognized,
            equalizerState = gestAudio.equalizerState,
            findMyChannel = gestAudio.findMyChannel,
            companionSettings = local.settings,
            musicVolume = local.musicVolume,
            musicMaxVolume = local.musicMaxVolume,
            showIosPopupSheet = local.showPopup,
            permissionStatus = local.permissionStatus
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PodPulseUiState(permissionStatus = readPermissionStatus())
    )

    init {
        viewModelScope.launch {
            val saved = container.preferencesRepository.companionSettingsFlow.first()
            container.equalizerEngine.setEqEnabled(saved.eqEnabled)
            container.equalizerEngine.applyPreset(saved.eqPreset)
            if (saved.eqPreset == EqPreset.CUSTOM) {
                saved.customBandLevels.forEachIndexed { idx, db ->
                    container.equalizerEngine.updateBandLevel(idx, db)
                }
            }
            container.equalizerEngine.setBassBoostStrength(saved.bassBoostStrength)
            container.equalizerEngine.setBassBoostEnabled(saved.bassBoostEnabled)
            container.equalizerEngine.setLoudnessGain(saved.loudnessGainMb)
            container.equalizerEngine.setLoudnessEnabled(saved.loudnessEnabled)
            if (saved.monitorServiceEnabled && readPermissionStatus().allBluetoothGranted) {
                runCatching { AirPodsMonitorService.start(getApplication()) }
            }
        }

        viewModelScope.launch {
            container.effectiveTelemetry.collect { telemetry ->
                if (telemetry != null) {
                    recordBatterySample(telemetry)
                    val settings = container.preferencesRepository.companionSettingsFlow.first()
                    if (telemetry.isLidOpen && !lastLidOpenState && settings.popupOnLidOpen) {
                        _showIosPopupSheet.value = true
                    }
                    if (telemetry.isLidOpen && !lastLidOpenState && settings.announceBatteryOnConnect) {
                        val now = System.currentTimeMillis()
                        if (now - lastVoiceAnnouncementTimestamp > 20_000L) {
                            lastVoiceAnnouncementTimestamp = now
                            container.speakCurrentBattery()
                        }
                    }
                    lastLidOpenState = telemetry.isLidOpen
                } else {
                    lastLidOpenState = false
                }
            }
        }

        refreshPermissionsAndStartScanning()
    }

    fun refreshPermissionsAndStartScanning() {
        val perms = readPermissionStatus()
        _permissionStatus.value = perms
        container.deviceMonitor.startMonitoring(perms.hasBluetoothConnect)
        if (perms.hasBluetoothScan) {
            container.bleScanner.startScanning(true)
        }
        if (perms.allBluetoothGranted) {
            viewModelScope.launch {
                val settings = container.preferencesRepository.companionSettingsFlow.first()
                if (settings.monitorServiceEnabled) {
                    runCatching { AirPodsMonitorService.start(getApplication()) }
                }
            }
        }
        refreshMusicVolume()
    }

    fun restartBleScan() {
        val perms = readPermissionStatus()
        _permissionStatus.value = perms
        container.bleScanner.stopScanning()
        container.bleScanner.startScanning(perms.hasBluetoothScan)
        container.deviceMonitor.refreshConnectedDevices()
    }

    fun refreshMusicVolume() {
        _musicVolume.value = container.equalizerEngine.getCurrentMusicVolume()
    }

    fun setMusicVolume(volume: Int) {
        container.equalizerEngine.setMusicVolume(volume)
        _musicVolume.value = container.equalizerEngine.getCurrentMusicVolume()
    }

    fun openIosPopupPreview() {
        _showIosPopupSheet.value = true
    }

    fun dismissIosPopup() {
        _showIosPopupSheet.value = false
    }

    fun speakBatteryNow() {
        container.speakCurrentBattery()
    }

    fun setMonitorServiceEnabled(enabled: Boolean) {
        val ctx = getApplication<Application>()
        if (enabled) {
            AirPodsMonitorService.start(ctx)
        } else {
            AirPodsMonitorService.stop(ctx)
        }
        viewModelScope.launch {
            container.preferencesRepository.updateCompanionSettings {
                it.copy(monitorServiceEnabled = enabled)
            }
        }
    }

    fun updatePopupOnLidOpen(enabled: Boolean) {
        viewModelScope.launch {
            container.preferencesRepository.updateCompanionSettings { it.copy(popupOnLidOpen = enabled) }
        }
    }

    fun updateAutoEarPause(enabled: Boolean) {
        viewModelScope.launch {
            container.preferencesRepository.updateCompanionSettings { it.copy(autoEarDetectionPause = enabled) }
        }
    }

    fun updateAutoEarResume(enabled: Boolean) {
        viewModelScope.launch {
            container.preferencesRepository.updateCompanionSettings { it.copy(autoEarDetectionResume = enabled) }
        }
    }

    fun updateAnnounceBatteryOnConnect(enabled: Boolean) {
        viewModelScope.launch {
            container.preferencesRepository.updateCompanionSettings { it.copy(announceBatteryOnConnect = enabled) }
        }
    }

    fun updateLowBatteryThreshold(percent: Int) {
        viewModelScope.launch {
            container.preferencesRepository.updateCompanionSettings { it.copy(lowBatteryAlertPercent = percent) }
        }
    }

    // Gesture mapping actions
    fun updateGestureMapping(gesture: AirPodsInputGesture, action: SystemIntentAction) {
        viewModelScope.launch {
            container.preferencesRepository.updateGestureMapping(gesture, action)
        }
    }

    fun setGesturesEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.preferencesRepository.updateGestureEngineOptions(enabled = enabled)
        }
    }

    fun setInterceptMediaButtons(enabled: Boolean) {
        viewModelScope.launch {
            container.preferencesRepository.updateGestureEngineOptions(interceptMediaButtons = enabled)
        }
    }

    fun setRequireAirPodsConnected(required: Boolean) {
        viewModelScope.launch {
            container.preferencesRepository.updateGestureEngineOptions(requireAirPodsConnected = required)
        }
    }

    fun setMultiPressWindowMs(windowMs: Int) {
        viewModelScope.launch {
            container.preferencesRepository.updateGestureEngineOptions(multiPressWindowMs = windowMs)
        }
    }

    fun setVolumeStepCount(steps: Int) {
        viewModelScope.launch {
            container.preferencesRepository.updateGestureEngineOptions(volumeStepCount = steps)
        }
    }

    fun resetGestureMappings() {
        viewModelScope.launch {
            container.preferencesRepository.resetGestureMappings()
        }
    }

    fun testGesture(gesture: AirPodsInputGesture) {
        container.gestureDispatcher.simulateGestureInput(gesture)
        refreshMusicVolume()
    }

    fun clearGestureEventLog() {
        container.gestureDispatcher.clearEventLog()
    }

    // Equalizer & Bass Boost Mode actions
    fun setEqEnabled(enabled: Boolean) {
        container.equalizerEngine.setEqEnabled(enabled)
        viewModelScope.launch {
            container.preferencesRepository.updateCompanionSettings { it.copy(eqEnabled = enabled) }
        }
    }

    fun applyEqPreset(preset: EqPreset) {
        container.equalizerEngine.applyPreset(preset)
        val eqState = container.equalizerEngine.state.value
        viewModelScope.launch {
            container.preferencesRepository.updateCompanionSettings {
                it.copy(
                    eqPreset = preset,
                    bassBoostEnabled = eqState.bassBoostEnabled,
                    bassBoostStrength = eqState.bassBoostStrength,
                    customBandLevels = eqState.bands.map { band -> band.levelDb }
                )
            }
        }
    }

    fun updateEqBand(index: Int, levelDb: Float) {
        container.equalizerEngine.updateBandLevel(index, levelDb)
        val bands = container.equalizerEngine.state.value.bands.map { it.levelDb }
        viewModelScope.launch {
            container.preferencesRepository.updateCompanionSettings {
                it.copy(eqPreset = EqPreset.CUSTOM, customBandLevels = bands)
            }
        }
    }

    fun setBassBoostEnabled(enabled: Boolean) {
        container.equalizerEngine.setBassBoostEnabled(enabled)
        viewModelScope.launch {
            container.preferencesRepository.updateCompanionSettings { it.copy(bassBoostEnabled = enabled) }
        }
    }

    fun setBassBoostStrength(strength: Int) {
        container.equalizerEngine.setBassBoostStrength(strength)
        viewModelScope.launch {
            container.preferencesRepository.updateCompanionSettings { it.copy(bassBoostStrength = strength) }
        }
    }

    fun setLoudnessEnabled(enabled: Boolean) {
        container.equalizerEngine.setLoudnessEnabled(enabled)
        viewModelScope.launch {
            container.preferencesRepository.updateCompanionSettings { it.copy(loudnessEnabled = enabled) }
        }
    }

    fun setLoudnessGain(gainMb: Int) {
        container.equalizerEngine.setLoudnessGain(gainMb)
        viewModelScope.launch {
            container.preferencesRepository.updateCompanionSettings { it.copy(loudnessGainMb = gainMb) }
        }
    }

    // Find My AirPods audio locator
    fun toggleFindMy(channel: FindMyChannel) {
        if (container.findMyEngine.activeChannel.value == channel) {
            container.findMyEngine.stop()
        } else {
            container.findMyEngine.startChirp(channel)
        }
    }

    fun stopFindMy() {
        container.findMyEngine.stop()
    }

    private fun recordBatterySample(telemetry: AirPodsTelemetry) {
        val last = _batteryHistory.value.lastOrNull()
        val now = telemetry.timestampMillis
        if (last == null ||
            now - last.timestampMillis >= 20_000L ||
            last.leftPercent != telemetry.leftPod.percentage ||
            last.rightPercent != telemetry.rightPod.percentage ||
            last.casePercent != telemetry.caseBattery.percentage
        ) {
            _batteryHistory.update { current ->
                (current + BatterySample(
                    timestampMillis = now,
                    leftPercent = telemetry.leftPod.percentage,
                    rightPercent = telemetry.rightPod.percentage,
                    casePercent = telemetry.caseBattery.percentage
                )).takeLast(30)
            }
        }
    }

    private fun readPermissionStatus(): PermissionStatus {
        val ctx = getApplication<Application>()
        val hasScan = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }
        val hasConnect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val hasNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return PermissionStatus(hasScan, hasConnect, hasNotifications)
    }

    override fun onCleared() {
        super.onCleared()
        container.findMyEngine.stop()
    }

    private data class BluetoothSnapshot(
        val telemetry: AirPodsTelemetry?,
        val scanStatus: BleScanStatus,
        val errorDetail: String?,
        val isBluetoothEnabled: Boolean,
        val connectedDevices: List<ConnectedAudioDevice>
    )

    private data class DeviceLogSnapshot(
        val bondedDevices: List<ConnectedAudioDevice>,
        val isAudioRouted: Boolean,
        val packetLogs: List<BlePacketLog>,
        val batteryHistory: List<BatterySample>
    )

    private data class GestureAudioSnapshot(
        val gestureConfig: GestureMappingConfig,
        val eventLog: List<GestureEventRecord>,
        val lastRecognized: GestureEventRecord?,
        val equalizerState: EqualizerState,
        val findMyChannel: FindMyChannel?
    )

    private data class LocalUiSnapshot(
        val settings: CompanionSettings,
        val musicVolume: Int,
        val musicMaxVolume: Int,
        val showPopup: Boolean,
        val permissionStatus: PermissionStatus
    )
}
