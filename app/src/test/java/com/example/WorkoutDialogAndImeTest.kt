package com.example

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.ui.theme.WorkoutTimerTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class WorkoutDialogAndImeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun testMainActivitySoftInputModeIsAdjustResize() {
        val packageManager = context.packageManager
        val componentName = ComponentName(context, MainActivity::class.java)
        val activityInfo = packageManager.getActivityInfo(componentName, PackageManager.GET_META_DATA)

        // Verify adjustResize mode is declared on MainActivity in AndroidManifest.xml
        val adjustMode = activityInfo.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST
        assertEquals(
            "MainActivity must configure windowSoftInputMode to adjustResize to prevent IME insets animation timeout",
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
            adjustMode
        )
    }

    @Test
    fun testSavePlanDialogSaveAndValidationFlow() {
        var savedNameResult: String? = null
        var dismissed = false
        var showDialog by mutableStateOf(true)

        composeTestRule.setContent {
            WorkoutTimerTheme {
                if (showDialog) {
                    SavePlanDialog(
                        onDismiss = {
                            dismissed = true
                            showDialog = false
                        },
                        onSave = {
                            savedNameResult = it
                            showDialog = false
                        }
                    )
                }
            }
        }

        composeTestRule.waitForIdle()

        // Verify dialog title and input field
        composeTestRule.onNodeWithText("CREATE NEW PLAN").assertIsDisplayed()
        val textInputNode = composeTestRule.onNodeWithTag("workout_name_input")
        textInputNode.assertIsDisplayed()

        // 1. Click save without typing anything -> should NOT save
        composeTestRule.onNodeWithTag("save_plan_confirm_button").performClick()
        assertNull("Empty plan name should not trigger onSave", savedNameResult)
        assertTrue(showDialog)
        composeTestRule.onNodeWithText("CREATE NEW PLAN").assertIsDisplayed()

        // 2. Enter plan name and save
        textInputNode.performTextInput("HIIT Beast Mode")

        // Click save button
        composeTestRule.onNodeWithTag("save_plan_confirm_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals("HIIT Beast Mode", savedNameResult)
        assertFalse(dismissed)
        assertFalse(showDialog)
        composeTestRule.onNodeWithText("CREATE NEW PLAN").assertDoesNotExist()
    }

    @Test
    fun testSavePlanDialogComponentCancelFlow() {
        var savedNameResult: String? = null
        var dismissed = false
        var showDialog by mutableStateOf(true)

        composeTestRule.setContent {
            WorkoutTimerTheme {
                if (showDialog) {
                    SavePlanDialog(
                        onDismiss = {
                            dismissed = true
                            showDialog = false
                        },
                        onSave = {
                            savedNameResult = it
                            showDialog = false
                        }
                    )
                }
            }
        }

        composeTestRule.waitForIdle()

        // Click cancel button
        composeTestRule.onNodeWithTag("save_plan_cancel_button").performClick()
        composeTestRule.waitForIdle()

        assertTrue(dismissed)
        assertNull(savedNameResult)
        assertFalse(showDialog)
        composeTestRule.onNodeWithText("CREATE NEW PLAN").assertDoesNotExist()
    }

    @Test
    fun testBackupExportsOnlyActiveSets() {
        val converters = WorkoutTypeConverters()
        val allSlots = List(11) { idx ->
            SetPreferences(
                durationSeconds = if (idx == 1) 30 else if (idx == 2) 45 else 60,
                startBeepSeconds = 3,
                endAlarmSeconds = 2,
                startSound = SoundManager.SOUND_BEEP_MED,
                endSound = SoundManager.SOUND_BEEP_HIGH
            )
        }

        val workout = Workout(
            id = 5,
            name = "Quick 2-Round HIIT",
            totalSets = 2,
            setPreferencesJson = converters.fromSetPreferencesList(allSlots),
            displayOrder = 0
        )

        val json = WorkoutBackupManager.createBackupJson(listOf(workout))
        assertNotNull(json)

        // Verify JSON string contains exactly the 2 active sets and no extra slots
        assertTrue(json.contains("\"setIndex\": 1"))
        assertTrue(json.contains("\"setIndex\": 2"))
        assertFalse(json.contains("\"setIndex\": 3"))
        assertFalse(json.contains("\"setIndex\": 10"))

        val parsedList = WorkoutBackupManager.parseBackupJson(json)
        assertEquals(1, parsedList.size)
        val parsed = parsedList.first()

        assertEquals("Quick 2-Round HIIT", parsed.name)
        assertEquals(2, parsed.totalSets)
        val restoredPrefs = converters.toSetPreferencesList(parsed.setPreferencesJson)
        assertEquals(30, restoredPrefs[1].durationSeconds)
        assertEquals(45, restoredPrefs[2].durationSeconds)
    }

    @Test
    fun testViewModelSoundSettingsUpdate() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = TimerViewModel(application)

        viewModel.updateUniversalSounds(
            startSound = SoundManager.SOUND_SWEEP_UP,
            endSound = SoundManager.SOUND_TRIPLE_BURST
        )

        assertEquals(SoundManager.SOUND_SWEEP_UP, viewModel.uiState.value.universalStartSound)
        assertEquals(SoundManager.SOUND_TRIPLE_BURST, viewModel.uiState.value.universalEndSound)
    }
}
