package com.example

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class DoubleKnobSetsSliderTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testDragOverlappingKnobsRight_selectsBoth() {
        var totalSetsVal by mutableStateOf(3)
        var selectedSetVal by mutableStateOf(3)

        composeTestRule.setContent {
            DoubleKnobSetsSlider(
                totalSets = totalSetsVal,
                selectedSet = selectedSetVal,
                onTotalSetsChange = { totalSetsVal = it },
                onSelectedSetChange = { selectedSetVal = it },
                modifier = Modifier
                    .width(300.dp)
                    .height(84.dp)
                    .testTag("sets_double_slider")
            )
        }

        composeTestRule.waitForIdle()

        // Drag overlapping knobs from tick 3 to the right (tick 4 or 5)
        composeTestRule.onNodeWithTag("sets_double_slider").performTouchInput {
            // center of the slider corresponds to tick 3 out of 1..5
            val startX = width / 2f
            val startY = height / 2f

            down(Offset(startX, startY))
            // Drag to the right
            moveTo(Offset(startX + width / 4f, startY))
            up()
        }

        composeTestRule.waitForIdle()

        // We expect both totalSets and selectedSet to increase
        assertTrue("Expected totalSetsVal to increase", totalSetsVal > 3)
        assertTrue("Expected selectedSetVal to increase", selectedSetVal > 3)
        assertEquals(totalSetsVal, selectedSetVal)
    }

    @Test
    fun testDragOverlappingKnobsLeft_selectsSelectedOnly() {
        var totalSetsVal by mutableStateOf(3)
        var selectedSetVal by mutableStateOf(3)

        composeTestRule.setContent {
            DoubleKnobSetsSlider(
                totalSets = totalSetsVal,
                selectedSet = selectedSetVal,
                onTotalSetsChange = { totalSetsVal = it },
                onSelectedSetChange = { selectedSetVal = it },
                modifier = Modifier
                    .width(300.dp)
                    .height(84.dp)
                    .testTag("sets_double_slider")
            )
        }

        composeTestRule.waitForIdle()

        // Drag overlapping knobs from tick 3 to the left (tick 1 or 2)
        composeTestRule.onNodeWithTag("sets_double_slider").performTouchInput {
            val startX = width / 2f
            val startY = height / 2f

            down(Offset(startX, startY))
            // Drag to the left
            moveTo(Offset(startX - width / 4f, startY))
            up()
        }

        composeTestRule.waitForIdle()

        // We expect only selectedSet to decrease, totalSets remains at 3
        assertEquals(3, totalSetsVal)
        assertTrue("Expected selectedSetVal to decrease", selectedSetVal < 3)
    }
}
