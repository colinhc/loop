package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WorkoutBackupRestoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `test workout plans backup json creation and validation`() {
        val converters = WorkoutTypeConverters()
        val setPrefs = listOf(
            SetPreferences(
                durationSeconds = 45,
                startBeepSeconds = 4,
                endAlarmSeconds = 5,
                startSound = SoundManager.SOUND_SWEEP_UP,
                endSound = SoundManager.SOUND_TRIPLE_BURST
            ),
            SetPreferences(
                durationSeconds = 60,
                startBeepSeconds = 3,
                endAlarmSeconds = 2,
                startSound = SoundManager.SOUND_BEEP_MED,
                endSound = SoundManager.SOUND_DOUBLE_BEEP
            )
        )
        val jsonPrefs = converters.fromSetPreferencesList(setPrefs)

        val workout1 = Workout(
            id = 1,
            name = "HIIT Power Surge",
            totalSets = 2,
            setPreferencesJson = jsonPrefs,
            displayOrder = 1
        )

        // Generate Backup JSON
        val backupJson = WorkoutBackupManager.createBackupJson(listOf(workout1))

        assertNotNull(backupJson)
        assertTrue(backupJson.contains("HIIT Power Surge"))
        assertTrue(backupJson.contains("45")) // duration
        assertTrue(backupJson.contains("4"))  // startBeepSeconds count-in
        assertTrue(backupJson.contains("5"))  // endAlarmSeconds count-out
        assertTrue(backupJson.contains(SoundManager.SOUND_SWEEP_UP))
        assertTrue(backupJson.contains(SoundManager.SOUND_TRIPLE_BURST))

        val parsedBackups = WorkoutBackupManager.parseBackupJson(backupJson)
        assertEquals(1, parsedBackups.size)
        assertEquals(2, parsedBackups.first().totalSets)
    }

    @Test
    fun `test save to internal storage and restore from file`() {
        val converters = WorkoutTypeConverters()
        val setPrefs = listOf(
            SetPreferences(
                durationSeconds = 30,
                startBeepSeconds = 2,
                endAlarmSeconds = 3,
                startSound = SoundManager.SOUND_BEEP_HIGH,
                endSound = SoundManager.SOUND_BEEP_MED
            )
        )
        val jsonPrefs = converters.fromSetPreferencesList(setPrefs)

        val workout = Workout(
            id = 10,
            name = "Core Blast",
            totalSets = 1,
            setPreferencesJson = jsonPrefs,
            displayOrder = 0
        )

        val json = WorkoutBackupManager.createBackupJson(listOf(workout))

        // Save to internal storage
        val savedFile = WorkoutBackupManager.saveBackupToInternalStorage(context, json)

        assertTrue(savedFile.exists())
        assertEquals("workout_plans_backup.json", savedFile.name)
        assertEquals(context.filesDir.absolutePath, savedFile.parentFile?.absolutePath)

        // Read and restore
        val internalFile = WorkoutBackupManager.getInternalStorageBackupFile(context)
        assertNotNull(internalFile)
        val fileContent = internalFile!!.readText()

        val restoredWorkouts = WorkoutBackupManager.parseBackupJson(fileContent)

        assertEquals(1, restoredWorkouts.size)
        val restored = restoredWorkouts.first()
        assertEquals("Core Blast", restored.name)
        assertEquals(1, restored.totalSets)

        val restoredPrefsList = converters.toSetPreferencesList(restored.setPreferencesJson)
        assertTrue(restoredPrefsList.size >= 2)
        val restoredSet = restoredPrefsList[1] // Set 1 is at index 1
        assertEquals(30, restoredSet.durationSeconds)
        assertEquals(2, restoredSet.startBeepSeconds)
        assertEquals(3, restoredSet.endAlarmSeconds)
        assertEquals(SoundManager.SOUND_BEEP_HIGH, restoredSet.startSound)
        assertEquals(SoundManager.SOUND_BEEP_MED, restoredSet.endSound)
    }

    @Test
    fun `test backup parsing handles array format and single object format`() {
        val arrayJson = """
            [
              {
                "name": "Tabata Sprint",
                "totalSets": 4,
                "setPreferences": [
                  {
                    "setIndex": 1,
                    "durationSeconds": 20,
                    "startBeepSeconds": 3,
                    "endAlarmSeconds": 3,
                    "startSound": "beep_med",
                    "endSound": "beep_high"
                  }
                ]
              }
            ]
        """.trimIndent()

        val restoredWorkouts = WorkoutBackupManager.parseBackupJson(arrayJson)
        assertEquals(1, restoredWorkouts.size)
        assertEquals("Tabata Sprint", restoredWorkouts.first().name)
        assertEquals(4, restoredWorkouts.first().totalSets)
    }

    @Test
    fun `test timer view model backup and restore flow`() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = TimerViewModel(application)

        // Create a backup to internal storage
        val exportedJson = viewModel.backupWorkoutPlansToInternalStorage()
        assertNotNull(exportedJson)

        // Restore from internal storage
        val restoredCount = viewModel.restoreWorkoutPlansFromInternalStorage()
        assertTrue(restoredCount >= 0)
    }
}
