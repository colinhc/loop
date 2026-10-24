package com.example

import android.app.Application
import android.content.Context
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SetPreferences(
    val durationSeconds: Int = 20,
    val startBeepSeconds: Int = 3,
    val endAlarmSeconds: Int = 3,
    val startSound: String = SoundManager.SOUND_BEEP_MED,
    val endSound: String = SoundManager.SOUND_BEEP_HIGH
)

data class TimerUiState(
    val totalSets: Int = 5,
    val selectedSet: Int = 1,
    val isRunning: Boolean = false,
    val activeSetIndex: Int = 1,
    val elapsedSeconds: Float = 0f,
    val setPreferencesList: List<SetPreferences> = List(11) { SetPreferences() },
    val universalStartSound: String = SoundManager.SOUND_BEEP_MED,
    val universalEndSound: String = SoundManager.SOUND_BEEP_HIGH,
    val workouts: List<Workout> = emptyList(),
    val activeWorkoutId: Int? = null,
    val activeWorkoutName: String? = null,
    val backupRestoreMessage: String? = null,
    val isWorkoutActive: Boolean = false,
    val startCounter: Int = 0
)

class TimerViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(TimerUiState())
    val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()

    private val soundManager = SoundManager()
    private var timerJob: Job? = null
    private var lastPlayedSecond: Int = -1

    private var wakeLock: PowerManager.WakeLock? = null

    private fun getWakeLock(): PowerManager.WakeLock {
        if (wakeLock == null) {
            val powerManager = getApplication<Application>().getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WorkoutTimer:TimerWakeLock").apply {
                setReferenceCounted(false)
            }
        }
        return wakeLock!!
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            TimerService.stopService(getApplication())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val database = WorkoutDatabase.getDatabase(application)
    private val repository = WorkoutRepository(database.workoutDao())

    init {
        // Setup initial default values to make the sets distinct and rich
        val initialList = List(11) { i ->
            when (i) {
                1 -> SetPreferences(durationSeconds = 10, startBeepSeconds = 2, endAlarmSeconds = 2, startSound = SoundManager.SOUND_BEEP_MED, endSound = SoundManager.SOUND_BEEP_HIGH)
                2 -> SetPreferences(durationSeconds = 20, startBeepSeconds = 3, endAlarmSeconds = 3, startSound = SoundManager.SOUND_BEEP_HIGH, endSound = SoundManager.SOUND_SWEEP_UP)
                3 -> SetPreferences(durationSeconds = 30, startBeepSeconds = 3, endAlarmSeconds = 3, startSound = SoundManager.SOUND_TICK, endSound = SoundManager.SOUND_DOUBLE_BEEP)
                4 -> SetPreferences(durationSeconds = 40, startBeepSeconds = 3, endAlarmSeconds = 4, startSound = SoundManager.SOUND_BEEP_MED, endSound = SoundManager.SOUND_BEEP_HIGH)
                5 -> SetPreferences(durationSeconds = 50, startBeepSeconds = 3, endAlarmSeconds = 5, startSound = SoundManager.SOUND_BEEP_HIGH, endSound = SoundManager.SOUND_SWEEP_UP)
                else -> SetPreferences(durationSeconds = 20, startBeepSeconds = 3, endAlarmSeconds = 3, startSound = SoundManager.SOUND_BEEP_MED, endSound = SoundManager.SOUND_BEEP_HIGH)
            }
        }
        val sharedPrefs = application.getSharedPreferences("loop_prefs", Context.MODE_PRIVATE)
        val savedStart = sharedPrefs.getString("universal_start_sound", SoundManager.SOUND_BEEP_MED) ?: SoundManager.SOUND_BEEP_MED
        val savedEnd = sharedPrefs.getString("universal_end_sound", SoundManager.SOUND_BEEP_HIGH) ?: SoundManager.SOUND_BEEP_HIGH

        _uiState.update { state ->
            state.copy(
                setPreferencesList = initialList,
                universalStartSound = savedStart,
                universalEndSound = savedEnd
            )
        }

        var hasLoadedLastActive = false

        // Core reactively sync workouts list
        viewModelScope.launch {
            repository.allWorkouts.collect { list ->
                _uiState.update { state ->
                    state.copy(workouts = list)
                }

                if (!hasLoadedLastActive && list.isNotEmpty()) {
                    hasLoadedLastActive = true
                    val sharedPrefs = getApplication<Application>().getSharedPreferences("loop_prefs", Context.MODE_PRIVATE)
                    val lastId = sharedPrefs.getInt("last_active_workout_id", -1)
                    if (lastId != -1) {
                        val matchingWorkout = list.find { it.id == lastId }
                        if (matchingWorkout != null) {
                            loadWorkout(matchingWorkout)
                        }
                    }
                }
            }
        }
    }

    fun setTotalSets(total: Int) {
        _uiState.update { state ->
            val clampedTotal = total.coerceIn(1, 5)
            val nextSelected = when {
                state.selectedSet > clampedTotal -> clampedTotal
                state.selectedSet == 0 -> 1
                else -> state.selectedSet
            }
            val nextActive = if (state.activeSetIndex > clampedTotal) 1 else state.activeSetIndex
            state.copy(
                totalSets = clampedTotal,
                selectedSet = nextSelected,
                activeSetIndex = nextActive
            )
        }
        autoSaveIfNeeded()
    }

    fun setSelectedSet(selected: Int) {
        _uiState.update { state ->
            val maxValid = state.totalSets
            val clampedSelected = selected.coerceIn(1, maxValid)
            state.copy(selectedSet = clampedSelected)
        }
    }

    fun updateSelectedSetPreferences(
        duration: Int? = null,
        startBeeps: Int? = null,
        endAlarms: Int? = null,
        startSound: String? = null,
        endSound: String? = null
    ) {
        _uiState.update { state ->
            val currentIdx = state.selectedSet
            if (currentIdx == 0) return@update state

            val currentPrefs = state.setPreferencesList[currentIdx]
            val newDuration = duration?.coerceIn(10, 300) ?: currentPrefs.durationSeconds
            
            val newStartBeeps = (startBeeps ?: currentPrefs.startBeepSeconds).coerceIn(0, 5)
            val newEndAlarms = (endAlarms ?: currentPrefs.endAlarmSeconds).coerceIn(0, 5).coerceIn(0, newDuration)

            val newPrefs = currentPrefs.copy(
                durationSeconds = newDuration,
                startBeepSeconds = newStartBeeps,
                endAlarmSeconds = newEndAlarms,
                startSound = startSound ?: currentPrefs.startSound,
                endSound = endSound ?: currentPrefs.endSound
            )

            val newList = state.setPreferencesList.toMutableList()
            newList[currentIdx] = newPrefs
            state.copy(setPreferencesList = newList)
        }
        autoSaveIfNeeded()
    }

    fun updateUniversalSounds(startSound: String? = null, endSound: String? = null) {
        _uiState.update { state ->
            state.copy(
                universalStartSound = startSound ?: state.universalStartSound,
                universalEndSound = endSound ?: state.universalEndSound
            )
        }
        val sharedPrefs = getApplication<Application>().getSharedPreferences("loop_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().apply {
            if (startSound != null) putString("universal_start_sound", startSound)
            if (endSound != null) putString("universal_end_sound", endSound)
            apply()
        }
    }

    fun playSound(soundName: String) {
        viewModelScope.launch {
            soundManager.playSound(soundName)
        }
    }

    fun loadWorkout(workout: Workout) {
        val converters = WorkoutTypeConverters()
        val list = converters.toSetPreferencesList(workout.setPreferencesJson)
        val fullList = if (list.size >= 11) list else {
            val mut = list.toMutableList()
            while (mut.size < 11) {
                mut.add(SetPreferences())
            }
            mut
        }
        lastPlayedSecond = -1
        _uiState.update { state ->
            state.copy(
                totalSets = workout.totalSets,
                selectedSet = if (workout.totalSets > 0) 1 else 0,
                activeSetIndex = if (workout.totalSets > 0) 1 else 0,
                elapsedSeconds = 0f,
                setPreferencesList = fullList,
                activeWorkoutId = workout.id,
                activeWorkoutName = workout.name,
                isWorkoutActive = false,
                startCounter = 0
            )
        }
        val sharedPrefs = getApplication<Application>().getSharedPreferences("loop_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putInt("last_active_workout_id", workout.id).apply()
    }

    fun saveWorkout(name: String) {
        viewModelScope.launch {
            val currentState = _uiState.value
            val converters = WorkoutTypeConverters()
            val json = converters.fromSetPreferencesList(currentState.setPreferencesList)
            val maxOrder = currentState.workouts.maxOfOrNull { it.displayOrder } ?: 0
            val workout = Workout(
                name = name,
                totalSets = currentState.totalSets,
                setPreferencesJson = json,
                displayOrder = maxOrder + 1
            )
            val newId = repository.insert(workout).toInt()
            _uiState.update { state ->
                state.copy(
                    activeWorkoutId = newId,
                    activeWorkoutName = name
                )
            }
            val sharedPrefs = getApplication<Application>().getSharedPreferences("loop_prefs", Context.MODE_PRIVATE)
            sharedPrefs.edit().putInt("last_active_workout_id", newId).apply()
        }
    }

    fun updateCurrentWorkout() {
        val currentState = _uiState.value
        val currentId = currentState.activeWorkoutId ?: return
        val currentName = currentState.activeWorkoutName ?: "Custom Workout"
        viewModelScope.launch {
            val converters = WorkoutTypeConverters()
            val json = converters.fromSetPreferencesList(currentState.setPreferencesList)
            val existingWorkout = currentState.workouts.find { it.id == currentId }
            val order = existingWorkout?.displayOrder ?: 0
            val workout = Workout(
                id = currentId,
                name = currentName,
                totalSets = currentState.totalSets,
                setPreferencesJson = json,
                displayOrder = order
            )
            repository.insert(workout)
        }
    }

    private fun autoSaveIfNeeded() {
        val currentState = _uiState.value
        val currentId = currentState.activeWorkoutId ?: return
        val currentName = currentState.activeWorkoutName ?: "Custom Workout"
        viewModelScope.launch {
            val converters = WorkoutTypeConverters()
            val json = converters.fromSetPreferencesList(currentState.setPreferencesList)
            val existingWorkout = currentState.workouts.find { it.id == currentId }
            val order = existingWorkout?.displayOrder ?: 0
            val workout = Workout(
                id = currentId,
                name = currentName,
                totalSets = currentState.totalSets,
                setPreferencesJson = json,
                displayOrder = order
            )
            repository.insert(workout)
        }
    }

    fun reorderWorkouts(fromIndex: Int, toIndex: Int) {
        val currentList = _uiState.value.workouts.toMutableList()
        if (fromIndex !in currentList.indices || toIndex !in currentList.indices) return
        val moved = currentList.removeAt(fromIndex)
        currentList.add(toIndex, moved)
        
        // Optimistically update local UI state index order immediately to prevent visual stuttering
        _uiState.update { it.copy(workouts = currentList) }

        viewModelScope.launch {
            currentList.forEachIndexed { idx, w ->
                val updated = w.copy(displayOrder = idx)
                repository.insert(updated)
            }
        }
    }

    fun deleteWorkout(id: Int) {
        viewModelScope.launch {
            repository.deleteById(id)
            if (_uiState.value.activeWorkoutId == id) {
                val sharedPrefs = getApplication<Application>().getSharedPreferences("loop_prefs", Context.MODE_PRIVATE)
                sharedPrefs.edit().remove("last_active_workout_id").apply()
                _uiState.update { state ->
                    state.copy(
                        activeWorkoutId = null,
                        activeWorkoutName = null
                    )
                }
            }
        }
    }

    fun clearBackupMessage() {
        _uiState.update { it.copy(backupRestoreMessage = null) }
    }

    fun backupWorkoutPlansToInternalStorage(): String {
        val json = WorkoutBackupManager.createBackupJson(_uiState.value.workouts)
        WorkoutBackupManager.saveBackupToInternalStorage(getApplication(), json)
        val count = _uiState.value.workouts.size
        _uiState.update {
            it.copy(backupRestoreMessage = "Backed up $count workout plan(s) to internal storage")
        }
        return json
    }

    fun exportWorkoutPlansToUri(androidUri: android.net.Uri) {
        viewModelScope.launch {
            val json = WorkoutBackupManager.createBackupJson(_uiState.value.workouts)
            val success = WorkoutBackupManager.exportToUri(getApplication(), androidUri, json)
            val msg = if (success) "Exported ${_uiState.value.workouts.size} workout plan(s) to file" else "Failed to export workout plans"
            _uiState.update { it.copy(backupRestoreMessage = msg) }
        }
    }

    fun restoreWorkoutPlansFromInternalStorage(): Int {
        val file = WorkoutBackupManager.getInternalStorageBackupFile(getApplication())
        if (file == null || !file.exists()) {
            _uiState.update { it.copy(backupRestoreMessage = "No internal storage backup file found") }
            return 0
        }
        val json = file.readText()
        return restoreWorkoutPlansFromJson(json)
    }

    fun restoreWorkoutPlansFromUri(androidUri: android.net.Uri) {
        viewModelScope.launch {
            val json = WorkoutBackupManager.readJsonFromUri(getApplication(), androidUri)
            if (json.isNullOrBlank()) {
                _uiState.update { it.copy(backupRestoreMessage = "Failed to read JSON backup file") }
            } else {
                restoreWorkoutPlansFromJson(json)
            }
        }
    }

    fun restoreWorkoutPlansFromJson(jsonString: String): Int {
        val restoredWorkouts = WorkoutBackupManager.parseBackupJson(jsonString)
        if (restoredWorkouts.isEmpty()) {
            _uiState.update { it.copy(backupRestoreMessage = "No valid workout plans found in JSON") }
            return 0
        }
        viewModelScope.launch {
            restoredWorkouts.forEach { workout ->
                repository.insert(workout)
            }
            _uiState.update {
                it.copy(
                    backupRestoreMessage = "Successfully restored ${restoredWorkouts.size} workout plan(s)!"
                )
            }
            // Automatically load first restored workout if none currently active
            if (_uiState.value.activeWorkoutId == null && restoredWorkouts.isNotEmpty()) {
                loadWorkout(restoredWorkouts.first())
            }
        }
        return restoredWorkouts.size
    }

    fun toggleStartPause() {
        if (_uiState.value.isRunning) {
            pauseTimer()
        } else {
            startTimer()
        }
    }

    private fun startTimer() {
        if (_uiState.value.totalSets == 0) return
        val isResumingFromPause = _uiState.value.isWorkoutActive && !_uiState.value.isRunning
        _uiState.update { 
            it.copy(
                isRunning = true, 
                isWorkoutActive = true,
                startCounter = if (isResumingFromPause) it.startCounter else it.startCounter + 1
            ) 
        }
        
        try {
            getWakeLock().acquire(1 * 3600 * 1000L) // 1-hour safe timeout to protect battery
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            TimerService.startService(getApplication())
        } catch (e: Exception) {
            e.printStackTrace()
        }

        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_uiState.value.isRunning) {
                delay(100)
                val state = _uiState.value
                val activeIdx = state.activeSetIndex
                val prefs = state.setPreferencesList[activeIdx]
                val currentElapsed = state.elapsedSeconds
                val nextElapsed = currentElapsed + 0.1f

                val totalDuration = prefs.startBeepSeconds + prefs.durationSeconds

                // Custom stable integer second audio event checking
                val tickSec = currentElapsed.toInt()
                if (tickSec != lastPlayedSecond && tickSec < totalDuration) {
                    lastPlayedSecond = tickSec
                    if (tickSec < prefs.startBeepSeconds) {
                        launch { soundManager.playSound(state.universalStartSound) }
                    } else if (tickSec >= totalDuration - prefs.endAlarmSeconds) {
                        launch { soundManager.playSound(state.universalEndSound) }
                    }
                }

                if (nextElapsed >= totalDuration) {
                    // Current set completed
                    lastPlayedSecond = -1
                    if (activeIdx < state.totalSets) {
                        // Advance to the next set
                        _uiState.update {
                            it.copy(
                                activeSetIndex = activeIdx + 1,
                                elapsedSeconds = 0f
                            )
                        }
                        // Alert sound for starting next set!
                        launch { soundManager.playSound(SoundManager.SOUND_DOUBLE_BEEP, 150) }
                    } else {
                        // All sets finished
                        _uiState.update {
                            it.copy(
                                isRunning = false,
                                activeSetIndex = 1,
                                elapsedSeconds = 0f,
                                isWorkoutActive = false
                            )
                        }
                        timerJob?.cancel()
                        // Play the triple burst sound to signal workout finished
                        viewModelScope.launch {
                            try {
                                soundManager.playSound(SoundManager.SOUND_TRIPLE_BURST)
                            } finally {
                                releaseWakeLock()
                            }
                        }
                    }
                } else {
                    _uiState.update { it.copy(elapsedSeconds = nextElapsed) }
                }
            }
        }
    }

    fun pauseTimer() {
        _uiState.update { it.copy(isRunning = false) }
        timerJob?.cancel()
        releaseWakeLock()
        viewModelScope.launch {
            soundManager.playSound(SoundManager.SOUND_TICK, 10)
        }
    }

    fun resetTimer() {
        pauseTimer()
        releaseWakeLock()
        lastPlayedSecond = -1
        _uiState.update {
            it.copy(
                activeSetIndex = if (it.totalSets > 0) 1 else 0,
                elapsedSeconds = 0f,
                isWorkoutActive = false,
                startCounter = 0
            )
        }
        viewModelScope.launch {
            soundManager.playSound(SoundManager.SOUND_BEEP_LOW, 300)
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        releaseWakeLock()
    }
}
