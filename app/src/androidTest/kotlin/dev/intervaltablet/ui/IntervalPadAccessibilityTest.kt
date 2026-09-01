package dev.intervaltablet.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.intervaltablet.AppUiState
import dev.intervaltablet.PerformanceCoordinatorState
import dev.intervaltablet.domain.ChordLibrary
import dev.intervaltablet.domain.InstrumentConfig
import dev.intervaltablet.domain.PadArticulation
import dev.intervaltablet.domain.ScaleDefinition
import dev.intervaltablet.domain.ScaleLibrary
import dev.intervaltablet.domain.strumNotes
import dev.intervaltablet.ui.theme.IntervalTabletTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IntervalPadAccessibilityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<IntervalComposeTestActivity>()

    @Test
    fun nineCompactDrawnPadsRemainDistinctAccessibleFortyEightDpTargets() {
        var oneShotStep: Int? = null
        var selectedChord = ChordLibrary.off
        var selectedScale: ScaleDefinition = ScaleLibrary.major
        var forceToScale = false
        composeRule.setContent {
            IntervalTabletTheme {
                PerformanceScreen(
                    state = AppUiState(settingsLoaded = true),
                    onIntervalDown = { _, _ -> },
                    onIntervalUp = {},
                    onIntervalOneShot = { oneShotStep = it },
                    onUndo = {},
                    onHome = {},
                    onPanic = {},
                    onSetScale = { selectedScale = it },
                    onSetRoot = {},
                    onSetChord = { selectedChord = it },
                    onSetForceToScale = { forceToScale = it },
                    onSetRange = {},
                    onSetWrap = {},
                    onSetInputChannel = {},
                    onSetOutputChannel = {},
                    onSetMode = {},
                    onSelectSource = {},
                    onSelectDestination = {},
                    onResetMidiMapping = {},
                    onToggleAudio = {},
                    onTogglePerformanceLock = {},
                    onDismissStatus = {},
                )
            }
        }

        val density = InstrumentationRegistry.getInstrumentation()
            .targetContext.resources.displayMetrics.density
        val minimumTargetPixels = 48f * density
        PerformanceIntervalSteps.forEach { steps ->
            val node = composeRule.onNodeWithTag(intervalPadTestTag(steps), useUnmergedTree = true)
                .assertIsDisplayed()
                .assertHasClickAction()
                .fetchSemanticsNode()
            assertTrue(node.boundsInRoot.width >= minimumTargetPixels)
            assertTrue(node.boundsInRoot.height >= minimumTargetPixels)
            assertTrue(
                node.config[SemanticsProperties.ContentDescription].any(String::isNotBlank),
            )
            assertNotNull(node.config[SemanticsProperties.StateDescription])
        }

        composeRule.onNodeWithTag(intervalPadTestTag(3), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        ChordLibrary.all.forEach { chord ->
            composeRule.onNodeWithTag(chordChipTestTag(chord.id), useUnmergedTree = true)
                .assertIsDisplayed()
                .assertHasClickAction()
        }
        composeRule.onNodeWithTag(chordChipTestTag(ChordLibrary.triad.id), useUnmergedTree = true)
            .performClick()
        composeRule.onNodeWithTag(scaleChipTestTag(ScaleLibrary.major.id), useUnmergedTree = true)
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onNodeWithTag(ForceToScaleTestTag, useUnmergedTree = true)
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        composeRule.runOnIdle {
            assertEquals(3, oneShotStep)
            assertEquals(ChordLibrary.triad, selectedChord)
            assertEquals(ScaleLibrary.major, selectedScale)
            assertTrue(forceToScale)
        }
    }

    @Test
    fun articulationModesAndStrummerTonesRemainDistinctAccessibleTargets() {
        var selectedArticulation: PadArticulation? = null
        var strumHit: Pair<Int, Int>? = null
        val state = AppUiState(
            performance = PerformanceCoordinatorState.initial(
                InstrumentConfig(chord = ChordLibrary.triad, defaultVelocity = 83),
            ),
            settingsLoaded = true,
        )
        composeRule.setContent {
            IntervalTabletTheme {
                PerformanceScreen(
                    state = state,
                    onSetPadArticulation = { selectedArticulation = it },
                    onStrumTone = { index, velocity -> strumHit = index to velocity },
                    onIntervalDown = { _, _ -> },
                    onIntervalUp = {},
                    onIntervalOneShot = {},
                    onUndo = {},
                    onHome = {},
                    onPanic = {},
                    onSetScale = {},
                    onSetRoot = {},
                    onSetChord = {},
                    onSetRange = {},
                    onSetWrap = {},
                    onSetInputChannel = {},
                    onSetOutputChannel = {},
                    onSetMode = {},
                    onSelectSource = {},
                    onSelectDestination = {},
                    onResetMidiMapping = {},
                    onToggleAudio = {},
                    onTogglePerformanceLock = {},
                    onDismissStatus = {},
                )
            }
        }

        val density = InstrumentationRegistry.getInstrumentation()
            .targetContext.resources.displayMetrics.density
        PadArticulation.entries.forEach { articulation ->
            composeRule.onNodeWithTag(articulationModeTestTag(articulation), useUnmergedTree = true)
                .assertIsDisplayed()
                .assertHasClickAction()
        }
        state.instrument.strumNotes().indices.forEach { index ->
            val node = composeRule.onNodeWithTag(strummerToneTestTag(index), useUnmergedTree = true)
                .assertIsDisplayed()
                .assertHasClickAction()
                .fetchSemanticsNode()
            assertTrue(node.boundsInRoot.width >= 48f * density)
            assertTrue(node.boundsInRoot.height >= 48f * density)
            assertTrue(node.config[SemanticsProperties.ContentDescription].any(String::isNotBlank))
        }

        composeRule.onNodeWithTag(
            articulationModeTestTag(PadArticulation.MUTED),
            useUnmergedTree = true,
        ).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag(strummerToneTestTag(1), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle {
            assertEquals(PadArticulation.MUTED, selectedArticulation)
            assertEquals(1 to 83, strumHit)
        }
    }
}
