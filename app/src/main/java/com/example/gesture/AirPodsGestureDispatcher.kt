package com.example.gesture

import android.view.KeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Stateful input-event recognizer and custom gesture router for connected AirPods 1.
 *
 * Detects single/double/triple-press cadences within a configurable time window and maps
 * incoming AVRCP / MediaButton / HeadsetHook events to user-configured SystemIntentActions.
 */
class AirPodsGestureDispatcher(
    private val executor: GestureActionExecutor,
    private val isAirPodsConnectedProvider: () -> Boolean
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val eventIdGen = AtomicLong(1L)
    private val internalDispatchGuard = AtomicBoolean(false)
    private val guardUntilMillis = AtomicLong(0L)

    private val _config = MutableStateFlow(GestureMappingConfig())
    val config: StateFlow<GestureMappingConfig> = _config.asStateFlow()

    private val _eventLog = MutableStateFlow<List<GestureEventRecord>>(emptyList())
    val eventLog: StateFlow<List<GestureEventRecord>> = _eventLog.asStateFlow()

    private val _lastRecognized = MutableStateFlow<GestureEventRecord?>(null)
    val lastRecognized: StateFlow<GestureEventRecord?> = _lastRecognized.asStateFlow()

    private var tapCount = 0
    private var lastTriggerKeyCode = KeyEvent.KEYCODE_HEADSETHOOK
    private var lastSourceLabel = "AirPods AVRCP"
    private var lastSimulatedFlag = false
    private var cadenceJob: Job? = null

    fun setInternalDispatchGuard(active: Boolean) {
        internalDispatchGuard.set(active)
        if (!active) {
            guardUntilMillis.set(0L)
        }
    }

    fun armTimedDispatchGuard(durationMs: Long = 450L) {
        guardUntilMillis.set(System.currentTimeMillis() + durationMs)
    }

    private fun isInternalGuardActive(): Boolean {
        return internalDispatchGuard.get() || System.currentTimeMillis() < guardUntilMillis.get()
    }

    fun updateConfig(newConfig: GestureMappingConfig) {
        _config.value = newConfig
    }

    /**
     * Processes a hardware/AVRCP KeyEvent. Returns true if consumed and mapped by PodPulse.
     */
    fun onKeyEvent(
        keyEvent: KeyEvent,
        sourceLabel: String = "AirPods AVRCP",
        isSimulatedTest: Boolean = false
    ): Boolean {
        return onKeyInput(
            action = keyEvent.action,
            keyCode = keyEvent.keyCode,
            repeatCount = keyEvent.repeatCount,
            sourceLabel = sourceLabel,
            isSimulatedTest = isSimulatedTest
        )
    }

    fun onKeyInput(
        action: Int,
        keyCode: Int,
        repeatCount: Int = 0,
        sourceLabel: String = "AirPods AVRCP",
        isSimulatedTest: Boolean = false
    ): Boolean {
        if (isInternalGuardActive() && !isSimulatedTest) return false

        val cfg = _config.value
        if (!cfg.enabled && !isSimulatedTest) return false
        if (cfg.requireAirPodsConnected && !isSimulatedTest && !isAirPodsConnectedProvider()) {
            return false
        }

        if (!isSupportedMediaKey(keyCode)) return false

        // Consume ACTION_UP for supported media keys so the matching UP event isn't
        // leaked to the underlying OS media stack while we handle ACTION_DOWN.
        if (action == KeyEvent.ACTION_UP) {
            return true
        }
        if (action != KeyEvent.ACTION_DOWN || repeatCount > 0) return false

        return when (keyCode) {
            KeyEvent.KEYCODE_HEADSETHOOK,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_STOP -> {
                registerCadenceTap(keyCode, sourceLabel, isSimulatedTest, cfg.multiPressWindowMs)
                true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                if (cfg.actionFor(AirPodsInputGesture.POD_NEXT_SIGNAL) == SystemIntentAction.NO_ACTION && !isSimulatedTest) {
                    false
                } else {
                    resolveAndExecute(
                        gesture = AirPodsInputGesture.POD_NEXT_SIGNAL,
                        keyCode = keyCode,
                        sourceLabel = sourceLabel,
                        isSimulatedTest = isSimulatedTest
                    )
                    true
                }
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                if (cfg.actionFor(AirPodsInputGesture.POD_PREVIOUS_SIGNAL) == SystemIntentAction.NO_ACTION && !isSimulatedTest) {
                    false
                } else {
                    resolveAndExecute(
                        gesture = AirPodsInputGesture.POD_PREVIOUS_SIGNAL,
                        keyCode = keyCode,
                        sourceLabel = sourceLabel,
                        isSimulatedTest = isSimulatedTest
                    )
                    true
                }
            }
            KeyEvent.KEYCODE_VOICE_ASSIST -> {
                if (cfg.actionFor(AirPodsInputGesture.VOICE_ASSIST_SIGNAL) == SystemIntentAction.NO_ACTION && !isSimulatedTest) {
                    false
                } else {
                    resolveAndExecute(
                        gesture = AirPodsInputGesture.VOICE_ASSIST_SIGNAL,
                        keyCode = keyCode,
                        sourceLabel = sourceLabel,
                        isSimulatedTest = isSimulatedTest
                    )
                    true
                }
            }
            else -> false
        }
    }

    /**
     * Allows interactive testing of multi-tap gestures directly from the Gestures Studio UI.
     */
    fun simulateGestureInput(gesture: AirPodsInputGesture) {
        val (keyCode, repeatTaps) = when (gesture) {
            AirPodsInputGesture.DOUBLE_TAP_PLAY_PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE to 1
            AirPodsInputGesture.SINGLE_PRESS_HOOK -> KeyEvent.KEYCODE_HEADSETHOOK to 1
            AirPodsInputGesture.RAPID_DOUBLE_PRESS -> KeyEvent.KEYCODE_HEADSETHOOK to 2
            AirPodsInputGesture.TRIPLE_PRESS -> KeyEvent.KEYCODE_HEADSETHOOK to 3
            AirPodsInputGesture.POD_NEXT_SIGNAL -> KeyEvent.KEYCODE_MEDIA_NEXT to 1
            AirPodsInputGesture.POD_PREVIOUS_SIGNAL -> KeyEvent.KEYCODE_MEDIA_PREVIOUS to 1
            AirPodsInputGesture.VOICE_ASSIST_SIGNAL -> KeyEvent.KEYCODE_VOICE_ASSIST to 1
        }
        val now = System.currentTimeMillis()
        if (repeatTaps == 1 && gesture == AirPodsInputGesture.SINGLE_PRESS_HOOK) {
            onKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0), "Gesture Tester", true)
        } else if (repeatTaps == 1) {
            resolveAndExecute(gesture, keyCode, "Gesture Tester", isSimulatedTest = true)
        } else {
            scope.launch {
                repeat(repeatTaps) { idx ->
                    onKeyEvent(
                        KeyEvent(now + idx * 90L, now + idx * 90L, KeyEvent.ACTION_DOWN, keyCode, 0),
                        "Gesture Tester",
                        isSimulatedTest = true
                    )
                    delay(90L)
                }
            }
        }
    }

    fun clearEventLog() {
        _eventLog.value = emptyList()
        _lastRecognized.value = null
    }

    private fun registerCadenceTap(
        keyCode: Int,
        sourceLabel: String,
        isSimulatedTest: Boolean,
        windowMs: Int
    ) {
        tapCount += 1
        lastTriggerKeyCode = keyCode
        lastSourceLabel = sourceLabel
        lastSimulatedFlag = isSimulatedTest

        cadenceJob?.cancel()
        if (tapCount >= 3) {
            val finalKey = lastTriggerKeyCode
            val finalSource = lastSourceLabel
            val finalSim = lastSimulatedFlag
            tapCount = 0
            resolveAndExecute(AirPodsInputGesture.TRIPLE_PRESS, finalKey, finalSource, finalSim)
            return
        }

        val waitWindow = windowMs.coerceIn(220, 800).toLong()
        cadenceJob = scope.launch {
            delay(waitWindow)
            val count = tapCount
            val finalKey = lastTriggerKeyCode
            val finalSource = lastSourceLabel
            val finalSim = lastSimulatedFlag
            tapCount = 0

            val recognized = when {
                count >= 3 -> AirPodsInputGesture.TRIPLE_PRESS
                count == 2 -> AirPodsInputGesture.RAPID_DOUBLE_PRESS
                finalKey == KeyEvent.KEYCODE_HEADSETHOOK -> AirPodsInputGesture.SINGLE_PRESS_HOOK
                else -> AirPodsInputGesture.DOUBLE_TAP_PLAY_PAUSE
            }
            resolveAndExecute(recognized, finalKey, finalSource, finalSim)
        }
    }

    private fun resolveAndExecute(
        gesture: AirPodsInputGesture,
        keyCode: Int,
        sourceLabel: String,
        isSimulatedTest: Boolean
    ) {
        val cfg = _config.value
        val mappedAction = cfg.actionFor(gesture)
        val outcome = executor.execute(mappedAction, cfg.volumeStepCount)

        val record = GestureEventRecord(
            id = eventIdGen.getAndIncrement(),
            timestampMillis = System.currentTimeMillis(),
            sourceLabel = sourceLabel,
            keyCodeName = KeyCodeFormatter.format(keyCode),
            recognizedGesture = gesture,
            executedAction = mappedAction,
            outcome = outcome,
            isSimulatedTest = isSimulatedTest
        )
        _lastRecognized.value = record
        _eventLog.update { current -> (listOf(record) + current).take(30) }
    }

    private fun isSupportedMediaKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_HEADSETHOOK ||
            keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
            keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
            keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE ||
            keyCode == KeyEvent.KEYCODE_MEDIA_STOP ||
            keyCode == KeyEvent.KEYCODE_MEDIA_NEXT ||
            keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS ||
            keyCode == KeyEvent.KEYCODE_VOICE_ASSIST
    }
}
