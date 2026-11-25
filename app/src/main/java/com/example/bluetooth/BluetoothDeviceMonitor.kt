package com.example.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monitors classic Bluetooth A2DP / Headset connections, bonded devices, and active audio routing
 * so PodPulse knows when AirPods are connected for audio playback and gesture interception.
 */
class BluetoothDeviceMonitor(private val context: Context) {

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter
    private val audioManager: AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val _isBluetoothEnabled = MutableStateFlow(bluetoothAdapter?.isEnabled == true)
    val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    private val _connectedDevices = MutableStateFlow<List<ConnectedAudioDevice>>(emptyList())
    val connectedDevices: StateFlow<List<ConnectedAudioDevice>> = _connectedDevices.asStateFlow()

    private val _bondedAudioDevices = MutableStateFlow<List<ConnectedAudioDevice>>(emptyList())
    val bondedAudioDevices: StateFlow<List<ConnectedAudioDevice>> = _bondedAudioDevices.asStateFlow()

    private val _isAirPodsAudioRouted = MutableStateFlow(false)
    val isAirPodsAudioRouted: StateFlow<Boolean> = _isAirPodsAudioRouted.asStateFlow()

    private var a2dpProxy: BluetoothA2dp? = null
    private var headsetProxy: BluetoothHeadset? = null
    private var isRegistered = false
    private var hasConnectPermission = false

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            when (profile) {
                BluetoothProfile.A2DP -> a2dpProxy = proxy as? BluetoothA2dp
                BluetoothProfile.HEADSET -> headsetProxy = proxy as? BluetoothHeadset
            }
            refreshConnectedDevices()
        }

        override fun onServiceDisconnected(profile: Int) {
            when (profile) {
                BluetoothProfile.A2DP -> a2dpProxy = null
                BluetoothProfile.HEADSET -> headsetProxy = null
            }
            refreshConnectedDevices()
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    _isBluetoothEnabled.value = state == BluetoothAdapter.STATE_ON
                    refreshConnectedDevices()
                }
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED,
                BluetoothDevice.ACTION_ACL_CONNECTED,
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    refreshConnectedDevices()
                }
            }
        }
    }

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refreshAudioRouting()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            refreshAudioRouting()
        }
    }

    @SuppressLint("MissingPermission")
    fun startMonitoring(connectPermissionGranted: Boolean) {
        hasConnectPermission = connectPermissionGranted
        _isBluetoothEnabled.value = bluetoothAdapter?.isEnabled == true

        if (!isRegistered) {
            val filter = IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(bluetoothReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(bluetoothReceiver, filter)
            }
            audioManager?.registerAudioDeviceCallback(audioDeviceCallback, null)
            isRegistered = true
        }

        if (connectPermissionGranted) {
            bluetoothAdapter?.getProfileProxy(context, profileListener, BluetoothProfile.A2DP)
            bluetoothAdapter?.getProfileProxy(context, profileListener, BluetoothProfile.HEADSET)
        }
        refreshConnectedDevices()
        refreshAudioRouting()
    }

    fun stopMonitoring() {
        if (isRegistered) {
            try {
                context.unregisterReceiver(bluetoothReceiver)
            } catch (_: IllegalArgumentException) {
            }
            audioManager?.unregisterAudioDeviceCallback(audioDeviceCallback)
            isRegistered = false
        }
        a2dpProxy?.let { bluetoothAdapter?.closeProfileProxy(BluetoothProfile.A2DP, it) }
        headsetProxy?.let { bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HEADSET, it) }
        a2dpProxy = null
        headsetProxy = null
    }

    @SuppressLint("MissingPermission")
    fun refreshConnectedDevices() {
        val adapter = bluetoothAdapter
        _isBluetoothEnabled.value = adapter?.isEnabled == true
        if (!hasConnectPermission || adapter == null || !adapter.isEnabled) {
            _connectedDevices.value = emptyList()
            _bondedAudioDevices.value = emptyList()
            return
        }

        try {
            val a2dpConnected = a2dpProxy?.connectedDevices.orEmpty()
            val hfpConnected = headsetProxy?.connectedDevices.orEmpty()

            val byAddress = linkedMapOf<String, ConnectedAudioDevice>()
            a2dpConnected.forEach { dev ->
                val name = dev.name ?: "Bluetooth Audio"
                byAddress[dev.address] = ConnectedAudioDevice(
                    name = name,
                    address = dev.address,
                    isA2dpConnected = true,
                    isHeadsetConnected = hfpConnected.any { it.address == dev.address },
                    isLikelyAirPods = isAirPodsName(name)
                )
            }
            hfpConnected.forEach { dev ->
                if (!byAddress.containsKey(dev.address)) {
                    val name = dev.name ?: "Bluetooth Headset"
                    byAddress[dev.address] = ConnectedAudioDevice(
                        name = name,
                        address = dev.address,
                        isA2dpConnected = false,
                        isHeadsetConnected = true,
                        isLikelyAirPods = isAirPodsName(name)
                    )
                }
            }
            _connectedDevices.value = byAddress.values.toList()

            val bonded = adapter.bondedDevices.orEmpty()
                .map { dev ->
                    val name = dev.name ?: "Paired Device"
                    ConnectedAudioDevice(
                        name = name,
                        address = dev.address,
                        isA2dpConnected = byAddress[dev.address]?.isA2dpConnected == true,
                        isHeadsetConnected = byAddress[dev.address]?.isHeadsetConnected == true,
                        isLikelyAirPods = isAirPodsName(name)
                    )
                }
                .sortedByDescending { it.isA2dpConnected || it.isLikelyAirPods }
            _bondedAudioDevices.value = bonded
        } catch (_: SecurityException) {
            _connectedDevices.value = emptyList()
            _bondedAudioDevices.value = emptyList()
        }
        refreshAudioRouting()
    }

    private fun refreshAudioRouting() {
        val outputs = audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty()
        val btAudioActive = outputs.any { info ->
            info.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                info.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
        _isAirPodsAudioRouted.value = btAudioActive
    }

    private fun isAirPodsName(name: String): Boolean {
        val lower = name.lowercase()
        return lower.contains("airpod") || lower.contains("pod") || lower.contains("beats")
    }
}
