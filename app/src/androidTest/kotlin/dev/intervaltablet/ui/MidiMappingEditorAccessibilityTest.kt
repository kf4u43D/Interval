package dev.intervaltablet.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.intervaltablet.AppUiState
import dev.intervaltablet.domain.MidiAction
import dev.intervaltablet.domain.MidiBindingKey
import dev.intervaltablet.domain.MidiMapping
import dev.intervaltablet.domain.MidiMappingCapture
import dev.intervaltablet.domain.MidiMappingEditorAction
import dev.intervaltablet.domain.MidiMappingEditorEvent
import dev.intervaltablet.domain.MidiMappingEditorReducer
import dev.intervaltablet.domain.MidiMappingEditorState
import dev.intervaltablet.domain.MidiMessage
import dev.intervaltablet.midi.MidiConnectionPhase
import dev.intervaltablet.midi.MidiConnectionState
import dev.intervaltablet.midi.MidiPortDescriptor
import dev.intervaltablet.midi.MidiPortDirection
import dev.intervaltablet.midi.MidiRepositoryState
import dev.intervaltablet.ui.theme.IntervalTabletTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MidiMappingEditorAccessibilityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<IntervalComposeTestActivity>()

    @Test
    fun editorHandlesConflictReplaceSaveAndCancelTransactionally() {
        val source = MidiPortDescriptor(
            deviceId = 31,
            portNumber = 0,
            direction = MidiPortDirection.SOURCE,
            deviceName = "Controller",
            portName = "Out",
        )
        val conflictingKey = MidiBindingKey(
            kind = MidiBindingKey.Kind.CC,
            number = 74,
            channel = 2,
        )
        val baseline = MidiMapping(
            bindings = mapOf(conflictingKey to MidiAction.Stop),
            ccThresholds = mapOf(conflictingKey to 96),
        )
        val reducer = MidiMappingEditorReducer()
        val observedEvents = mutableListOf<MidiMappingEditorEvent>()
        var state by mutableStateOf(
            AppUiState(
                midiMappingEditor = MidiMappingEditorState.Editing(
                    baseline = baseline,
                    draft = baseline,
                ),
                midi = MidiRepositoryState(
                    selectedSource = source,
                    sourceConnection = MidiConnectionState(
                        phase = MidiConnectionPhase.OPEN,
                        descriptor = source,
                        generation = 1,
                    ),
                ),
                settingsLoaded = true,
                hostStarted = true,
            ),
        )
        val dispatch: (MidiMappingEditorAction) -> Unit = { action ->
            val transition = reducer.reduce(state.midiMappingEditor, action)
            observedEvents += transition.events
            state = state.copy(midiMappingEditor = transition.state)
        }
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
                    onMidiMappingEditorAction = dispatch,
                    onSaveMidiMappingEditor = {
                        dispatch(MidiMappingEditorAction.Save(baseline))
                    },
                )
            }
        }

        composeRule.onNodeWithTag(MidiMappingEditorTestTag).assertIsDisplayed()
        composeRule.onNodeWithTag(MidiMappingActionSelectorTestTag).assertIsEnabled()
        composeRule.onNodeWithTag(MidiMappingLearnTestTag)
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        composeRule.onNodeWithTag(MidiMappingActionSelectorTestTag).assertIsNotEnabled()
        composeRule.onNodeWithTag(MidiMappingSaveTestTag).assertIsNotEnabled()

        composeRule.runOnIdle {
            dispatch(
                MidiMappingEditorAction.Receive(
                    MidiMessage.ControlChange(
                        channel = 2,
                        controller = 74,
                        value = 127,
                        timestampNanos = 10L,
                    ),
                ),
            )
        }
        composeRule.onNodeWithTag(MidiMappingActionSelectorTestTag).assertIsEnabled()
        composeRule.onNodeWithTag(MidiMappingSaveTestTag).assertIsNotEnabled()
        composeRule.onNodeWithTag(MidiMappingConflictTestTag)
            .performScrollTo()
            .assertIsDisplayed()
        val thresholdNode = composeRule.onNodeWithTag(
            MidiMappingThresholdTestTag,
            useUnmergedTree = true,
        ).fetchSemanticsNode()
        assertTrue(
            thresholdNode.config[SemanticsProperties.ContentDescription].any(String::isNotBlank),
        )

        composeRule.onNodeWithTag(MidiMappingReplaceTestTag)
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
        composeRule.runOnIdle {
            val editing = state.midiMappingEditor as MidiMappingEditorState.Editing
            assertEquals(MidiMappingCapture.Idle, editing.capture)
            assertEquals(MidiAction.Move(1), editing.draft.bindings[conflictingKey])
            assertEquals(64, editing.draft.ccThreshold(channel = 2, controller = 74))
        }
        composeRule.onNodeWithTag(MidiMappingSaveTestTag)
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(MidiMappingEditorState.Closed, state.midiMappingEditor)
            val commit = observedEvents
                .filterIsInstance<MidiMappingEditorEvent.CommitRequested>()
                .single()
            assertEquals(baseline, commit.expectedBaseline)
            assertEquals(MidiAction.Move(1), commit.replacement.bindings[conflictingKey])
        }
        assertTrue(
            composeRule.onAllNodesWithTag(MidiMappingEditorTestTag)
                .fetchSemanticsNodes()
                .isEmpty(),
        )

        composeRule.runOnIdle {
            dispatch(MidiMappingEditorAction.Open(baseline))
            dispatch(MidiMappingEditorAction.DeleteBinding(conflictingKey))
            val editing = state.midiMappingEditor as MidiMappingEditorState.Editing
            assertTrue(editing.hasChanges)
        }
        composeRule.onNodeWithTag(MidiMappingEditorTestTag).assertIsDisplayed()
        composeRule.onNodeWithTag(MidiMappingCancelTestTag)
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(MidiMappingEditorState.Closed, state.midiMappingEditor)
            assertEquals(
                1,
                observedEvents.filterIsInstance<MidiMappingEditorEvent.CommitRequested>().size,
            )
            assertEquals(MidiMappingEditorEvent.Cancelled, observedEvents.last())
        }

        composeRule.runOnIdle {
            dispatch(MidiMappingEditorAction.Open(baseline))
            state = state.copy(performanceLock = true)
        }
        composeRule.waitForIdle()
        assertTrue(
            composeRule.onAllNodesWithTag(MidiMappingEditorTestTag)
                .fetchSemanticsNodes()
                .isEmpty(),
        )
    }
}
