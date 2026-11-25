package com.example.gesture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.KeyEvent
import com.example.core.PodPulseContainer

/**
 * Receives system ACTION_MEDIA_BUTTON broadcasts from connected AirPods / Bluetooth headsets
 * and forwards KeyEvents to the shared AirPodsGestureDispatcher.
 */
class AirPodsMediaButtonReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_MEDIA_BUTTON) return

        val keyEvent: KeyEvent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
        }
        keyEvent ?: return

        val container = PodPulseContainer.get(context)
        val handled = container.gestureDispatcher.onKeyEvent(
            keyEvent = keyEvent,
            sourceLabel = "MediaButton Broadcast",
            isSimulatedTest = false
        )
        if (handled && isOrderedBroadcast) {
            abortBroadcast()
        }
    }
}
