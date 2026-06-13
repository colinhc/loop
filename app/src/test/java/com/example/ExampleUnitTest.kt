package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun test_formatSecToMinSec() {
        assertEquals("5s", formatSecToMinSec(5))
        assertEquals("50s", formatSecToMinSec(50))
        assertEquals("1m", formatSecToMinSec(60))
        assertEquals("1m 15s", formatSecToMinSec(75))
        assertEquals("4m 59s", formatSecToMinSec(299))
        assertEquals("5m", formatSecToMinSec(300))
    }

    @Test
    fun test_setPreferences_defaults() {
        val prefs = SetPreferences()
        assertEquals(20, prefs.durationSeconds) // updated default is 20
        assertEquals(3, prefs.startBeepSeconds) // default
        assertEquals(3, prefs.endAlarmSeconds) // default
    }

    @Test
    fun test_workout_type_converters_json() {
        val converters = WorkoutTypeConverters()
        val originalList = List(11) { i ->
            SetPreferences(
                durationSeconds = 10 * i,
                startBeepSeconds = 2,
                endAlarmSeconds = 2
            )
        }
        val json = converters.fromSetPreferencesList(originalList)
        assertTrue(json.isNotEmpty())
        
        val deserializedList = converters.toSetPreferencesList(json)
        assertEquals(11, deserializedList.size)
        assertEquals(0, deserializedList[0].durationSeconds)
        assertEquals(10, deserializedList[1].durationSeconds)
        assertEquals(50, deserializedList[5].durationSeconds)
    }
}
