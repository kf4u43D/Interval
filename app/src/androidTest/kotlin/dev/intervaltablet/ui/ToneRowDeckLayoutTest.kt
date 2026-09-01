package dev.intervaltablet.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ToneRowDeckLayoutTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(ToneRowDeckTestActivity::class.java)

    @Test
    fun aPopulatedTimelineKeepsTheWeightedPerformanceStageVisible() {
        val emptyHeight = activityRule.scenario.remainingStageHeight()
        val oneStepHeight = activityRule.scenario.remainingStageHeight(rowOfSize(1))
        val fullRowHeight = activityRule.scenario.remainingStageHeight(rowOfSize(12))

        assertTrue("The empty deck must leave room for the performance stage", emptyHeight > 0)
        assertTrue("One timeline step must not collapse the performance stage", oneStepHeight >= emptyHeight * 0.8f)
        assertTrue("A full row must not collapse the performance stage", fullRowHeight >= emptyHeight * 0.8f)
    }

    private fun ActivityScenario<ToneRowDeckTestActivity>.remainingStageHeight(
        row: List<ToneRowStepUi>? = null,
    ): Int {
        if (row != null) {
            onActivity {
                ToneRowDeckTestActivity.toneRowState.value =
                    ToneRowDeckTestActivity.toneRowState.value.copy(row = row)
            }
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        var height = 0
        onActivity { activity -> height = activity.remainingStage.height }
        return height
    }

    private fun rowOfSize(size: Int): List<ToneRowStepUi> = List(size) { index ->
        ToneRowStepUi(
            noteLabel = "C${index + 1}",
            degreeLabel = "+${index + 1}",
            velocity = 64,
        )
    }
}
