package com.example

import android.view.KeyEvent
import com.example.gesture.AirPodsGestureDispatcher
import com.example.gesture.AirPodsInputGesture
import com.example.gesture.GestureActionExecutor
import com.example.gesture.GestureMappingConfig
import com.example.gesture.SystemIntentAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AirPodsGestureDispatcherTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeExecutor : GestureActionExecutor {
        val executed = mutableListOf<Pair<SystemIntentAction, Int>>()
        override fun execute(action: SystemIntentAction, volumeSteps: Int): String {
            executed += action to volumeSteps
            return "Executed ${action.name}"
        }

        override fun dispatchPauseIfPlaying(): Boolean = true
        override fun dispatchPlay(): Boolean = true
    }

    @Test
    fun `maps double-tap and triple-press gestures to custom system actions`() = runTest {
        val fakeExecutor = FakeExecutor()
        val dispatcher = AirPodsGestureDispatcher(
            executor = fakeExecutor,
            isAirPodsConnectedProvider = { true }
        )

        dispatcher.updateConfig(
            GestureMappingConfig(
                enabled = true,
                multiPressWindowMs = 350,
                volumeStepCount = 2,
                mappings = mapOf(
                    AirPodsInputGesture.DOUBLE_TAP_PLAY_PAUSE to SystemIntentAction.ANNOUNCE_BATTERY,
                    AirPodsInputGesture.TRIPLE_PRESS to SystemIntentAction.VOLUME_UP
                )
            )
        )

        val consumed = dispatcher.onKeyInput(
            action = KeyEvent.ACTION_DOWN,
            keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            sourceLabel = "Test"
        )
        assertTrue(consumed)
        advanceTimeBy(400L)
        assertEquals(listOf(SystemIntentAction.ANNOUNCE_BATTERY to 2), fakeExecutor.executed)
        assertEquals(SystemIntentAction.ANNOUNCE_BATTERY, dispatcher.lastRecognized.value?.executedAction)

        // Triple headset hook press triggers immediately on 3rd press -> VOLUME_UP with 2 steps
        repeat(3) {
            dispatcher.onKeyInput(
                action = KeyEvent.ACTION_DOWN,
                keyCode = KeyEvent.KEYCODE_HEADSETHOOK,
                sourceLabel = "Test"
            )
        }
        assertEquals(SystemIntentAction.VOLUME_UP to 2, fakeExecutor.executed.last())
        assertEquals(AirPodsInputGesture.TRIPLE_PRESS, dispatcher.lastRecognized.value?.recognizedGesture)
    }

    @Test
    fun `internal dispatch guard ignores synthesized media key events`() {
        val fakeExecutor = FakeExecutor()
        val dispatcher = AirPodsGestureDispatcher(
            executor = fakeExecutor,
            isAirPodsConnectedProvider = { true }
        )

        dispatcher.setInternalDispatchGuard(true)
        val handled = dispatcher.onKeyInput(
            action = KeyEvent.ACTION_DOWN,
            keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            isSimulatedTest = false
        )
        assertFalse(handled)
        assertTrue(fakeExecutor.executed.isEmpty())
    }

    @Test
    fun `consumes ACTION_UP without double counting and honors timed dispatch guard`() = runTest {
        val fakeExecutor = FakeExecutor()
        val dispatcher = AirPodsGestureDispatcher(
            executor = fakeExecutor,
            isAirPodsConnectedProvider = { true }
        )

        val upConsumed = dispatcher.onKeyInput(
            action = KeyEvent.ACTION_UP,
            keyCode = KeyEvent.KEYCODE_MEDIA_NEXT,
            isSimulatedTest = false
        )
        assertTrue(upConsumed)
        assertTrue(fakeExecutor.executed.isEmpty())

        dispatcher.armTimedDispatchGuard(500L)
        val guarded = dispatcher.onKeyInput(
            action = KeyEvent.ACTION_DOWN,
            keyCode = KeyEvent.KEYCODE_MEDIA_NEXT,
            isSimulatedTest = false
        )
        assertFalse(guarded)
        assertTrue(fakeExecutor.executed.isEmpty())
    }
}
