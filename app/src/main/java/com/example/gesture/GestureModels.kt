package com.example.gesture

import android.view.KeyEvent

/**
 * Hardware and AVRCP media input gestures emitted by connected AirPods 1 / wireless earbuds.
 */
enum class AirPodsInputGesture(
    val title: String,
    val subtitle: String,
    val triggerSummary: String
) {
    DOUBLE_TAP_PLAY_PAUSE(
        title = "Double-Tap (Play/Pause)",
        subtitle = "Standard AirPods 1 double-tap mapped to Play/Pause over AVRCP",
        triggerSummary = "MEDIA_PLAY_PAUSE / PLAY / PAUSE"
    ),
    RAPID_DOUBLE_PRESS(
        title = "Rapid Double-Press",
        subtitle = "Two quick tap/hook signals within your cadence window",
        triggerSummary = "2x tap within window"
    ),
    TRIPLE_PRESS(
        title = "Triple-Press",
        subtitle = "Three quick tap/hook signals within your cadence window",
        triggerSummary = "3x tap within window"
    ),
    SINGLE_PRESS_HOOK(
        title = "Single Press (Headset Hook)",
        subtitle = "Single headset hook / stem press signal from earbuds",
        triggerSummary = "KEYCODE_HEADSETHOOK"
    ),
    POD_NEXT_SIGNAL(
        title = "Pod Next-Track Signal",
        subtitle = "Triggered when an AirPod side is configured to Next Track",
        triggerSummary = "KEYCODE_MEDIA_NEXT"
    ),
    POD_PREVIOUS_SIGNAL(
        title = "Pod Previous-Track Signal",
        subtitle = "Triggered when an AirPod side is configured to Previous Track",
        triggerSummary = "KEYCODE_MEDIA_PREVIOUS"
    ),
    VOICE_ASSIST_SIGNAL(
        title = "Pod Voice / Siri Signal",
        subtitle = "Triggered when an AirPod side invokes voice assistant",
        triggerSummary = "KEYCODE_VOICE_ASSIST"
    )
}

/**
 * Custom Android system actions and intents that any AirPods gesture can invoke.
 */
enum class SystemIntentAction(
    val title: String,
    val description: String,
    val intentLabel: String
) {
    TOGGLE_PLAY_PAUSE(
        title = "Toggle Music Playback",
        description = "Play or pause active music & podcast media",
        intentLabel = "KEYCODE_MEDIA_PLAY_PAUSE"
    ),
    NEXT_TRACK(
        title = "Skip to Next Track",
        description = "Advance current media queue to the next track",
        intentLabel = "KEYCODE_MEDIA_NEXT"
    ),
    PREVIOUS_TRACK(
        title = "Previous Track",
        description = "Restart or jump back to the previous track",
        intentLabel = "KEYCODE_MEDIA_PREVIOUS"
    ),
    VOLUME_UP(
        title = "Volume Up",
        description = "Increase media stream volume with system HUD",
        intentLabel = "STREAM_MUSIC + ADJUST_RAISE"
    ),
    VOLUME_DOWN(
        title = "Volume Down",
        description = "Decrease media stream volume with system HUD",
        intentLabel = "STREAM_MUSIC + ADJUST_LOWER"
    ),
    TOGGLE_MUTE(
        title = "Mute / Unmute Audio",
        description = "Toggle media audio mute state",
        intentLabel = "STREAM_MUSIC + TOGGLE_MUTE"
    ),
    ANNOUNCE_BATTERY(
        title = "Speak Battery Level",
        description = "Read Left, Right, and Case battery aloud into AirPods",
        intentLabel = "TextToSpeech Readout"
    ),
    TOGGLE_BASS_BOOST(
        title = "Toggle Bass Boost Mode",
        description = "Switch hardware Bass Boost EQ profile on or off",
        intentLabel = "AudioEffect BassBoost"
    ),
    VOICE_ASSISTANT(
        title = "Launch Voice Assistant",
        description = "Open system voice assistant via ACTION_VOICE_COMMAND",
        intentLabel = "Intent.ACTION_VOICE_COMMAND"
    ),
    OPEN_MUSIC_APP(
        title = "Open Music Player",
        description = "Launch your default music streaming application",
        intentLabel = "CATEGORY_APP_MUSIC"
    ),
    OPEN_CAMERA(
        title = "Open Camera",
        description = "Launch camera for hands-free photo capture",
        intentLabel = "INTENT_ACTION_STILL_IMAGE_CAMERA"
    ),
    NO_ACTION(
        title = "Pass Through / Disabled",
        description = "Do not intercept or remap this gesture",
        intentLabel = "Pass-through"
    )
}

data class GestureMappingConfig(
    val enabled: Boolean = true,
    val interceptMediaButtons: Boolean = true,
    val requireAirPodsConnected: Boolean = false,
    val multiPressWindowMs: Int = 450,
    val volumeStepCount: Int = 1,
    val mappings: Map<AirPodsInputGesture, SystemIntentAction> = defaultMappings()
) {
    fun actionFor(gesture: AirPodsInputGesture): SystemIntentAction {
        return mappings[gesture] ?: SystemIntentAction.NO_ACTION
    }

    companion object {
        fun defaultMappings(): Map<AirPodsInputGesture, SystemIntentAction> = mapOf(
            AirPodsInputGesture.DOUBLE_TAP_PLAY_PAUSE to SystemIntentAction.TOGGLE_PLAY_PAUSE,
            AirPodsInputGesture.RAPID_DOUBLE_PRESS to SystemIntentAction.NEXT_TRACK,
            AirPodsInputGesture.TRIPLE_PRESS to SystemIntentAction.PREVIOUS_TRACK,
            AirPodsInputGesture.SINGLE_PRESS_HOOK to SystemIntentAction.TOGGLE_PLAY_PAUSE,
            AirPodsInputGesture.POD_NEXT_SIGNAL to SystemIntentAction.VOLUME_UP,
            AirPodsInputGesture.POD_PREVIOUS_SIGNAL to SystemIntentAction.VOLUME_DOWN,
            AirPodsInputGesture.VOICE_ASSIST_SIGNAL to SystemIntentAction.ANNOUNCE_BATTERY
        )
    }
}

data class GestureEventRecord(
    val id: Long,
    val timestampMillis: Long,
    val sourceLabel: String,
    val keyCodeName: String,
    val recognizedGesture: AirPodsInputGesture,
    val executedAction: SystemIntentAction,
    val outcome: String,
    val isSimulatedTest: Boolean = false
)

object KeyCodeFormatter {
    fun format(keyCode: Int): String = when (keyCode) {
        KeyEvent.KEYCODE_HEADSETHOOK -> "KEYCODE_HEADSETHOOK"
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "KEYCODE_MEDIA_PLAY_PAUSE"
        KeyEvent.KEYCODE_MEDIA_PLAY -> "KEYCODE_MEDIA_PLAY"
        KeyEvent.KEYCODE_MEDIA_PAUSE -> "KEYCODE_MEDIA_PAUSE"
        KeyEvent.KEYCODE_MEDIA_NEXT -> "KEYCODE_MEDIA_NEXT"
        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> "KEYCODE_MEDIA_PREVIOUS"
        KeyEvent.KEYCODE_VOICE_ASSIST -> "KEYCODE_VOICE_ASSIST"
        else -> KeyEvent.keyCodeToString(keyCode)
    }
}
