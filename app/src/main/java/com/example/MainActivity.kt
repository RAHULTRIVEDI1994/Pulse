package com.example

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.core.PodPulseContainer
import com.example.ui.PodPulseApp
import com.example.ui.theme.PodPulseTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PodPulseTheme {
                PodPulseApp()
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val handled = PodPulseContainer.get(this).gestureDispatcher.onKeyEvent(
            keyEvent = event,
            sourceLabel = "Activity KeyEvent",
            isSimulatedTest = false
        )
        return if (handled) true else super.dispatchKeyEvent(event)
    }
}
