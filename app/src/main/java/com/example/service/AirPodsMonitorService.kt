package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.bluetooth.AirPodsTelemetry
import com.example.core.PodPulseContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Foreground service (connectedDevice) that:
 * 1. Keeps BLE telemetry alive and displays an iOS-style live L / R / Case battery notification.
 * 2. Hosts an active MediaSession to intercept AVRCP double-tap / press events in the background.
 * 3. Drives Automatic Ear Detection (auto-pause when removed, auto-resume when placed back).
 * 4. Sends low-battery alerts below the user's threshold.
 */
class AirPodsMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var monitorJob: Job? = null
    private var mediaSession: MediaSession? = null
    private var wasAnyPodInEar = false
    private var autoPausedByEarDetection = false
    private var lowBatteryNotified = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        setupMediaSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_ANNOUNCE_BATTERY -> {
                PodPulseContainer.get(this).speakCurrentBattery()
                return START_STICKY
            }
        }

        val initialNotification = buildLiveNotification(null)
        startForeground(NOTIFICATION_ID_LIVE, initialNotification)

        val container = PodPulseContainer.get(this)
        val hasScanPerm = hasBluetoothScanPermission()
        val hasConnectPerm = hasBluetoothConnectPermission()
        container.bleScanner.startScanning(hasScanPerm)
        container.deviceMonitor.startMonitoring(hasConnectPerm)

        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            combine(
                container.bleScanner.telemetry,
                container.preferencesRepository.companionSettingsFlow,
                container.preferencesRepository.gestureConfigFlow
            ) { telemetry, settings, gestureCfg ->
                Triple(telemetry, settings, gestureCfg)
            }.collectLatest { (telemetry, settings, gestureCfg) ->
                mediaSession?.isActive = gestureCfg.enabled && gestureCfg.interceptMediaButtons
                updateNotification(telemetry)

                if (telemetry != null) {
                    handleEarDetection(
                        telemetry = telemetry,
                        autoPause = settings.autoEarDetectionPause,
                        autoResume = settings.autoEarDetectionResume,
                        container = container
                    )
                    handleLowBatteryAlert(telemetry, settings.lowBatteryAlertPercent)
                }
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun setupMediaSession() {
        val container = PodPulseContainer.get(this)
        val session = MediaSession(this, "PodPulseAirPodsSession")
        @Suppress("DEPRECATION")
        session.setFlags(
            MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
        )
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or
                        PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS
                )
                .setState(PlaybackState.STATE_PAUSED, 0L, 1f)
                .build()
        )
        session.setCallback(object : MediaSession.Callback() {
            override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                val keyEvent: KeyEvent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                }
                if (keyEvent != null) {
                    val consumed = container.gestureDispatcher.onKeyEvent(
                        keyEvent = keyEvent,
                        sourceLabel = "MediaSession AVRCP",
                        isSimulatedTest = false
                    )
                    if (consumed) return true
                }
                return super.onMediaButtonEvent(mediaButtonIntent)
            }
        })
        session.isActive = true
        mediaSession = session
    }

    private fun handleEarDetection(
        telemetry: AirPodsTelemetry,
        autoPause: Boolean,
        autoResume: Boolean,
        container: PodPulseContainer
    ) {
        val inEarNow = telemetry.leftPod.isInEar || telemetry.rightPod.isInEar
        if (wasAnyPodInEar && !inEarNow && autoPause) {
            val paused = container.intentExecutor.dispatchPauseIfPlaying()
            if (paused) {
                autoPausedByEarDetection = true
            }
        } else if (!wasAnyPodInEar && inEarNow && autoResume && autoPausedByEarDetection) {
            container.intentExecutor.dispatchPlay()
            autoPausedByEarDetection = false
        }
        wasAnyPodInEar = inEarNow
    }

    private fun handleLowBatteryAlert(telemetry: AirPodsTelemetry, threshold: Int) {
        val minPod = listOfNotNull(telemetry.leftPod.percentage, telemetry.rightPod.percentage)
            .minOrNull() ?: return
        val anyCharging = telemetry.leftPod.isCharging || telemetry.rightPod.isCharging
        if (minPod <= threshold && !anyCharging && !lowBatteryNotified) {
            lowBatteryNotified = true
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notification = NotificationCompat.Builder(this, CHANNEL_ALERTS)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("AirPods Battery Low ($minPod%)")
                .setContentText("Place your ${telemetry.model.displayName} in the charging case soon.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            nm.notify(NOTIFICATION_ID_ALERT, notification)
        } else if (minPod > threshold + 10 || anyCharging) {
            lowBatteryNotified = false
        }
    }

    private fun updateNotification(telemetry: AirPodsTelemetry?) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID_LIVE, buildLiveNotification(telemetry))
    }

    private fun buildLiveNotification(telemetry: AirPodsTelemetry?): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, AirPodsMonitorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val speakIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, AirPodsMonitorService::class.java).setAction(ACTION_ANNOUNCE_BATTERY),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = telemetry?.model?.displayName ?: getString(R.string.service_scanning_title)
        val body = if (telemetry != null) {
            val l = telemetry.leftPod.percentage?.let { "$it%" } ?: "--"
            val r = telemetry.rightPod.percentage?.let { "$it%" } ?: "--"
            val c = telemetry.caseBattery.percentage?.let { "$it%" } ?: "--"
            "L: $l  •  R: $r  •  Case: $c"
        } else {
            getString(R.string.service_scanning_text)
        }

        return NotificationCompat.Builder(this, CHANNEL_LIVE)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Speak", speakIntent)
            .addAction(0, "Stop", stopIntent)
            .build()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val liveChannel = NotificationChannel(
                CHANNEL_LIVE,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
            }
            val alertChannel = NotificationChannel(
                CHANNEL_ALERTS,
                getString(R.string.notification_channel_alerts_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.notification_channel_alerts_desc)
            }
            nm.createNotificationChannel(liveChannel)
            nm.createNotificationChannel(alertChannel)
        }
    }

    private fun hasBluetoothScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    companion object {
        const val CHANNEL_LIVE = "podpulse_live_telemetry"
        const val CHANNEL_ALERTS = "podpulse_battery_alerts"
        const val NOTIFICATION_ID_LIVE = 1001
        const val NOTIFICATION_ID_ALERT = 1002
        const val ACTION_STOP = "com.example.podpulse.ACTION_STOP_MONITOR"
        const val ACTION_ANNOUNCE_BATTERY = "com.example.podpulse.ACTION_ANNOUNCE_BATTERY"

        fun start(context: Context) {
            val intent = Intent(context, AirPodsMonitorService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AirPodsMonitorService::class.java))
        }
    }
}
