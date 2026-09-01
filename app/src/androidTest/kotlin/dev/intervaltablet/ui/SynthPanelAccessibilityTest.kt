package dev.intervaltablet.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.intervaltablet.AppUiState
import dev.intervaltablet.audio.AudioDiagnostics
import dev.intervaltablet.domain.SynthParameter
import dev.intervaltablet.domain.SynthPatch
import dev.intervaltablet.ui.theme.IntervalTabletTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SynthPanelAccessibilityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<IntervalComposeTestActivity>()

    @Test
    fun synthPanelExposesControlsCommitsFinishedChangesAndClosesUnderLock() {
        var state by mutableStateOf(
            AppUiState(
                settingsLoaded = true,
                audioDiagnostics = AudioDiagnostics(
                    sampleRate = 48_000,
                    framesPerBurst = 192,
                    bufferSizeFrames = 384,
                    streamRunning = true,
                ),
            ),
        )
        val events = mutableListOf<String>()
        var previewedPatch: SynthPatch? = null
        var committedPatch: SynthPatch? = null
        composeRule.setContent {
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
                    onSynthPatchPreview = {
                        previewedPatch = it
                        events += "preview"
                    },
                    onSynthPatchChangeFinished = {
                        committedPatch = it
                        events += "commit"
                    },
                )
            }
        }

        composeRule.onNodeWithTag(SynthPanelOpenTestTag)
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(SynthPanelTestTag).assertIsDisplayed()

        val sliderKeys = listOf("timbre") + listOf(
            SynthParameter.CUTOFF,
            SynthParameter.RESONANCE,
            SynthParameter.ATTACK,
            SynthParameter.DECAY,
            SynthParameter.SUSTAIN,
            SynthParameter.RELEASE,
            SynthParameter.CHORUS_MIX,
            SynthParameter.DELAY_MIX,
            SynthParameter.REVERB_MIX,
            SynthParameter.MASTER,
        ).map(SynthParameter::name)
        sliderKeys.forEach { key ->
            val node = composeRule.onNodeWithTag(synthSliderTestTag(key), useUnmergedTree = true)
                .fetchSemanticsNode()
            assertTrue(node.config[SemanticsProperties.ContentDescription].any(String::isNotBlank))
            assertNotNull(node.config[SemanticsProperties.StateDescription])
        }

        composeRule.onNodeWithTag(synthSliderTestTag("timbre"), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                setProgress(0.8f)
            }
        composeRule.runOnIdle {
            assertNotNull(previewedPatch)
            assertNotNull(committedPatch)
            assertEquals(0.8f, previewedPatch?.pulseMix ?: 0f, 0.001f)
            assertEquals(0.8f, committedPatch?.pulseMix ?: 0f, 0.001f)
            assertEquals(listOf("preview", "commit"), events)
        }

        composeRule.runOnIdle { state = state.copy(performanceLock = true) }
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodesWithTag(SynthPanelTestTag).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithTag(SynthPanelOpenTestTag).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun synthPanelStaysDisabledUntilSettingsHaveLoaded() {
        var state by mutableStateOf(AppUiState(settingsLoaded = false))
        composeRule.setContent {
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

        composeRule.onNodeWithTag(SynthPanelOpenTestTag)
            .assertIsNotEnabled()
        assertTrue(composeRule.onAllNodesWithTag(SynthPanelTestTag).fetchSemanticsNodes().isEmpty())

        composeRule.runOnIdle { state = state.copy(settingsLoaded = true) }
        composeRule.onNodeWithTag(SynthPanelOpenTestTag)
            .assertIsEnabled()
            .performClick()
        composeRule.onNodeWithTag(SynthPanelTestTag).assertIsDisplayed()
    }
}
