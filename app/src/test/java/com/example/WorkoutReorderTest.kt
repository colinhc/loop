package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WorkoutReorderTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testReorderWorkouts_ViewModelLogic() = runTest(testDispatcher) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = TimerViewModel(app)

        val initialWorkouts = listOf(
            Workout(id = 1, name = "Workout A", totalSets = 3, setPreferencesJson = "[]", displayOrder = 1),
            Workout(id = 2, name = "Workout B", totalSets = 3, setPreferencesJson = "[]", displayOrder = 2),
            Workout(id = 3, name = "Workout C", totalSets = 3, setPreferencesJson = "[]", displayOrder = 3)
        )

        val database = WorkoutDatabase.getDatabase(app)
        val dao = database.workoutDao()
        
        initialWorkouts.forEach { dao.insertWorkout(it) }

        // Wait for repository flow to collect and populate viewmodel
        val workouts = viewModel.uiState.first { it.workouts.size == 3 }.workouts
        assertEquals("Workout A", workouts[0].name)
        assertEquals("Workout B", workouts[1].name)
        assertEquals("Workout C", workouts[2].name)

        // Perform reorder: Move Workout C (index 2) to first position (index 0)
        viewModel.reorderWorkouts(2, 0)

        // Wait for UI State to be updated and verify correct order
        val updatedWorkouts = viewModel.uiState.first { it.workouts[0].name == "Workout C" }.workouts
        assertEquals(3, updatedWorkouts.size)
        assertEquals("Workout C", updatedWorkouts[0].name)
        assertEquals("Workout A", updatedWorkouts[1].name)
        assertEquals("Workout B", updatedWorkouts[2].name)
    }
}
