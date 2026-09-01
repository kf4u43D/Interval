package dev.intervaltablet.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
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
    @OptIn(ExperimentalTestApi::class)
    fun portraitStageSeparatesLeftHarmonyFromRightIntervals() {
        var portraitDensity = 0f
        val state = AppUiState(
            performance = PerformanceCoordinatorState.initial(
                InstrumentConfig(chord = ChordLibrary.triad),
            ),
            settingsLoaded = true,
        )
        composeRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(width = 900.dp, height = 1_440.dp)),
            ) {
                portraitDensity = LocalDensity.current.density
                IntervalTabletTheme {
                    PerformanceScreen(
                        state = state,
                        onIntervalDown = { _, _ -> },
                        onIntervalUp = {},
                        onIntervalOneShot = {},
                        onUndo = {},
                        onHome = {},
                        onPanic = {},
                        onSetScale = {},
                        onSetRoot = {},
                        onSetChord = {},
                        onSetForceToScale = {},
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
        }

        val harmonyBounds = composeRule
            .onNodeWithTag(HarmonyHandPaneTestTag, useUnmergedTree = true)
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        val intervalBounds = composeRule
            .onNodeWithTag(IntervalHandPaneTestTag, useUnmergedTree = true)
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        assertTrue(harmonyBounds.right <= intervalBounds.left)
        assertTrue(harmonyBounds.width > 0f)
        assertTrue(intervalBounds.width > harmonyBounds.width)
        assertTrue(portraitDensity > 0f)
        val minimumTargetPixels = 48f * portraitDensity

        ChordLibrary.all.forEach { chord ->
            composeRule.onNodeWithTag(chordChipTestTag(chord.id), useUnmergedTree = true)
                .assertIsDisplayed()
                .assertHasClickAction()
        }
        PerformanceIntervalSteps.forEach { steps ->
            val padBounds = composeRule
                .onNodeWithTag(intervalPadTestTag(steps), useUnmergedTree = true)
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
            assertTrue(padBounds.left >= intervalBounds.left)
            assertTrue(padBounds.right <= intervalBounds.right)
            assertTrue(padBounds.width >= minimumTargetPixels)
            assertTrue(padBounds.height >= minimumTargetPixels)
        }
        PadArticulation.entries.forEach { articulation ->
            val articulationBounds = composeRule
                .onNodeWithTag(articulationModeTestTag(articulation), useUnmergedTree = true)
                .assertIsDisplayed()
                .assertHasClickAction()
                .fetchSemanticsNode()
                .boundsInRoot
            assertTrue(
                "Portrait articulation $articulation width=${articulationBounds.width}px, minimum=${minimumTargetPixels}px",
                articulationBounds.width >= minimumTargetPixels - 1f,
            )
            assertTrue(
                "Portrait articulation $articulation height=${articulationBounds.height}px, minimum=${minimumTargetPixels}px",
                articulationBounds.height >= minimumTargetPixels - 1f,
            )
        }
        state.instrument.strumNotes().indices.forEach { index ->
            val toneBounds = composeRule
                .onNodeWithTag(strummerToneTestTag(index), useUnmergedTree = true)
                .assertIsDisplayed()
                .assertHasClickAction()
                .fetchSemanticsNode()
                .boundsInRoot
            assertTrue(
                "Portrait strummer tone $index width=${toneBounds.width}px, minimum=${minimumTargetPixels}px",
                toneBounds.width >= minimumTargetPixels - 1f,
            )
            assertTrue(
                "Portrait strummer tone $index height=${toneBounds.height}px, minimum=${minimumTargetPixels}px",
                toneBounds.height >= minimumTargetPixels - 1f,
            )
        }
    }

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
            // Semantics bounds can round one physical pixel below the 48 dp layout constraint.
            assertTrue(
                "Strummer tone $index width=${node.boundsInRoot.width}px, minimum=${48f * density}px",
                node.boundsInRoot.width >= 48f * density - 1f,
            )
            assertTrue(
                "Strummer tone $index height=${node.boundsInRoot.height}px, minimum=${48f * density}px",
                node.boundsInRoot.height >= 48f * density - 1f,
            )
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
