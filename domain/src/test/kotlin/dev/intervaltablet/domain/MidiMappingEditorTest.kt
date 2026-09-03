package dev.intervaltablet.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MidiMappingEditorTest {
    private val reducer = MidiMappingEditorReducer()

    @Test
    fun openSnapshotsMutableInputAndStartsASeparateUnchangedDraft() {
        val key = ccKey(number = 12, channel = 3)
        val mutableBindings = linkedMapOf(key to MidiAction.Move(2))
        val mutableThresholds = linkedMapOf(key to 91)
        val expected = MidiMapping(mutableBindings.toMap(), mutableThresholds.toMap())
        val source = MidiMapping(mutableBindings, mutableThresholds)

        val editing = reducer.reduce(
            MidiMappingEditorState.Closed,
            MidiMappingEditorAction.Open(source),
        ).editing()
        mutableBindings.clear()
        mutableThresholds.clear()

        assertEquals(expected, editing.baseline)
        assertEquals(expected, editing.draft)
        assertNotSame(source, editing.baseline)
        assertNotSame(editing.baseline, editing.draft)
        assertFalse(editing.hasChanges)
        assertEquals(MidiMappingCapture.Idle, editing.capture)
    }

    @Test
    fun armRequestsPanicAndFirstNoteOnBecomesStableConsumedCandidate() {
        val opened = open(MidiMapping(emptyMap()))
        val armed = reducer.reduce(opened, MidiMappingEditorAction.Arm(MidiAction.Move(-4)))

        assertEquals(listOf(MidiMappingEditorEvent.PanicRequested), armed.events)
        assertEquals(
            MidiMappingCapture.Armed(MidiAction.Move(-4)),
            armed.editing().capture,
        )

        val captured = reducer.reduce(
            armed.state,
            MidiMappingEditorAction.Receive(MidiMessage.NoteOn(4, 60, 110, 99L)),
        )
        val candidate = captured.candidate()

        assertTrue(captured.inputConsumed)
        assertEquals(noteKey(60, 4), candidate.binding.key)
        assertEquals(MidiAction.Move(-4), candidate.binding.action)
        assertEquals(4, candidate.receivedChannel)
        assertEquals(MidiLearnChannelMode.RECEIVED, candidate.channelMode)
        assertNull(candidate.binding.ccThreshold)
        assertNull(candidate.conflict)
        assertEquals(candidate, captured.singleEvent<MidiMappingEditorEvent.CandidateCaptured>().candidate)

        val second = reducer.reduce(
            captured.state,
            MidiMappingEditorAction.Receive(MidiMessage.NoteOn(4, 61, 100, 100L)),
        )
        assertTrue(second.inputConsumed)
        assertEquals(candidate, second.candidate())
        assertTrue(second.events.isEmpty())

        val program = reducer.reduce(
            second.state,
            MidiMappingEditorAction.Receive(MidiMessage.ProgramChange(4, 8)),
        )
        assertFalse(program.inputConsumed)
        assertEquals(candidate, program.candidate())
    }

    @Test
    fun noteOffIsShieldedWhileArmedButCannotBecomeACandidate() {
        val armed = reducer.reduce(
            open(MidiMapping(emptyMap())),
            MidiMappingEditorAction.Arm(MidiAction.Undo),
        )
        val noteOff = reducer.reduce(
            armed.state,
            MidiMappingEditorAction.Receive(MidiMessage.NoteOff(2, 64)),
        )

        assertTrue(noteOff.inputConsumed)
        assertEquals(MidiMappingCapture.Armed(MidiAction.Undo), noteOff.editing().capture)
        assertTrue(noteOff.events.isEmpty())

        val realtime = reducer.reduce(
            noteOff.state,
            MidiMappingEditorAction.Receive(MidiMessage.RealTime(0xF8)),
        )
        assertFalse(realtime.inputConsumed)
        assertEquals(noteOff.state, realtime.state)
    }

    @Test
    fun ccCaptureUsesReceivedChannelAndConcreteDefaultThreshold() {
        val armed = reducer.reduce(
            open(MidiMapping(emptyMap())),
            MidiMappingEditorAction.Arm(MidiAction.TogglePassThrough),
        )
        val captured = reducer.reduce(
            armed.state,
            MidiMappingEditorAction.Receive(MidiMessage.ControlChange(2, 74, 1, 123L)),
        )
        val candidate = captured.candidate()

        assertTrue(captured.inputConsumed)
        assertEquals(ccKey(74, 2), candidate.binding.key)
        assertEquals(MIDI_MAPPING_DEFAULT_CC_THRESHOLD, candidate.binding.ccThreshold)
        assertEquals(MidiAction.TogglePassThrough, candidate.binding.action)
    }

    @Test
    fun candidateCanSwitchOnlyBetweenReceivedChannelAndOmni() {
        val omni = noteKey(60, null)
        val mapping = MidiMapping(mapOf(omni to MidiAction.Move(1)))
        val captured = captureNote(mapping, channel = 5, note = 60, action = MidiAction.Home(true))

        assertEquals(mapping.bindings[omni], captured.candidate().overlap.omniFallback?.action)

        val omniCandidate = reducer.reduce(
            captured.state,
            MidiMappingEditorAction.SetCandidateChannelMode(MidiLearnChannelMode.OMNI),
        )
        assertNull(omniCandidate.candidate().binding.key.channel)
        assertEquals(MidiLearnChannelMode.OMNI, omniCandidate.candidate().channelMode)
        assertEquals(MidiAction.Move(1), omniCandidate.candidate().conflict?.existing?.action)

        val exactAgain = reducer.reduce(
            omniCandidate.state,
            MidiMappingEditorAction.SetCandidateChannelMode(MidiLearnChannelMode.RECEIVED),
        )
        assertEquals(5, exactAgain.candidate().binding.key.channel)
        assertNull(exactAgain.candidate().conflict)
    }

    @Test
    fun ccThresholdCanBeEditedAcrossItsWholeRangeAndNoteThresholdIsANoOp() {
        var cc = captureCc(MidiMapping(emptyMap()), channel = 1, controller = 7)
        for (threshold in listOf(1, 64, 127)) {
            cc = reducer.reduce(
                cc.state,
                MidiMappingEditorAction.SetCandidateThreshold(threshold),
            )
            assertEquals(threshold, cc.candidate().binding.ccThreshold)
        }

        assertThrows(IllegalArgumentException::class.java) {
            MidiMappingEditorAction.SetCandidateThreshold(0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MidiMappingEditorAction.SetCandidateThreshold(128)
        }

        val note = captureNote(MidiMapping(emptyMap()), channel = 1, note = 60)
        val unchanged = reducer.reduce(
            note.state,
            MidiMappingEditorAction.SetCandidateThreshold(100),
        )
        assertEquals(note.state, unchanged.state)
    }

    @Test
    fun addingCandidateChangesOnlyDraftAndCanonicalizesDefaultCcThreshold() {
        val empty = MidiMapping(emptyMap())
        val captured = captureCc(empty, channel = 6, controller = 21, action = MidiAction.Play)
        val added = reducer.reduce(captured.state, MidiMappingEditorAction.AddCandidate)
        val editing = added.editing()
        val key = ccKey(21, 6)

        assertEquals(empty, editing.baseline)
        assertEquals(MidiAction.Play, editing.draft.bindings[key])
        assertFalse(key in editing.draft.ccThresholds)
        assertEquals(64, editing.draft.ccThreshold(6, 21))
        assertEquals(MidiMappingCapture.Idle, editing.capture)
        assertTrue(editing.hasChanges)
        assertEquals(listOf(MidiMappingEditorEvent.DraftChanged), added.events)
    }

    @Test
    fun customCcThresholdIsStoredAndReplacingItWithDefaultRemovesOverride() {
        val key = ccKey(18, 2)
        val baseline = MidiMapping(
            bindings = mapOf(key to MidiAction.Stop),
            ccThresholds = mapOf(key to 96),
        )
        var captured = captureCc(baseline, channel = 2, controller = 18, action = MidiAction.Record)
        captured = reducer.reduce(
            captured.state,
            MidiMappingEditorAction.SetCandidateThreshold(64),
        )
        val replaced = reducer.reduce(captured.state, MidiMappingEditorAction.ReplaceCandidate)
        val draft = replaced.editing().draft

        assertEquals(MidiAction.Record, draft.bindings[key])
        assertFalse(key in draft.ccThresholds)
        assertEquals(64, draft.ccThreshold(2, 18))

        var custom = captureCc(MidiMapping(emptyMap()), channel = 3, controller = 19)
        custom = reducer.reduce(
            custom.state,
            MidiMappingEditorAction.SetCandidateThreshold(127),
        )
        custom = reducer.reduce(custom.state, MidiMappingEditorAction.AddCandidate)
        assertEquals(127, custom.editing().draft.ccThresholds[ccKey(19, 3)])
    }

    @Test
    fun exactCollisionCannotBeAddedWithoutExplicitReplacement() {
        val key = noteKey(67, 1)
        val baseline = MidiMapping(mapOf(key to MidiAction.Move(2)))
        val captured = captureNote(
            mapping = baseline,
            channel = 1,
            note = 67,
            action = MidiAction.Octave(1),
        )
        val conflict = captured.candidate().conflict

        assertEquals(MidiAction.Move(2), conflict?.existing?.action)

        val refused = reducer.reduce(captured.state, MidiMappingEditorAction.AddCandidate)
        assertEquals(baseline, refused.editing().draft)
        assertEquals(
            conflict,
            refused.singleEvent<MidiMappingEditorEvent.ReplacementRequired>().conflict,
        )

        val replaced = reducer.reduce(refused.state, MidiMappingEditorAction.ReplaceCandidate)
        assertEquals(MidiAction.Octave(1), replaced.editing().draft.bindings[key])
        assertEquals(MidiMappingCapture.Idle, replaced.editing().capture)
    }

    @Test
    fun exactAndOmniOverlapsRemainNonDestructiveAndAreReported() {
        val omni = noteKey(60, null)
        val exactThree = noteKey(60, 3)
        val exactFive = noteKey(60, 5)
        val mapping = MidiMapping(
            mapOf(
                omni to MidiAction.Move(1),
                exactThree to MidiAction.Move(3),
                exactFive to MidiAction.Move(5),
            ),
        )

        val exactCandidate = captureNote(mapping, channel = 4, note = 60, action = MidiAction.Undo)
        assertNull(exactCandidate.candidate().conflict)
        assertEquals(MidiAction.Move(1), exactCandidate.candidate().overlap.omniFallback?.action)

        val exactAdded = reducer.reduce(exactCandidate.state, MidiMappingEditorAction.AddCandidate)
        val exactDraft = exactAdded.editing().draft
        assertEquals(MidiAction.Undo, exactDraft.noteAction(4, 60))
        assertEquals(MidiAction.Move(1), exactDraft.noteAction(2, 60))

        var omniCandidate = captureNote(mapping, channel = 4, note = 60, action = MidiAction.Home(false))
        omniCandidate = reducer.reduce(
            omniCandidate.state,
            MidiMappingEditorAction.SetCandidateChannelMode(MidiLearnChannelMode.OMNI),
        )
        assertEquals(
            listOf(3, 5),
            omniCandidate.candidate().overlap.exactOverrides.map { it.key.channel },
        )
        assertEquals(MidiAction.Move(1), omniCandidate.candidate().conflict?.existing?.action)

        val omniReplaced = reducer.reduce(
            omniCandidate.state,
            MidiMappingEditorAction.ReplaceCandidate,
        ).editing().draft
        assertEquals(MidiAction.Move(3), omniReplaced.noteAction(3, 60))
        assertEquals(MidiAction.Move(5), omniReplaced.noteAction(5, 60))
        assertEquals(MidiAction.Home(false), omniReplaced.noteAction(2, 60))
    }

    @Test
    fun deletingBindingAlsoDeletesThresholdAndReanalyzesPendingConflict() {
        val key = ccKey(74, 7)
        val baseline = MidiMapping(
            bindings = mapOf(key to MidiAction.Move(1)),
            ccThresholds = mapOf(key to 103),
        )
        val captured = captureCc(
            mapping = baseline,
            channel = 7,
            controller = 74,
            action = MidiAction.Move(-1),
        )
        assertEquals(103, captured.candidate().conflict?.existing?.ccThreshold)

        val deleted = reducer.reduce(
            captured.state,
            MidiMappingEditorAction.DeleteBinding(key),
        )
        val editing = deleted.editing()

        assertFalse(key in editing.draft.bindings)
        assertFalse(key in editing.draft.ccThresholds)
        assertNull(deleted.candidate().conflict)
        assertEquals(listOf(MidiMappingEditorEvent.DraftChanged), deleted.events)

        val missing = reducer.reduce(deleted.state, MidiMappingEditorAction.DeleteBinding(key))
        assertEquals(deleted.state, missing.state)
        assertTrue(missing.events.isEmpty())
    }

    @Test
    fun resetChangesOnlyDraftUsesFreshDefaultAndCancelsCapture() {
        val custom = MidiMapping(mapOf(noteKey(1, null) to MidiAction.Stop))
        val armed = reducer.reduce(open(custom), MidiMappingEditorAction.Arm(MidiAction.Play))
        val reset = reducer.reduce(armed.state, MidiMappingEditorAction.ResetDraft)
        val editing = reset.editing()

        assertEquals(custom, editing.baseline)
        assertEquals(DefaultMidiMap.mapping, editing.draft)
        assertNotSame(DefaultMidiMap.mapping, editing.draft)
        assertEquals(MidiMappingCapture.Idle, editing.capture)
        assertTrue(editing.hasChanges)
        assertEquals(listOf(MidiMappingEditorEvent.DraftChanged), reset.events)
    }

    @Test
    fun saveClosesAndEmitsExactlyOneAtomicCommitWhenBaselineIsCurrent() {
        val baseline = MidiMapping(mapOf(noteKey(10, null) to MidiAction.Undo))
        val captured = captureNote(
            mapping = baseline,
            channel = 3,
            note = 11,
            action = MidiAction.Same,
        )
        val added = reducer.reduce(captured.state, MidiMappingEditorAction.AddCandidate)
        val replacement = added.editing().draft

        val saved = reducer.reduce(
            added.state,
            MidiMappingEditorAction.Save(currentMapping = baseline.copy()),
        )
        val commit = saved.singleEvent<MidiMappingEditorEvent.CommitRequested>()

        assertEquals(MidiMappingEditorState.Closed, saved.state)
        assertEquals(baseline, commit.expectedBaseline)
        assertEquals(replacement, commit.replacement)
        assertNotSame(added.editing().baseline, commit.expectedBaseline)
        assertNotSame(added.editing().draft, commit.replacement)
        assertEquals(1, saved.events.size)
    }

    @Test
    fun saveRejectsArmedOrCapturedTransactionsUntilCaptureIsResolved() {
        val baseline = MidiMapping(emptyMap())
        val armed = reducer.reduce(
            open(baseline),
            MidiMappingEditorAction.Arm(MidiAction.Play),
        )
        val armedSave = reducer.reduce(
            armed.state,
            MidiMappingEditorAction.Save(baseline),
        )
        assertEquals(
            MidiMappingSaveRejection.UNRESOLVED_CAPTURE,
            armedSave.singleEvent<MidiMappingEditorEvent.SaveRejected>().reason,
        )
        assertEquals(armed.state, armedSave.state)

        val captured = reducer.reduce(
            armed.state,
            MidiMappingEditorAction.Receive(MidiMessage.NoteOn(0, 60, 100)),
        )
        val capturedSave = reducer.reduce(
            captured.state,
            MidiMappingEditorAction.Save(baseline),
        )
        assertEquals(
            MidiMappingSaveRejection.UNRESOLVED_CAPTURE,
            capturedSave.singleEvent<MidiMappingEditorEvent.SaveRejected>().reason,
        )
        assertEquals(captured.state, capturedSave.state)
    }

    @Test
    fun staleBaselineRejectsSaveWithoutClosingOrOverwritingCurrentMapping() {
        val baseline = MidiMapping(mapOf(noteKey(60, null) to MidiAction.Move(1)))
        val current = MidiMapping(mapOf(noteKey(61, null) to MidiAction.Move(2)))
        val state = open(baseline)

        val rejected = reducer.reduce(
            state,
            MidiMappingEditorAction.Save(currentMapping = current),
        )

        assertEquals(state, rejected.state)
        assertEquals(
            MidiMappingSaveRejection.STALE_BASELINE,
            rejected.singleEvent<MidiMappingEditorEvent.SaveRejected>().reason,
        )
        assertTrue(rejected.events.none { it is MidiMappingEditorEvent.CommitRequested })
    }

    @Test
    fun cancelCaptureKeepsDraftWhileCancelDiscardsTransactionWithoutCommit() {
        val baseline = MidiMapping(mapOf(noteKey(50, null) to MidiAction.Home(true)))
        val armed = reducer.reduce(open(baseline), MidiMappingEditorAction.Arm(MidiAction.Stop))
        val captureCancelled = reducer.reduce(armed.state, MidiMappingEditorAction.CancelCapture)

        assertEquals(baseline, captureCancelled.editing().draft)
        assertEquals(MidiMappingCapture.Idle, captureCancelled.editing().capture)
        assertTrue(captureCancelled.events.isEmpty())

        val cancelled = reducer.reduce(captureCancelled.state, MidiMappingEditorAction.Cancel)
        assertEquals(MidiMappingEditorState.Closed, cancelled.state)
        assertEquals(listOf(MidiMappingEditorEvent.Cancelled), cancelled.events)
        assertTrue(cancelled.events.none { it is MidiMappingEditorEvent.CommitRequested })
    }

    @Test
    fun candidateActionCanChangeAcrossEveryCurrentActionVariant() {
        val actions = listOf(
            MidiAction.Move(-14),
            MidiAction.Chromatic(-127),
            MidiAction.ChromaticShift(12),
            MidiAction.UndoThenMove(14),
            MidiAction.Undo,
            MidiAction.Same,
            MidiAction.SamePitch,
            MidiAction.Random,
            MidiAction.Home(sound = false),
            MidiAction.Octave(10),
            MidiAction.Panic,
            MidiAction.TogglePassThrough,
            MidiAction.Play,
            MidiAction.Stop,
            MidiAction.Record,
        )
        var transition = captureNote(MidiMapping(emptyMap()), channel = 0, note = 1)

        actions.forEach { action ->
            transition = reducer.reduce(
                transition.state,
                MidiMappingEditorAction.SetCandidateAction(action),
            )
            assertEquals(action, transition.candidate().binding.action)
        }
    }

    @Test
    fun closedEditorNeverConsumesInputOrMutatesForEditingCommands() {
        val closed = MidiMappingEditorState.Closed
        val actions = listOf<MidiMappingEditorAction>(
            MidiMappingEditorAction.Receive(MidiMessage.NoteOn(0, 60, 100)),
            MidiMappingEditorAction.Arm(MidiAction.Undo),
            MidiMappingEditorAction.AddCandidate,
            MidiMappingEditorAction.ReplaceCandidate,
            MidiMappingEditorAction.ResetDraft,
            MidiMappingEditorAction.Save(MidiMapping(emptyMap())),
            MidiMappingEditorAction.CancelCapture,
            MidiMappingEditorAction.Cancel,
        )

        actions.forEach { action ->
            val transition = reducer.reduce(closed, action)
            assertEquals(closed, transition.state)
            assertFalse(transition.inputConsumed)
            assertTrue(transition.events.isEmpty())
        }
    }

    private fun open(mapping: MidiMapping): MidiMappingEditorState.Editing {
        return reducer.reduce(
            MidiMappingEditorState.Closed,
            MidiMappingEditorAction.Open(mapping),
        ).editing()
    }

    private fun captureNote(
        mapping: MidiMapping,
        channel: Int,
        note: Int,
        action: MidiAction = MidiAction.Move(1),
    ): MidiMappingEditorTransition {
        val armed = reducer.reduce(open(mapping), MidiMappingEditorAction.Arm(action))
        return reducer.reduce(
            armed.state,
            MidiMappingEditorAction.Receive(MidiMessage.NoteOn(channel, note, 100)),
        )
    }

    private fun captureCc(
        mapping: MidiMapping,
        channel: Int,
        controller: Int,
        action: MidiAction = MidiAction.Move(1),
    ): MidiMappingEditorTransition {
        val armed = reducer.reduce(open(mapping), MidiMappingEditorAction.Arm(action))
        return reducer.reduce(
            armed.state,
            MidiMappingEditorAction.Receive(MidiMessage.ControlChange(channel, controller, 127)),
        )
    }

    private fun MidiMappingEditorTransition.editing(): MidiMappingEditorState.Editing {
        return state as MidiMappingEditorState.Editing
    }

    private fun MidiMappingEditorTransition.candidate(): MidiMappingCandidate {
        return (editing().capture as MidiMappingCapture.Captured).candidate
    }

    private inline fun <reified T : MidiMappingEditorEvent> MidiMappingEditorTransition.singleEvent(): T {
        return events.filterIsInstance<T>().single()
    }

    private fun noteKey(number: Int, channel: Int?): MidiBindingKey {
        return MidiBindingKey(MidiBindingKey.Kind.NOTE, number, channel)
    }

    private fun ccKey(number: Int, channel: Int?): MidiBindingKey {
        return MidiBindingKey(MidiBindingKey.Kind.CC, number, channel)
    }
}
