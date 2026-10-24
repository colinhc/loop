package com.example

import android.app.Application
import android.content.Context
import android.os.PowerManager
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPowerManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TimerScreenOffTest {

    @Test
    fun testTimerKeepsRunningAndAcquiresWakeLockWhenScreenOff() = runBlocking {
        // Use ApplicationProvider context to check wake lock status via PowerManager
        val app = ApplicationProvider.getApplicationContext<Application>()
        val powerManager = app.getSystemService(Context.POWER_SERVICE) as PowerManager

        // Instantiate TimerViewModel directly
        val viewModel = TimerViewModel(app)
        
        // Set total sets and select one
        viewModel.setTotalSets(3)
        viewModel.setSelectedSet(1)

        // Make sure the timer is not running originally
        assertFalse(viewModel.uiState.value.isRunning)

        // Toggle timer: start counting down
        viewModel.toggleStartPause()
        assertTrue(viewModel.uiState.value.isRunning)

        // Launch the activity to simulate lifecycle
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        // Simulating screen off / going to background: move activity lifecycle to CREATED (Stopped)
        scenario.moveToState(Lifecycle.State.CREATED)

        // Verify that the timer is STILL running
        assertTrue(viewModel.uiState.value.isRunning)

        // Wait/advance a bit of time so it actually ticks
        delay(300)

        // Verify it continues to run and has updated elapsedSeconds
        val elapsed = viewModel.uiState.value.elapsedSeconds
        assertTrue("Expected elapsedSeconds to be greater than 0f, but was $elapsed", elapsed > 0)

        // Verify a WakeLock is actively held protecting the CPU from going to sleep
        val latestWakeLock = ShadowPowerManager.getLatestWakeLock()
        val wakeLockStatus = latestWakeLock?.isHeld == true
        assertTrue("Expected a wake lock to be acquired to keep the timer ticking with the screen off", wakeLockStatus)

        // Pause the timer and verify wake lock is released
        viewModel.toggleStartPause()
        assertFalse(viewModel.uiState.value.isRunning)
        assertFalse("Wake lock should be released when timer is paused", latestWakeLock?.isHeld == true)

        scenario.close()
    }
}
