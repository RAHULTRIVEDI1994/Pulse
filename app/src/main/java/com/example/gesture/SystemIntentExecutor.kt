package com.example.gesture

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.SystemClock
import android.provider.MediaStore
import android.view.KeyEvent

interface GestureActionExecutor {
    fun execute(action: SystemIntentAction, volumeSteps: Int = 1): String
    fun dispatchPauseIfPlaying(): Boolean
    fun dispatchPlay(): Boolean
}

/**
 * Executes mapped Android system intents and media/volume controls triggered by AirPods gestures.
 */
class SystemIntentExecutor(
    private val context: Context,
    private val onSpeakBatteryRequested: () -> Unit,
    private val onToggleBassBoostRequested: () -> Boolean,
    private val onInternalKeyDispatch: (Boolean) -> Unit
) : GestureActionExecutor {
    private val audioManager: AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    override fun execute(action: SystemIntentAction, volumeSteps: Int): String {
        return when (action) {
            SystemIntentAction.TOGGLE_PLAY_PAUSE -> {
                dispatchMediaKeyPair(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                "Dispatched Play/Pause media key"
            }
            SystemIntentAction.NEXT_TRACK -> {
                dispatchMediaKeyPair(KeyEvent.KEYCODE_MEDIA_NEXT)
                "Skipped to next track"
            }
            SystemIntentAction.PREVIOUS_TRACK -> {
                dispatchMediaKeyPair(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                "Jumped to previous track"
            }
            SystemIntentAction.VOLUME_UP -> {
                adjustMusicVolume(AudioManager.ADJUST_RAISE, volumeSteps)
                "Raised media volume (+$volumeSteps step${if (volumeSteps > 1) "s" else ""})"
            }
            SystemIntentAction.VOLUME_DOWN -> {
                adjustMusicVolume(AudioManager.ADJUST_LOWER, volumeSteps)
                "Lowered media volume (-$volumeSteps step${if (volumeSteps > 1) "s" else ""})"
            }
            SystemIntentAction.TOGGLE_MUTE -> {
                audioManager?.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_TOGGLE_MUTE,
                    AudioManager.FLAG_SHOW_UI
                )
                "Toggled media mute"
            }
            SystemIntentAction.ANNOUNCE_BATTERY -> {
                onSpeakBatteryRequested()
                "Speaking AirPods battery status"
            }
            SystemIntentAction.TOGGLE_BASS_BOOST -> {
                val enabled = onToggleBassBoostRequested()
                if (enabled) "Bass Boost Mode enabled" else "Bass Boost Mode disabled"
            }
            SystemIntentAction.VOICE_ASSISTANT -> {
                launchIntentSafely(
                    Intent(Intent.ACTION_VOICE_COMMAND).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    "Launched Voice Assistant",
                    "No Voice Assistant app available"
                )
            }
            SystemIntentAction.OPEN_MUSIC_APP -> {
                val musicIntent = Intent.makeMainSelectorActivity(
                    Intent.ACTION_MAIN,
                    Intent.CATEGORY_APP_MUSIC
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                launchIntentSafely(
                    musicIntent,
                    "Opened Music Player",
                    "No default Music Player found"
                )
            }
            SystemIntentAction.OPEN_CAMERA -> {
                val cameraIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                launchIntentSafely(
                    cameraIntent,
                    "Launched Camera",
                    "No Camera app available"
                )
            }
            SystemIntentAction.NO_ACTION -> "Pass-through (no action)"
        }
    }

    override fun dispatchPauseIfPlaying(): Boolean {
        val am = audioManager ?: return false
        if (!am.isMusicActive) return false
        dispatchMediaKeyPair(KeyEvent.KEYCODE_MEDIA_PAUSE)
        return true
    }

    override fun dispatchPlay(): Boolean {
        val am = audioManager ?: return false
        if (am.isMusicActive) return false
        dispatchMediaKeyPair(KeyEvent.KEYCODE_MEDIA_PLAY)
        return true
    }

    private fun adjustMusicVolume(direction: Int, steps: Int) {
        val am = audioManager ?: return
        val clampedSteps = steps.coerceIn(1, 5)
        repeat(clampedSteps) { index ->
            val flags = if (index == clampedSteps - 1) AudioManager.FLAG_SHOW_UI else 0
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, flags)
        }
    }

    private fun dispatchMediaKeyPair(keyCode: Int) {
        val am = audioManager ?: return
        val now = SystemClock.uptimeMillis()
        onInternalKeyDispatch(true)
        try {
            val downEvent = KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0)
            val upEvent = KeyEvent(now, now + 15L, KeyEvent.ACTION_UP, keyCode, 0)
            am.dispatchMediaKeyEvent(downEvent)
            am.dispatchMediaKeyEvent(upEvent)
        } finally {
            onInternalKeyDispatch(false)
        }
    }

    private fun launchIntentSafely(
        intent: Intent,
        successMsg: String,
        errorMsg: String
    ): String {
        return try {
            context.startActivity(intent)
            successMsg
        } catch (_: ActivityNotFoundException) {
            errorMsg
        } catch (e: SecurityException) {
            e.localizedMessage ?: errorMsg
        }
    }
}
