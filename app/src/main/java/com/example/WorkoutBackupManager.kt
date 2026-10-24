package com.example

import android.content.Context
import android.net.Uri
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File

data class WorkoutSetBackup(
    val setIndex: Int? = null,
    val durationSeconds: Int = 20,
    val startBeepSeconds: Int = 3,
    val endAlarmSeconds: Int = 3,
    val startSound: String = SoundManager.SOUND_BEEP_MED,
    val endSound: String = SoundManager.SOUND_BEEP_HIGH
)

data class WorkoutPlanBackup(
    val name: String,
    val totalSets: Int,
    val setPreferences: List<WorkoutSetBackup>,
    val displayOrder: Int = 0
)

data class WorkoutBackupContainer(
    val version: Int = 1,
    val appName: String = "Loop Workout Timer",
    val exportTimestamp: Long = System.currentTimeMillis(),
    val plans: List<WorkoutPlanBackup>
)

object WorkoutBackupManager {

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    private val containerAdapter by lazy {
        moshi.adapter(WorkoutBackupContainer::class.java)
    }

    private val planListAdapter by lazy {
        val type = Types.newParameterizedType(List::class.java, WorkoutPlanBackup::class.java)
        moshi.adapter<List<WorkoutPlanBackup>>(type)
    }

    private val singlePlanAdapter by lazy {
        moshi.adapter(WorkoutPlanBackup::class.java)
    }

    const val BACKUP_FILE_NAME = "workout_plans_backup.json"

    /**
     * Converts a list of database Workout entities into a JSON string backup.
     */
    fun createBackupJson(workouts: List<Workout>): String {
        val converters = WorkoutTypeConverters()
        val planBackups = workouts.map { workout ->
            val setPrefs = converters.toSetPreferencesList(workout.setPreferencesJson)
            val numSets = workout.totalSets.coerceAtLeast(0)
            val extractedPrefs = if (setPrefs.size > numSets) {
                // 1-indexed list format used in DB (index 0 is unused, index 1..numSets are the active sets)
                (1..numSets).mapNotNull { idx -> setPrefs.getOrNull(idx) }
            } else {
                // 0-indexed list format
                setPrefs.take(numSets)
            }

            val setBackups = extractedPrefs.mapIndexed { index, pref ->
                WorkoutSetBackup(
                    setIndex = index + 1,
                    durationSeconds = pref.durationSeconds,
                    startBeepSeconds = pref.startBeepSeconds,
                    endAlarmSeconds = pref.endAlarmSeconds,
                    startSound = pref.startSound,
                    endSound = pref.endSound
                )
            }

            WorkoutPlanBackup(
                name = workout.name,
                totalSets = workout.totalSets,
                setPreferences = setBackups,
                displayOrder = workout.displayOrder
            )
        }
        val container = WorkoutBackupContainer(plans = planBackups)
        return containerAdapter.indent("  ").toJson(container)
    }

    /**
     * Saves workout plans JSON into app internal storage directory (filesDir/workout_plans_backup.json).
     * Returns the File object saved.
     */
    fun saveBackupToInternalStorage(context: Context, json: String): File {
        val internalFile = File(context.filesDir, BACKUP_FILE_NAME)
        internalFile.writeText(json)

        // Also save a timestamped copy in filesDir/backups/
        val backupDir = File(context.filesDir, "backups").apply { if (!exists()) mkdirs() }
        val timestampedFile = File(backupDir, "backup_${System.currentTimeMillis()}.json")
        timestampedFile.writeText(json)

        return internalFile
    }

    /**
     * Reads the internal storage backup file if present.
     */
    fun getInternalStorageBackupFile(context: Context): File? {
        val internalFile = File(context.filesDir, BACKUP_FILE_NAME)
        return if (internalFile.exists()) internalFile else null
    }

    /**
     * Parses JSON string (supporting container format, array format, or single plan format)
     * and returns a list of Workout objects ready to insert into Room DB.
     */
    fun parseBackupJson(jsonString: String): List<Workout> {
        if (jsonString.isBlank()) return emptyList()

        val converters = WorkoutTypeConverters()
        val planBackups = mutableListOf<WorkoutPlanBackup>()

        try {
            // Attempt 1: Parse as WorkoutBackupContainer
            val container = containerAdapter.fromJson(jsonString)
            if (container != null && container.plans.isNotEmpty()) {
                planBackups.addAll(container.plans)
            }
        } catch (_: Exception) {
        }

        if (planBackups.isEmpty()) {
            try {
                // Attempt 2: Parse as List<WorkoutPlanBackup>
                val list = planListAdapter.fromJson(jsonString)
                if (!list.isNullOrEmpty()) {
                    planBackups.addAll(list)
                }
            } catch (_: Exception) {
            }
        }

        if (planBackups.isEmpty()) {
            try {
                // Attempt 3: Parse as single WorkoutPlanBackup
                val single = singlePlanAdapter.fromJson(jsonString)
                if (single != null) {
                    planBackups.add(single)
                }
            } catch (_: Exception) {
            }
        }

        return planBackups.mapIndexed { index, planBackup ->
            val effectiveSets = if (planBackup.totalSets > 0) planBackup.totalSets else planBackup.setPreferences.size
            val clampedSets = effectiveSets.coerceIn(1, 10)
            val prefsList = MutableList(11) { SetPreferences() }

            planBackup.setPreferences.forEachIndexed { idx, setBackup ->
                val targetIdx = (setBackup.setIndex ?: (idx + 1)).coerceIn(1, 10)
                if (targetIdx in prefsList.indices) {
                    prefsList[targetIdx] = SetPreferences(
                        durationSeconds = setBackup.durationSeconds.coerceIn(10, 300),
                        startBeepSeconds = setBackup.startBeepSeconds.coerceIn(0, 5),
                        endAlarmSeconds = setBackup.endAlarmSeconds.coerceIn(0, 5),
                        startSound = setBackup.startSound,
                        endSound = setBackup.endSound
                    )
                }
            }

            val setPrefsJson = converters.fromSetPreferencesList(prefsList)
            Workout(
                name = planBackup.name.ifBlank { "Restored Workout ${index + 1}" },
                totalSets = clampedSets,
                setPreferencesJson = setPrefsJson,
                displayOrder = if (planBackup.displayOrder != 0) planBackup.displayOrder else index
            )
        }
    }

    /**
     * Writes backup JSON to a Uri output stream.
     */
    fun exportToUri(context: Context, uri: Uri, json: String): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(json.toByteArray(Charsets.UTF_8))
                outputStream.flush()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Reads backup JSON from a Uri input stream.
     */
    fun readJsonFromUri(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
