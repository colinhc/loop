package com.example

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.example.ui.theme.WorkoutTimerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TimerStartCounterTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testStartCounterViewModelLogic() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = TimerViewModel(app)

        // 1. Initial counter must be 0
        assertEquals(0, viewModel.uiState.value.startCounter)
        assertFalse(viewModel.uiState.value.isRunning)
        assertFalse(viewModel.uiState.value.isWorkoutActive)

        // 2. Play pressed to start timer -> counter increments to 1
        viewModel.toggleStartPause()
        assertTrue(viewModel.uiState.value.isRunning)
        assertTrue(viewModel.uiState.value.isWorkoutActive)
        assertEquals(1, viewModel.uiState.value.startCounter)

        // 3. Pause timer -> counter remains 1, isWorkoutActive remains true
        viewModel.toggleStartPause()
        assertFalse(viewModel.uiState.value.isRunning)
        assertTrue(viewModel.uiState.value.isWorkoutActive)
        assertEquals(1, viewModel.uiState.value.startCounter)

        // 4. Play pressed again to resume from pause -> counter does NOT increment, remains 1
        viewModel.toggleStartPause()
        assertTrue(viewModel.uiState.value.isRunning)
        assertEquals(1, viewModel.uiState.value.startCounter)

        // 5. Pause and resume once more -> counter still remains 1
        viewModel.toggleStartPause() // pause
        assertFalse(viewModel.uiState.value.isRunning)
        assertEquals(1, viewModel.uiState.value.startCounter)

        viewModel.toggleStartPause() // resume
        assertTrue(viewModel.uiState.value.isRunning)
        assertEquals(1, viewModel.uiState.value.startCounter)

        // 6. Long pressed to reset -> counter resets to 0, isWorkoutActive becomes false
        viewModel.resetTimer()
        assertFalse(viewModel.uiState.value.isRunning)
        assertFalse(viewModel.uiState.value.isWorkoutActive)
        assertEquals(0, viewModel.uiState.value.startCounter)

        // 7. Start after reset -> counter increments to 1
        viewModel.toggleStartPause()
        assertTrue(viewModel.uiState.value.isRunning)
        assertEquals(1, viewModel.uiState.value.startCounter)
    }

    @Test
    fun testStartCounterUiDisplayAndInteraction() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = TimerViewModel(app)

        composeTestRule.setContent {
            WorkoutTimerTheme {
                WorkoutTimerApp(
                    viewModel = viewModel,
                    enablePulseAnimation = false
                )
            }
        }

        composeTestRule.waitForIdle()

        // 1. Initial counter is in composition and displays "0"
        val counterNode = composeTestRule.onNodeWithTag("timer_start_counter")
        counterNode.assertExists()
        counterNode.assertTextEquals("0")

        // 2. Tap the start/play button -> timer starts, counter increments to "1"
        val playButton = composeTestRule.onNodeWithTag("dial_play_pause_button")
        playButton.performClick()
        composeTestRule.waitForIdle()

        counterNode.assertExists()
        counterNode.assertTextEquals("1")

        // 3. Tap to pause -> counter stays "1"
        playButton.performClick()
        composeTestRule.waitForIdle()

        counterNode.assertExists()
        counterNode.assertTextEquals("1")

        // 4. Tap to resume from pause -> counter must NOT increment, stays "1"
        playButton.performClick()
        composeTestRule.waitForIdle()

        counterNode.assertExists()
        counterNode.assertTextEquals("1")

        // 5. Long click to reset -> counter resets to "0"
        playButton.performTouchInput {
            longClick()
        }
        composeTestRule.waitForIdle()

        counterNode.assertExists()
        counterNode.assertTextEquals("0")

        // 6. Tap start again after reset -> counter increments to "1"
        playButton.performClick()
        composeTestRule.waitForIdle()

        counterNode.assertExists()
        counterNode.assertTextEquals("1")
    }

    @Test
    fun testClockDialCounterDisplayedAtSixOClock() {
        var startCount by mutableStateOf(0)
        var isRunning by mutableStateOf(false)
        var isWorkoutActive by mutableStateOf(false)

        composeTestRule.setContent {
            WorkoutTimerTheme {
                ClockDial(
                    totalSets = 3,
                    activeSetIndex = 1,
                    elapsedSeconds = 0f,
                    preferencesList = List(11) { SetPreferences() },
                    boxWidthPx = 1000f,
                    isRunning = isRunning,
                    onTogglePlay = {
                        val isResumingFromPause = isWorkoutActive && !isRunning
                        if (!isRunning) {
                            if (!isResumingFromPause) startCount++
                            isWorkoutActive = true
                        }
                        isRunning = !isRunning
                    },
                    onReset = {
                        isRunning = false
                        isWorkoutActive = false
                        startCount = 0
                    },
                    startCounter = startCount,
                    enablePulseAnimation = false
                )
            }
        }

        composeTestRule.waitForIdle()

        val counterNode = composeTestRule.onNodeWithTag("timer_start_counter")
        counterNode.assertIsDisplayed()
        counterNode.assertTextEquals("0")

        val playButton = composeTestRule.onNodeWithTag("dial_play_pause_button")
        // Start fresh -> increments to 1
        playButton.performClick()
        composeTestRule.waitForIdle()

        counterNode.assertIsDisplayed()
        counterNode.assertTextEquals("1")

        // Pause -> remains 1
        playButton.performClick()
        composeTestRule.waitForIdle()

        counterNode.assertIsDisplayed()
        counterNode.assertTextEquals("1")

        // Resume from pause -> remains 1
        playButton.performClick()
        composeTestRule.waitForIdle()

        counterNode.assertIsDisplayed()
        counterNode.assertTextEquals("1")

        // Reset -> resets to 0
        playButton.performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        counterNode.assertIsDisplayed()
        counterNode.assertTextEquals("0")
    }
}
