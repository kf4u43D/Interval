package dev.intervaltablet

import android.app.Application
import androidx.lifecycle.ViewModelStore
import dev.intervaltablet.audio.AudioDiagnostics
import dev.intervaltablet.audio.AudioMonitor
import dev.intervaltablet.data.MusicalContextSnapshot
import dev.intervaltablet.data.PerformancePresetSnapshot
import dev.intervaltablet.data.PresetBank
import dev.intervaltablet.data.RoutingSnapshot
import dev.intervaltablet.data.SettingsStore
import dev.intervaltablet.data.StoredClockSource
import dev.intervaltablet.data.StoredSettings
import dev.intervaltablet.data.ToneRowEntrySnapshot
import dev.intervaltablet.data.ToneRowPlaybackSnapshotMode
import dev.intervaltablet.data.ToneRowSnapshot
import dev.intervaltablet.data.TransportOptionsSnapshot
import dev.intervaltablet.domain.AudioCommand
import dev.intervaltablet.domain.DefaultMidiMap
import dev.intervaltablet.domain.MidiAction
import dev.intervaltablet.domain.MidiBindingKey
import dev.intervaltablet.domain.MidiMappingCapture
import dev.intervaltablet.domain.MidiMappingEditorAction
import dev.intervaltablet.domain.MidiMappingEditorState
import dev.intervaltablet.domain.MidiMessage
import dev.intervaltablet.domain.PadArticulation
import dev.intervaltablet.domain.PassThroughMode
import dev.intervaltablet.domain.SynthParameter
import dev.intervaltablet.domain.SynthPatch
import dev.intervaltablet.domain.ToneRowMode
import dev.intervaltablet.domain.TransportMode
import dev.intervaltablet.midi.FakeMidiPortRepository
import dev.intervaltablet.midi.MidiInputPacket
import dev.intervaltablet.midi.MidiConnectionLossReason
import dev.intervaltablet.midi.MidiPacketSink
import dev.intervaltablet.midi.MidiPortDescriptor
import dev.intervaltablet.midi.MidiPortDirection
import dev.intervaltablet.ui.ToneRowUiIntent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IntervalTabletViewModelTest {
    private lateinit var scheduler: TestCoroutineScheduler
    private lateinit var dispatcher: TestDispatcher
    private var viewModelStore: ViewModelStore? = null

    @Before
    fun setUp() {
        scheduler = TestCoroutineScheduler()
        dispatcher = StandardTestDispatcher(scheduler)
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        viewModelStore?.clear()
        viewModelStore = null
        scheduler.advanceTimeBy(3_000L)
        scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test
    fun storedWorkingPresetRestoresContentButNeverRuntimePlaybackState() {
        val storedPreset = preset(
            rootPitchClass = 9,
            inputChannel = 4,
            tempoBpm = 73,
            clockSource = StoredClockSource.MIDI,
        )
        val fixture = fixture(
            StoredSettings(
                rootPitchClass = 1,
                workingPreset = storedPreset,
                presetBank = PresetBank(mapOf(4 to preset(rootPitchClass = 2))),
                selectedPresetSlot = 4,
            ),
        )

        val state = fixture.viewModel.uiState.value
        assertTrue(state.settingsLoaded)
        assertEquals(9, state.instrument.config.rootPitchClass)
        assertEquals(4, state.performance.router.inputChannel)
        assertEquals(listOf(60, 62, 64), state.performance.toneRow.entries.map { it.recordedMidiNote })
        assertEquals(ToneRowMode.IDLE, state.performance.toneRow.mode)
        assertEquals(TransportMode.STOPPED, state.performance.transport.mode)
        assertEquals(73, state.performance.transport.tempoBpm)
        assertEquals(dev.intervaltablet.domain.ClockSource.MIDI, state.performance.transport.clockSource)
        assertEquals(0L, state.performance.transport.stepCounter)
        assertEquals(null, state.performance.transport.nextInternalTickNanos)
        assertEquals(4, state.selectedPresetSlot)
    }

    @Test
    fun saveAndRecallIntentsRoundTripPresetAndRecallStopsPlayback() {
        val fixture = fixture(StoredSettings(workingPreset = preset(rootPitchClass = 0)))

        fixture.viewModel.onToneRowIntent(ToneRowUiIntent.SelectPreset(slot = 3))
        drain()
        fixture.viewModel.setRoot(7)
        drain()
        fixture.viewModel.onToneRowIntent(ToneRowUiIntent.SavePreset)
        drain()

        val saved = fixture.viewModel.uiState.value.presetBank[2]
        assertNotNull(saved)
        assertEquals(7, saved?.musicalContext?.rootPitchClass)

        fixture.viewModel.onToneRowIntent(ToneRowUiIntent.PlayPause)
        drain()
        assertEquals(ToneRowMode.AUTO_PLAYING, fixture.viewModel.uiState.value.performance.toneRow.mode)
        assertEquals(TransportMode.PLAYING, fixture.viewModel.uiState.value.performance.transport.mode)

        fixture.viewModel.setRoot(2)
        drain()
        fixture.viewModel.onToneRowIntent(ToneRowUiIntent.RecallPreset)
        drain()
        scheduler.advanceTimeBy(200L)
        drain()

        val restored = fixture.viewModel.uiState.value
        assertEquals(7, restored.instrument.config.rootPitchClass)
        assertEquals(ToneRowMode.IDLE, restored.performance.toneRow.mode)
        assertEquals(TransportMode.STOPPED, restored.performance.transport.mode)
        assertTrue(restored.performance.instrument.activeBySource.isEmpty())
        assertEquals(2, restored.selectedPresetSlot)
        assertEquals(7, fixture.settingsStore.updates.last().workingPreset?.musicalContext?.rootPitchClass)
        assertEquals(7, fixture.settingsStore.updates.last().presetBank[2]?.musicalContext?.rootPitchClass)
    }

    @Test
    fun aRecordedPadMovementAutosavesButItsTransientReleaseDoesNotRewrite() {
        val fixture = fixture(StoredSettings(workingPreset = preset()))
        fixture.viewModel.onToneRowIntent(ToneRowUiIntent.Record)
        drain()
        fixture.settingsStore.updates.clear()

        fixture.viewModel.pressInterval(pointerId = 71L, steps = 1)
        drain()
        assertTrue(fixture.settingsStore.updates.isEmpty())
        scheduler.advanceTimeBy(200L)
        drain()
        assertEquals(1, fixture.settingsStore.updates.size)
        assertEquals(
            1,
            fixture.settingsStore.updates.single().workingPreset?.toneRow?.entries?.size,
        )

        fixture.viewModel.releaseInterval(pointerId = 71L)
        drain()
        assertEquals(1, fixture.settingsStore.updates.size)
    }

    @Test
    fun lateInternalSchedulerCallbackAdvancesExactlyOneStepWithoutCatchUpBurst() {
        val initialTime = 1_000_000_000L
        val fixture = fixture(
            storedSettings = StoredSettings(workingPreset = preset()),
            initialClockNanos = initialTime,
        )
        fixture.viewModel.onHostStart()
        drain()
        fixture.viewModel.onToneRowIntent(ToneRowUiIntent.PlayPause)
        drain()

        val before = fixture.viewModel.uiState.value.performance
        val due = requireNotNull(before.transport.nextInternalTickNanos)
        val stepDuration = before.transport.stepDurationNanos()
        assertEquals(0L, before.transport.stepCounter)

        // Simulate a UI/process stall: the callback itself remains singular even though it is late.
        fixture.clock.timeNanos = due + stepDuration * 8L
        scheduler.advanceTimeBy(ceilNanosToMillis(due - initialTime))
        drain()

        val after = fixture.viewModel.uiState.value.performance
        assertEquals(1L, after.transport.stepCounter)
        assertEquals(1, after.toneRow.rowIndex)
        assertTrue(requireNotNull(after.transport.nextInternalTickNanos) > fixture.clock.timeNanos)
    }

    @Test
    fun programChangeAndSongSelectRecallExistingSlotsBeforeNormalRouting() {
        val bank = PresetBank(
            mapOf(
                5 to preset(rootPitchClass = 7, inputChannel = 2),
                6 to preset(rootPitchClass = 9, inputChannel = 2),
            ),
        )
        val fixture = fixture(
            StoredSettings(
                workingPreset = preset(rootPitchClass = 0, inputChannel = 2),
                presetBank = bank,
            ),
        )
        val midi = connectMidi(fixture)

        assertTrue(
            fixture.packetSink.offer(
                MidiInputPacket(
                    source = midi.source,
                    generation = fixture.midiRepository.state.value.sourceConnection.generation,
                    bytes = byteArrayOf(0xC2.toByte(), 5),
                    timestampNanos = 2_000_000_000L,
                ),
            ),
        )
        drain()
        assertEquals(7, fixture.viewModel.uiState.value.instrument.config.rootPitchClass)
        assertEquals(5, fixture.viewModel.uiState.value.selectedPresetSlot)

        assertTrue(
            fixture.packetSink.offer(
                MidiInputPacket(
                    source = midi.source,
                    generation = fixture.midiRepository.state.value.sourceConnection.generation,
                    bytes = byteArrayOf(0xF3.toByte(), 6),
                    timestampNanos = 2_100_000_000L,
                ),
            ),
        )
        drain()

        val state = fixture.viewModel.uiState.value
        assertEquals(9, state.instrument.config.rootPitchClass)
        assertEquals(6, state.selectedPresetSlot)
        assertEquals(ToneRowMode.IDLE, state.performance.toneRow.mode)
        assertEquals(TransportMode.STOPPED, state.performance.transport.mode)
        assertFalse(
            fixture.midiRepository.sentMessages.any { sent ->
                sent.message is MidiMessage.ProgramChange || sent.message is MidiMessage.SongSelect
            },
        )
    }

    @Test
    fun passThruForwardsProgramAndSongSelectionWithoutRecallingPresets() {
        val fixture = fixture(
            StoredSettings(
                workingPreset = preset(
                    rootPitchClass = 1,
                    passThroughMode = PassThroughMode.PASS_THRU,
                    inputChannel = 3,
                ),
                presetBank = PresetBank(
                    mapOf(
                        7 to preset(rootPitchClass = 10),
                        8 to preset(rootPitchClass = 11),
                    ),
                ),
            ),
        )
        val midi = connectMidi(fixture)

        assertTrue(
            fixture.packetSink.offer(
                MidiInputPacket(
                    source = midi.source,
                    generation = fixture.midiRepository.state.value.sourceConnection.generation,
                    bytes = byteArrayOf(0xC3.toByte(), 7, 0xF3.toByte(), 8),
                    timestampNanos = 3_000_000_000L,
                ),
            ),
        )
        drain()

        val state = fixture.viewModel.uiState.value
        assertEquals(1, state.instrument.config.rootPitchClass)
        assertEquals(0, state.selectedPresetSlot)
        assertTrue(
            fixture.midiRepository.sentMessages.any { sent ->
                sent.message is MidiMessage.ProgramChange && sent.message.program == 7
            },
        )
        assertTrue(
            fixture.midiRepository.sentMessages.any { sent ->
                sent.message is MidiMessage.SongSelect && sent.message.song == 8
            },
        )
    }

    @Test
    fun midiLearnConsumesCapturedNoteBeforeRoutingThenCommitsOnceOnSave() {
        val fixture = fixture(StoredSettings(workingPreset = preset()))
        val midi = connectMidi(fixture)
        scheduler.advanceTimeBy(200L)
        drain()
        fixture.settingsStore.updates.clear()
        val baselineNote = fixture.viewModel.uiState.value.instrument.currentNote

        fixture.viewModel.openMidiMappingEditor()
        fixture.viewModel.onMidiMappingEditorAction(
            MidiMappingEditorAction.Arm(MidiAction.Move(4)),
        )
        drain()
        val sentBeforeCapture = fixture.midiRepository.sentMessages.size
        assertTrue(
            fixture.packetSink.offer(
                MidiInputPacket(
                    source = midi.source,
                    generation = fixture.midiRepository.state.value.sourceConnection.generation,
                    bytes = byteArrayOf(0x90.toByte(), 77, 100),
                    timestampNanos = 3_200_000_000L,
                ),
            ),
        )
        drain()

        val captured = fixture.viewModel.uiState.value
            .midiMappingEditor as MidiMappingEditorState.Editing
        assertEquals(baselineNote, fixture.viewModel.uiState.value.instrument.currentNote)
        assertTrue(captured.capture is MidiMappingCapture.Captured)
        assertEquals(sentBeforeCapture, fixture.midiRepository.sentMessages.size)
        assertTrue(fixture.settingsStore.updates.isEmpty())

        fixture.viewModel.onMidiMappingEditorAction(MidiMappingEditorAction.AddCandidate)
        fixture.viewModel.saveMidiMappingEditor()
        drain()
        assertEquals(MidiMappingEditorState.Closed, fixture.viewModel.uiState.value.midiMappingEditor)
        assertEquals(
            MidiAction.Move(4),
            fixture.viewModel.uiState.value.performance.mapping.bindings[
                MidiBindingKey(MidiBindingKey.Kind.NOTE, 77, channel = 0)
            ],
        )
        assertTrue(
            fixture.packetSink.offer(
                MidiInputPacket(
                    source = midi.source,
                    generation = fixture.midiRepository.state.value.sourceConnection.generation,
                    bytes = byteArrayOf(0x80.toByte(), 77, 0),
                    timestampNanos = 3_200_100_000L,
                ),
            ),
        )
        drain()
        assertEquals(sentBeforeCapture, fixture.midiRepository.sentMessages.size)
        assertEquals(baselineNote, fixture.viewModel.uiState.value.instrument.currentNote)
        scheduler.advanceTimeBy(200L)
        drain()
        assertEquals(1, fixture.settingsStore.updates.size)
    }

    @Test
    fun cancellingMidiLearnDiscardsDraftWithoutPersistence() {
        val fixture = fixture(StoredSettings(workingPreset = preset()))
        connectMidi(fixture)
        scheduler.advanceTimeBy(200L)
        drain()
        fixture.settingsStore.updates.clear()
        val deleted = MidiBindingKey(MidiBindingKey.Kind.NOTE, 77, channel = null)

        fixture.viewModel.openMidiMappingEditor()
        fixture.viewModel.onMidiMappingEditorAction(MidiMappingEditorAction.DeleteBinding(deleted))
        drain()
        val editing = fixture.viewModel.uiState.value
            .midiMappingEditor as MidiMappingEditorState.Editing
        assertFalse(editing.draft.bindings.containsKey(deleted))

        fixture.viewModel.onMidiMappingEditorAction(MidiMappingEditorAction.Cancel)
        drain()
        scheduler.advanceTimeBy(200L)
        drain()

        assertEquals(MidiMappingEditorState.Closed, fixture.viewModel.uiState.value.midiMappingEditor)
        assertEquals(DefaultMidiMap.mapping, fixture.viewModel.uiState.value.performance.mapping)
        assertTrue(fixture.settingsStore.updates.isEmpty())
    }

    @Test
    fun acceptedCcLearnGestureSuppressesHeldAndReleasePacketsAfterSave() {
        val fixture = fixture(
            StoredSettings(
                workingPreset = preset(passThroughMode = PassThroughMode.PASS_THRU),
            ),
        )
        val midi = connectMidi(fixture)
        val baselineNote = fixture.viewModel.uiState.value.instrument.currentNote

        fixture.viewModel.openMidiMappingEditor()
        fixture.viewModel.onMidiMappingEditorAction(
            MidiMappingEditorAction.Arm(MidiAction.Move(3)),
        )
        drain()
        val sentBeforeCapture = fixture.midiRepository.sentMessages.size
        assertTrue(
            fixture.packetSink.offer(
                MidiInputPacket(
                    source = midi.source,
                    generation = fixture.midiRepository.state.value.sourceConnection.generation,
                    bytes = byteArrayOf(0xB0.toByte(), 74, 127),
                    timestampNanos = 3_250_000_000L,
                ),
            ),
        )
        drain()
        fixture.viewModel.onMidiMappingEditorAction(
            MidiMappingEditorAction.SetCandidateThreshold(100),
        )
        fixture.viewModel.onMidiMappingEditorAction(MidiMappingEditorAction.AddCandidate)
        fixture.viewModel.saveMidiMappingEditor()
        drain()

        assertTrue(
            fixture.packetSink.offer(
                MidiInputPacket(
                    source = midi.source,
                    generation = fixture.midiRepository.state.value.sourceConnection.generation,
                    bytes = byteArrayOf(0xB0.toByte(), 74, 110),
                    timestampNanos = 3_250_100_000L,
                ),
            ),
        )
        assertTrue(
            fixture.packetSink.offer(
                MidiInputPacket(
                    source = midi.source,
                    generation = fixture.midiRepository.state.value.sourceConnection.generation,
                    bytes = byteArrayOf(0xB0.toByte(), 74, 80),
                    timestampNanos = 3_250_200_000L,
                ),
            ),
        )
        drain()

        assertEquals(sentBeforeCapture, fixture.midiRepository.sentMessages.size)
        assertEquals(baselineNote, fixture.viewModel.uiState.value.instrument.currentNote)
        assertEquals(PassThroughMode.PASS_THRU, fixture.viewModel.uiState.value.passThroughMode)
        assertEquals(0, fixture.viewModel.uiState.value.selectedPresetSlot)

        assertTrue(
            fixture.packetSink.offer(
                MidiInputPacket(
                    source = midi.source,
                    generation = fixture.midiRepository.state.value.sourceConnection.generation,
                    bytes = byteArrayOf(0xB0.toByte(), 74, 127),
                    timestampNanos = 3_250_300_000L,
                ),
            ),
        )
        drain()
        assertEquals(sentBeforeCapture + 1, fixture.midiRepository.sentMessages.size)
    }

    @Test
    fun sourceChangeAndLossCloseMidiLearnAndDiscardEachDraft() {
        val fixture = fixture(StoredSettings(workingPreset = preset()))
        val midi = connectMidi(fixture)
        val alternateSource = MidiPortDescriptor(
            deviceId = 13,
            portNumber = 2,
            direction = MidiPortDirection.SOURCE,
            deviceName = "Alternate source",
            portName = "Out",
        )
        fixture.midiRepository.setPorts(
            listOf(midi.source, alternateSource) +
                requireNotNull(fixture.midiRepository.state.value.selectedDestination),
        )
        drain()

        fixture.viewModel.openMidiMappingEditor()
        fixture.viewModel.onMidiMappingEditorAction(
            MidiMappingEditorAction.DeleteBinding(
                MidiBindingKey(MidiBindingKey.Kind.NOTE, 77, channel = null),
            ),
        )
        drain()
        fixture.viewModel.selectSource(alternateSource)
        drain()

        assertEquals(MidiMappingEditorState.Closed, fixture.viewModel.uiState.value.midiMappingEditor)
        assertEquals(DefaultMidiMap.mapping, fixture.viewModel.uiState.value.performance.mapping)

        fixture.viewModel.openMidiMappingEditor()
        fixture.viewModel.onMidiMappingEditorAction(
            MidiMappingEditorAction.DeleteBinding(
                MidiBindingKey(MidiBindingKey.Kind.NOTE, 78, channel = null),
            ),
        )
        drain()
        fixture.midiRepository.simulateConnectionLoss(
            MidiPortDirection.SOURCE,
            MidiConnectionLossReason.PORT_DISAPPEARED,
        )
        drain()

        assertEquals(MidiMappingEditorState.Closed, fixture.viewModel.uiState.value.midiMappingEditor)
        assertEquals(DefaultMidiMap.mapping, fixture.viewModel.uiState.value.performance.mapping)
    }

    @Test
    fun lockMidiRecallAndHostStopCloseEditorAndDiscardDraft() {
        val fixture = fixture(
            StoredSettings(
                workingPreset = preset(rootPitchClass = 0),
                presetBank = PresetBank(
                    mapOf(
                        5 to preset(rootPitchClass = 7),
                        6 to preset(rootPitchClass = 9),
                    ),
                ),
            ),
        )
        val midi = connectMidi(fixture)
        val deleted = MidiBindingKey(MidiBindingKey.Kind.NOTE, 77, channel = null)
        fun openChangedDraft() {
            fixture.viewModel.openMidiMappingEditor()
            fixture.viewModel.onMidiMappingEditorAction(
                MidiMappingEditorAction.DeleteBinding(deleted),
            )
            drain()
            val editing = fixture.viewModel.uiState.value
                .midiMappingEditor as MidiMappingEditorState.Editing
            assertFalse(editing.draft.bindings.containsKey(deleted))
        }

        openChangedDraft()
        fixture.viewModel.togglePerformanceLock()
        drain()
        assertEquals(MidiMappingEditorState.Closed, fixture.viewModel.uiState.value.midiMappingEditor)
        assertEquals(DefaultMidiMap.mapping, fixture.viewModel.uiState.value.performance.mapping)
        fixture.viewModel.togglePerformanceLock()
        drain()

        openChangedDraft()
        assertTrue(
            fixture.packetSink.offer(
                MidiInputPacket(
                    source = midi.source,
                    generation = fixture.midiRepository.state.value.sourceConnection.generation,
                    bytes = byteArrayOf(0xC0.toByte(), 5),
                    timestampNanos = 3_275_000_000L,
                ),
            ),
        )
        drain()
        assertEquals(MidiMappingEditorState.Closed, fixture.viewModel.uiState.value.midiMappingEditor)
        assertEquals(7, fixture.viewModel.uiState.value.instrument.config.rootPitchClass)
        assertEquals(DefaultMidiMap.mapping, fixture.viewModel.uiState.value.performance.mapping)

        openChangedDraft()
        assertTrue(
            fixture.packetSink.offer(
                MidiInputPacket(
                    source = midi.source,
                    generation = fixture.midiRepository.state.value.sourceConnection.generation,
                    bytes = byteArrayOf(0xF3.toByte(), 6),
                    timestampNanos = 3_275_100_000L,
                ),
            ),
        )
        drain()
        assertEquals(MidiMappingEditorState.Closed, fixture.viewModel.uiState.value.midiMappingEditor)
        assertEquals(9, fixture.viewModel.uiState.value.instrument.config.rootPitchClass)
        assertEquals(DefaultMidiMap.mapping, fixture.viewModel.uiState.value.performance.mapping)

        openChangedDraft()
        fixture.viewModel.onHostStop()
        drain()
        assertEquals(MidiMappingEditorState.Closed, fixture.viewModel.uiState.value.midiMappingEditor)
        assertEquals(DefaultMidiMap.mapping, fixture.viewModel.uiState.value.performance.mapping)
    }

    @Test
    fun mailboxOverflowRecoveryClosesMidiLearnAndDiscardsDraft() {
        val fixture = fixture(StoredSettings(workingPreset = preset()))
        val midi = connectMidi(fixture)
        fixture.viewModel.openMidiMappingEditor()
        fixture.viewModel.onMidiMappingEditorAction(
            MidiMappingEditorAction.DeleteBinding(
                MidiBindingKey(MidiBindingKey.Kind.NOTE, 77, channel = null),
            ),
        )
        drain()
        var rejectedPackets = 0
        repeat(400) {
            val accepted = fixture.packetSink.offer(
                MidiInputPacket(
                    source = midi.source,
                    generation = fixture.midiRepository.state.value.sourceConnection.generation,
                    bytes = byteArrayOf(0xFE.toByte()),
                    timestampNanos = 3_300_000_000L + it,
                ),
            )
            if (!accepted) rejectedPackets += 1
        }
        assertTrue(rejectedPackets > 0)
        drain()

        val state = fixture.viewModel.uiState.value
        assertEquals(MidiMappingEditorState.Closed, state.midiMappingEditor)
        assertEquals(DefaultMidiMap.mapping, state.performance.mapping)
        assertTrue(state.droppedCoordinatorCommands > 0L)
    }

    @Test
    fun hostStopPanicsNotesDisconnectsPortsAndStopsAudioIdempotently() {
        val fixture = fixture(StoredSettings(workingPreset = preset()))
        connectMidi(fixture)
        assertTrue(fixture.viewModel.uiState.value.audioRunning)

        fixture.viewModel.onToneRowIntent(ToneRowUiIntent.PlayPause)
        fixture.viewModel.pressInterval(pointerId = 41L, steps = 1)
        drain()
        assertTrue(fixture.viewModel.uiState.value.performance.instrument.activeInstanceCount > 0)

        fixture.viewModel.onHostStop()
        drain()

        val stopped = fixture.viewModel.uiState.value
        assertFalse(stopped.hostStarted)
        assertFalse(stopped.audioRunning)
        assertEquals(ToneRowMode.IDLE, stopped.performance.toneRow.mode)
        assertEquals(TransportMode.STOPPED, stopped.performance.transport.mode)
        assertEquals(0, stopped.performance.instrument.activeInstanceCount)
        assertEquals(null, stopped.midi.selectedSource)
        assertEquals(null, stopped.midi.selectedDestination)
        assertTrue(fixture.audio.commands.contains(AudioCommand.Panic))
        assertEquals(1, fixture.audio.stopCalls)

        fixture.viewModel.onHostStop()
        drain()
        assertEquals(1, fixture.audio.stopCalls)
    }

    @Test
    fun actorAndAudioProgressWhileMainAndPersistenceDispatchersAreStalled() {
        val actorScheduler = TestCoroutineScheduler()
        val persistenceScheduler = TestCoroutineScheduler()
        val actor = StandardTestDispatcher(actorScheduler)
        val persistence = StandardTestDispatcher(persistenceScheduler)
        val clock = FakeClock(1_000_000L)
        val audio = FakeAudioMonitor()
        val midiRepository = FakeMidiPortRepository()
        val settingsStore = FakeSettingsStore(StoredSettings(workingPreset = preset()))
        val viewModel = IntervalTabletViewModel(
            application = Application(),
            clock = clock,
            audioEngine = audio,
            midiRepositoryFactory = { midiRepository },
            settingsStoreFactory = { settingsStore },
            actorDispatcher = actor,
            persistenceDispatcher = persistence,
            diagnosticsDispatcher = actor,
        )
        viewModelStore = ViewModelStore().also { store -> store.put("subject", viewModel) }

        actorScheduler.runCurrent()
        assertFalse(viewModel.uiState.value.settingsLoaded)
        persistenceScheduler.runCurrent()
        actorScheduler.runCurrent()
        assertTrue(viewModel.uiState.value.settingsLoaded)

        viewModel.onHostStart()
        viewModel.setRoot(7)
        viewModel.onToneRowIntent(ToneRowUiIntent.ChangeTempo(deltaBpm = 17))
        viewModel.togglePerformanceLock()
        viewModel.pressInterval(pointerId = 101L, steps = 1)
        actorScheduler.runCurrent()

        assertTrue(viewModel.uiState.value.hostStarted)
        assertEquals(7, viewModel.uiState.value.instrument.config.rootPitchClass)
        assertTrue(audio.commands.any { it is AudioCommand.NoteOn })
        assertTrue("Persistence must not gate musical commands", settingsStore.updates.isEmpty())

        persistenceScheduler.advanceTimeBy(200L)
        persistenceScheduler.runCurrent()
        assertEquals(1, settingsStore.updates.size)
        val persisted = settingsStore.updates.single()
        assertEquals(7, persisted.workingPreset?.musicalContext?.rootPitchClass)
        assertEquals(137, persisted.workingPreset?.transport?.tempoBpm)
        assertTrue(persisted.performanceLock)

        requireNotNull(viewModelStore).clear()
        viewModelStore = null
        persistenceScheduler.advanceTimeBy(3_000L)
        persistenceScheduler.runCurrent()
        actorScheduler.runCurrent()
    }

    @Test
    fun oneShotReleaseIsScheduledOnlyAfterItsPressIsApplied() {
        val fixture = fixture(StoredSettings(workingPreset = preset()))
        fixture.viewModel.onHostStart()
        drain()

        fixture.viewModel.triggerInterval(1)
        drain()
        assertTrue(fixture.viewModel.uiState.value.performance.instrument.activeInstanceCount > 0)
        assertTrue(fixture.audio.commands.any { it is AudioCommand.NoteOn })

        scheduler.advanceTimeBy(89L)
        drain()
        assertTrue(fixture.viewModel.uiState.value.performance.instrument.activeInstanceCount > 0)

        scheduler.advanceTimeBy(1L)
        drain()
        assertEquals(0, fixture.viewModel.uiState.value.performance.instrument.activeInstanceCount)
        assertTrue(fixture.audio.commands.any { it is AudioCommand.NoteOff })
    }

    @Test
    fun storedSynthPatchIsReplayedAfterEveryAcceptedAudioStart() {
        val patch = SynthPatch()
            .withTimbre(0.72F)
            .withParameter(SynthParameter.CUTOFF, 8_400.0F)
        val fixture = fixture(
            StoredSettings(
                synthPatch = patch,
                workingPreset = preset(),
            ),
        )

        assertEquals(patch, fixture.viewModel.uiState.value.synthPatch)
        assertTrue(fixture.audio.commands.isEmpty())

        fixture.viewModel.onHostStart()
        drain()
        assertTrue(fixture.viewModel.uiState.value.audioRunning)
        assertEquals(patch.toAudioCommands(), fixture.audio.commands.filterIsInstance<AudioCommand.Parameter>())

        fixture.viewModel.toggleAudioMonitor()
        drain()
        fixture.viewModel.toggleAudioMonitor()
        drain()
        assertEquals(
            patch.toAudioCommands() + patch.toAudioCommands(),
            fixture.audio.commands.filterIsInstance<AudioCommand.Parameter>(),
        )
    }

    @Test
    fun synthPatchCommitIsSerializedPersistedAndIndependentFromPresetRecall() {
        val initialPatch = SynthPatch().withTimbre(0.31F)
        val fixture = fixture(
            StoredSettings(
                synthPatch = initialPatch,
                workingPreset = preset(),
                presetBank = PresetBank(mapOf(0 to preset(rootPitchClass = 7))),
                selectedPresetSlot = 0,
            ),
        )
        fixture.viewModel.onHostStart()
        drain()
        fixture.audio.commands.clear()
        fixture.settingsStore.updates.clear()

        val changedPatch = initialPatch
            .withParameter(SynthParameter.ATTACK, 0.025F)
            .withParameter(SynthParameter.REVERB_MIX, 0.44F)
            .withParameter(SynthParameter.MASTER, 0.52F)
        fixture.viewModel.setSynthPatch(changedPatch)
        drain()

        assertEquals(changedPatch, fixture.viewModel.uiState.value.synthPatch)
        assertEquals(changedPatch.toAudioCommands(), fixture.audio.commands)
        scheduler.advanceTimeBy(200L)
        drain()
        assertEquals(changedPatch, fixture.settingsStore.updates.last().synthPatch)

        fixture.audio.commands.clear()
        fixture.viewModel.onToneRowIntent(ToneRowUiIntent.RecallPreset)
        drain()
        assertEquals(7, fixture.viewModel.uiState.value.instrument.config.rootPitchClass)
        assertEquals(changedPatch, fixture.viewModel.uiState.value.synthPatch)
        assertTrue(fixture.audio.commands.contains(AudioCommand.Panic))
        assertTrue(fixture.audio.commands.none { it is AudioCommand.Parameter })
    }

    @Test
    fun synthPatchPreviewStreamsOnlyDeltasAndPersistsOnlyTheFinishedValue() {
        val initialPatch = SynthPatch()
        val fixture = fixture(
            StoredSettings(
                synthPatch = initialPatch,
                workingPreset = preset(),
            ),
        )
        fixture.viewModel.onHostStart()
        drain()
        fixture.audio.commands.clear()
        fixture.settingsStore.updates.clear()

        val firstPreview = initialPatch.withParameter(SynthParameter.ATTACK, 0.025F)
        fixture.viewModel.previewSynthPatch(firstPreview)
        drain()

        assertEquals(initialPatch, fixture.viewModel.uiState.value.synthPatch)
        assertEquals(
            firstPreview.changedAudioCommandsSince(initialPatch),
            fixture.audio.commands,
        )
        scheduler.advanceTimeBy(200L)
        drain()
        assertTrue(fixture.settingsStore.updates.isEmpty())

        val finalPatch = firstPreview
            .withParameter(SynthParameter.ATTACK, 0.041F)
            .withParameter(SynthParameter.REVERB_MIX, 0.44F)
        fixture.viewModel.previewSynthPatch(finalPatch)
        drain()
        assertEquals(
            firstPreview.changedAudioCommandsSince(initialPatch) +
                finalPatch.changedAudioCommandsSince(firstPreview),
            fixture.audio.commands,
        )

        fixture.viewModel.setSynthPatch(finalPatch)
        drain()
        assertEquals(finalPatch, fixture.viewModel.uiState.value.synthPatch)
        assertEquals(
            finalPatch.toAudioCommands(),
            fixture.audio.commands.takeLast(SynthParameter.entries.size),
        )
        assertTrue(fixture.settingsStore.updates.isEmpty())

        scheduler.advanceTimeBy(200L)
        drain()
        assertEquals(finalPatch, fixture.settingsStore.updates.single().synthPatch)
    }

    @Test
    fun synthPatchPreviewReturningToTheStoredValueRestoresTheAuthoritativePatch() {
        val initialPatch = SynthPatch()
        val fixture = fixture(
            StoredSettings(
                synthPatch = initialPatch,
                workingPreset = preset(),
            ),
        )
        fixture.viewModel.onHostStart()
        drain()
        fixture.audio.commands.clear()
        fixture.settingsStore.updates.clear()

        fixture.viewModel.previewSynthPatch(
            initialPatch.withParameter(SynthParameter.CUTOFF, 9_500.0F),
        )
        drain()
        fixture.viewModel.setSynthPatch(initialPatch)
        drain()

        assertEquals(initialPatch, fixture.viewModel.uiState.value.synthPatch)
        assertEquals(
            initialPatch.toAudioCommands(),
            fixture.audio.commands.takeLast(SynthParameter.entries.size),
        )
        scheduler.advanceTimeBy(200L)
        drain()
        assertTrue(fixture.settingsStore.updates.isEmpty())
    }

    @Test
    fun heldPadArpeggiatesAtTheConfiguredStepWithoutTransportOrToneRow() {
        val working = preset().copy(
            musicalContext = MusicalContextSnapshot(
                chordId = "triad",
                padArticulation = PadArticulation.ARPEGGIATED,
            ),
            toneRow = ToneRowSnapshot(),
            transport = TransportOptionsSnapshot(tempoBpm = 120, clocksPerStep = 6),
        )
        val fixture = fixture(StoredSettings(workingPreset = working))
        fixture.viewModel.onHostStart()
        drain()
        fixture.audio.commands.clear()

        fixture.viewModel.pressInterval(pointerId = 501L, steps = 0)
        drain()
        assertEquals(ToneRowMode.IDLE, fixture.viewModel.uiState.value.performance.toneRow.mode)
        assertEquals(TransportMode.STOPPED, fixture.viewModel.uiState.value.performance.transport.mode)
        assertTrue(fixture.viewModel.uiState.value.performance.toneRow.entries.isEmpty())
        assertEquals(
            listOf(60),
            fixture.audio.commands.filterIsInstance<AudioCommand.NoteOn>().map { it.note },
        )

        scheduler.advanceTimeBy(124L)
        drain()
        assertEquals(
            listOf(60),
            fixture.audio.commands.filterIsInstance<AudioCommand.NoteOn>().map { it.note },
        )
        assertEquals(0, fixture.viewModel.uiState.value.instrument.activeInstanceCount)

        scheduler.advanceTimeBy(1L)
        drain()
        assertEquals(
            listOf(60, 57),
            fixture.audio.commands.filterIsInstance<AudioCommand.NoteOn>().map { it.note },
        )
        assertEquals(
            listOf(57),
            fixture.viewModel.uiState.value.instrument.activeBySource.values.flatten().map { it.note },
        )

        fixture.viewModel.releaseInterval(pointerId = 501L)
        drain()
        val noteOnCountAtRelease = fixture.audio.commands.filterIsInstance<AudioCommand.NoteOn>().size
        assertEquals(0, fixture.viewModel.uiState.value.instrument.activeInstanceCount)
        assertTrue(fixture.viewModel.uiState.value.instrument.heldPadBySource.isEmpty())

        scheduler.advanceTimeBy(500L)
        drain()
        assertEquals(
            noteOnCountAtRelease,
            fixture.audio.commands.filterIsInstance<AudioCommand.NoteOn>().size,
        )
    }

    @Test
    fun strummerUsesItsLongerGateWithoutMovingAndArticulationPersists() {
        val working = preset().copy(
            musicalContext = MusicalContextSnapshot(
                chordId = "triad",
                padArticulation = PadArticulation.MUTED,
            ),
        )
        val fixture = fixture(StoredSettings(workingPreset = working))
        fixture.viewModel.onHostStart()
        drain()
        fixture.settingsStore.updates.clear()
        val noteBefore = fixture.viewModel.uiState.value.instrument.currentNote

        fixture.viewModel.strumTone(index = 1, velocity = 121)
        drain()

        assertEquals(noteBefore, fixture.viewModel.uiState.value.instrument.currentNote)
        assertEquals(1, fixture.viewModel.uiState.value.instrument.activeInstanceCount)
        assertTrue(
            fixture.audio.commands.any { command ->
                command is AudioCommand.NoteOn && command.velocity == 121
            },
        )
        scheduler.advanceTimeBy(219L)
        drain()
        assertEquals(1, fixture.viewModel.uiState.value.instrument.activeInstanceCount)
        scheduler.advanceTimeBy(1L)
        drain()
        assertEquals(0, fixture.viewModel.uiState.value.instrument.activeInstanceCount)

        fixture.viewModel.setPadArticulation(PadArticulation.STACKED)
        drain()
        scheduler.advanceTimeBy(200L)
        drain()
        assertEquals(
            PadArticulation.STACKED,
            fixture.settingsStore.updates.last().workingPreset?.musicalContext?.padArticulation,
        )

        fixture.viewModel.setForceToScale(true)
        drain()
        scheduler.advanceTimeBy(200L)
        drain()
        assertTrue(fixture.viewModel.uiState.value.instrument.config.forceToScale)
        assertTrue(
            requireNotNull(fixture.settingsStore.updates.last().workingPreset)
                .musicalContext.forceToScale,
        )
    }

    @Test
    fun clearPanicsCurrentStateAndRejectsQueuedOrDelayedAdapterCommands() {
        val fixture = fixture(StoredSettings(workingPreset = preset()))
        fixture.viewModel.onHostStart()
        fixture.viewModel.triggerInterval(1)
        drain()
        assertTrue(fixture.viewModel.uiState.value.performance.instrument.activeInstanceCount > 0)

        // This press remains queued when clear starts; the already scheduled one-shot release is
        // pending too. Neither may reach a closed native engine.
        fixture.viewModel.pressInterval(pointerId = 202L, steps = 1)
        requireNotNull(viewModelStore).clear()
        viewModelStore = null
        val commandCountAtClose = fixture.audio.commands.size

        scheduler.advanceTimeBy(1_000L)
        drain()

        assertEquals(1, fixture.audio.closeCalls)
        assertEquals(1, fixture.midiRepository.closeCount)
        assertEquals(AudioCommand.Panic, fixture.audio.commands.last())
        assertEquals(commandCountAtClose, fixture.audio.commands.size)
        assertTrue(fixture.audio.commandsAfterClose.isEmpty())
    }

    @Test
    fun midiTrafficCountersAreSampledButOutputOverflowRemainsImmediate() {
        val fixture = fixture(StoredSettings(workingPreset = preset()))
        connectMidi(fixture)

        fixture.viewModel.pressInterval(pointerId = 303L, steps = 1)
        drain()
        val repositoryCount = fixture.midiRepository.state.value.sentMessageCount
        assertTrue(repositoryCount > fixture.viewModel.uiState.value.midi.sentMessageCount)

        scheduler.advanceTimeBy(1_000L)
        drain()
        assertEquals(repositoryCount, fixture.viewModel.uiState.value.midi.sentMessageCount)

        assertTrue(fixture.viewModel.uiState.value.performance.instrument.activeInstanceCount > 0)
        fixture.midiRepository.simulateOutputOverflow()
        drain()

        assertEquals(1L, fixture.viewModel.uiState.value.midi.droppedOutputMessageCount)
        assertEquals(0, fixture.viewModel.uiState.value.performance.instrument.activeInstanceCount)
    }

    @Test
    fun blockingAudioDiagnosticsNeverBlocksTheMusicalActor() {
        val actorScheduler = TestCoroutineScheduler()
        val actor = StandardTestDispatcher(actorScheduler)
        val diagnosticsEntered = CountDownLatch(1)
        val releaseDiagnostics = CountDownLatch(1)
        val diagnosticsDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val audio = FakeAudioMonitor().apply {
            diagnosticsHook = {
                diagnosticsEntered.countDown()
                check(releaseDiagnostics.await(5L, TimeUnit.SECONDS))
            }
        }
        val midiRepository = FakeMidiPortRepository()
        val settingsStore = FakeSettingsStore(StoredSettings(workingPreset = preset()))
        val viewModel = IntervalTabletViewModel(
            application = Application(),
            clock = FakeClock(1_000_000L),
            audioEngine = audio,
            midiRepositoryFactory = { midiRepository },
            settingsStoreFactory = { settingsStore },
            actorDispatcher = actor,
            persistenceDispatcher = actor,
            diagnosticsDispatcher = diagnosticsDispatcher,
        )
        val store = ViewModelStore().also { it.put("blocking-diagnostics", viewModel) }
        viewModelStore = store

        try {
            actorScheduler.runCurrent()
            assertTrue(viewModel.uiState.value.settingsLoaded)
            viewModel.onHostStart()
            actorScheduler.runCurrent()

            actorScheduler.advanceTimeBy(1_000L)
            actorScheduler.runCurrent()
            assertTrue(diagnosticsEntered.await(5L, TimeUnit.SECONDS))

            viewModel.pressInterval(pointerId = 404L, steps = 1)
            actorScheduler.runCurrent()
            assertTrue(viewModel.uiState.value.performance.instrument.activeInstanceCount > 0)
            assertTrue(audio.commands.any { it is AudioCommand.NoteOn })
        } finally {
            releaseDiagnostics.countDown()
            store.clear()
            viewModelStore = null
            actorScheduler.advanceTimeBy(3_000L)
            actorScheduler.runCurrent()
            diagnosticsDispatcher.close()
        }
    }

    @Test
    fun diagnosticsTrackActualAudioRecoveryWithoutChangingDesiredStateOrRestarting() {
        val fixture = fixture(StoredSettings(workingPreset = preset()))
        fixture.viewModel.onHostStart()
        drain()
        assertTrue(fixture.viewModel.uiState.value.audioMonitorEnabled)
        assertTrue(fixture.viewModel.uiState.value.audioRunning)
        assertEquals(1, fixture.audio.startCalls)

        fixture.audio.diagnosticsSnapshot = AudioDiagnostics(
            streamRunning = false,
            recoveryPending = true,
            restartCount = 1,
            lastErrorCode = -899,
        )
        scheduler.advanceTimeBy(1_000L)
        drain()

        val recovering = fixture.viewModel.uiState.value
        assertTrue(recovering.audioMonitorEnabled)
        assertFalse(recovering.audioRunning)
        assertTrue(recovering.audioDiagnostics.recoveryPending)
        assertEquals(1, fixture.audio.startCalls)

        fixture.audio.diagnosticsSnapshot = AudioDiagnostics(
            sampleRate = 48_000,
            streamRunning = true,
            recoveryPending = false,
            restartCount = 1,
        )
        scheduler.advanceTimeBy(1_000L)
        drain()

        val recovered = fixture.viewModel.uiState.value
        assertTrue(recovered.audioMonitorEnabled)
        assertTrue(recovered.audioRunning)
        assertEquals(48_000, recovered.audioDiagnostics.sampleRate)
        assertEquals(1, fixture.audio.startCalls)
    }

    @Test
    fun patchCommittedDuringAudioRecoveryIsReplayedWhenTheStreamReturns() {
        val fixture = fixture(StoredSettings(workingPreset = preset()))
        fixture.viewModel.onHostStart()
        drain()
        fixture.audio.commands.clear()

        fixture.audio.diagnosticsSnapshot = AudioDiagnostics(
            streamRunning = false,
            recoveryPending = true,
            restartCount = 1,
        )
        scheduler.advanceTimeBy(1_000L)
        drain()

        val recoveredPatch = SynthPatch()
            .withTimbre(0.84F)
            .withParameter(SynthParameter.CUTOFF, 6_200.0F)
            .withParameter(SynthParameter.REVERB_MIX, 0.38F)
        fixture.viewModel.setSynthPatch(recoveredPatch)
        drain()
        assertEquals(recoveredPatch, fixture.viewModel.uiState.value.synthPatch)
        assertTrue(fixture.audio.commands.none { it is AudioCommand.Parameter })

        fixture.audio.diagnosticsSnapshot = AudioDiagnostics(
            sampleRate = 48_000,
            streamRunning = true,
            recoveryPending = false,
            restartCount = 1,
        )
        scheduler.advanceTimeBy(1_000L)
        drain()

        assertTrue(fixture.viewModel.uiState.value.audioRunning)
        assertEquals(
            recoveredPatch.toAudioCommands(),
            fixture.audio.commands.filterIsInstance<AudioCommand.Parameter>(),
        )
        assertEquals(1, fixture.audio.startCalls)
    }

    @Test
    fun completedAudioRecoveryBetweenDiagnosticSamplesStillReplaysThePatch() {
        val patch = SynthPatch()
            .withTimbre(0.58F)
            .withParameter(SynthParameter.DELAY_MIX, 0.42F)
        val fixture = fixture(
            StoredSettings(
                synthPatch = patch,
                workingPreset = preset(),
            ),
        )
        fixture.viewModel.onHostStart()
        drain()
        fixture.audio.commands.clear()

        fixture.audio.diagnosticsSnapshot = AudioDiagnostics(
            sampleRate = 48_000,
            streamRunning = true,
            recoveryPending = false,
            restartCount = 1,
        )
        scheduler.advanceTimeBy(1_000L)
        drain()

        assertTrue(fixture.viewModel.uiState.value.audioRunning)
        assertEquals(
            patch.toAudioCommands(),
            fixture.audio.commands.filterIsInstance<AudioCommand.Parameter>(),
        )
        assertEquals(1, fixture.audio.startCalls)
    }

    @Test
    fun internalRandomClockMutationIsEventuallyPersistedFromTheLatestImmutableState() {
        val base = preset()
        val randomPreset = base.copy(
            toneRow = base.toneRow.copy(playMode = ToneRowPlaybackSnapshotMode.RANDOM),
        )
        val initialTime = 1_000_000_000L
        val fixture = fixture(
            storedSettings = StoredSettings(workingPreset = randomPreset),
            initialClockNanos = initialTime,
        )
        fixture.viewModel.onHostStart()
        fixture.viewModel.onToneRowIntent(ToneRowUiIntent.PlayPause)
        drain()

        val before = fixture.viewModel.uiState.value.performance
        val due = requireNotNull(before.transport.nextInternalTickNanos)
        val previousRandomState = before.toneRow.randomState
        fixture.settingsStore.updates.clear()

        fixture.clock.timeNanos = due
        val firstStepDelayMillis = ceilNanosToMillis(due - initialTime)
        scheduler.advanceTimeBy(firstStepDelayMillis)
        drain()
        // The durable RANDOM mutation opens its own bounded persistence window at the tick.
        scheduler.advanceTimeBy(201L)
        drain()

        val after = fixture.viewModel.uiState.value.performance
        assertTrue(after.toneRow.randomState != previousRandomState)
        assertEquals(
            after.toneRow.randomState,
            fixture.settingsStore.updates.last().workingPreset?.toneRow?.randomState,
        )
    }

    @Test
    fun transientPersistenceFailureRetriesTheLatestSnapshotWithoutBlockingTheActor() {
        val fixture = fixture(StoredSettings(workingPreset = preset()))
        fixture.settingsStore.updates.clear()
        fixture.settingsStore.failuresRemaining = 1

        fixture.viewModel.setRoot(8)
        drain()

        assertEquals(8, fixture.viewModel.uiState.value.instrument.config.rootPitchClass)
        assertTrue(fixture.settingsStore.updates.isEmpty())
        assertEquals(null, fixture.viewModel.uiState.value.statusMessage)

        scheduler.advanceTimeBy(200L)
        drain()
        assertTrue(fixture.viewModel.uiState.value.statusMessage?.contains("non enregistrés") == true)

        scheduler.advanceTimeBy(250L)
        drain()

        assertEquals(1, fixture.settingsStore.updates.size)
        assertEquals(
            8,
            fixture.settingsStore.updates.single().workingPreset?.musicalContext?.rootPitchClass,
        )
        assertEquals(null, fixture.viewModel.uiState.value.statusMessage)
    }

    @Test
    fun continuousDurableTrafficUsesOneBoundedWindowAndPersistsTheLatestState() {
        val fixture = fixture(StoredSettings(workingPreset = preset()))
        fixture.settingsStore.updates.clear()
        fixture.settingsStore.updateAttempts = 0

        repeat(24) { index ->
            fixture.viewModel.setRoot((index + 1) % 12)
            drain()
            if (index < 23) {
                scheduler.advanceTimeBy(8L)
                drain()
            }
        }

        assertEquals(184L, scheduler.currentTime)
        assertTrue(fixture.settingsStore.updates.isEmpty())
        scheduler.advanceTimeBy(16L)
        drain()

        assertEquals(1, fixture.settingsStore.updateAttempts)
        assertEquals(1, fixture.settingsStore.updates.size)
        assertEquals(
            0,
            fixture.settingsStore.updates.single().workingPreset?.musicalContext?.rootPitchClass,
        )
    }

    @Test
    fun clearReturnsBeforeDataStoreAndIndependentWorkerDrainsTheFinalSnapshot() {
        val fixture = fixture(StoredSettings(workingPreset = preset()))
        fixture.settingsStore.updates.clear()
        fixture.settingsStore.updateAttempts = 0

        fixture.viewModel.setRoot(9)
        drain()
        requireNotNull(viewModelStore).clear()
        viewModelStore = null

        assertTrue(
            "onCleared must not synchronously enter DataStore",
            fixture.settingsStore.updates.isEmpty(),
        )
        scheduler.advanceTimeBy(200L)
        drain()

        assertEquals(1, fixture.settingsStore.updateAttempts)
        assertEquals(
            9,
            fixture.settingsStore.updates.single().workingPreset?.musicalContext?.rootPitchClass,
        )
    }

    @Test
    fun finalDrainStopsAfterTwoBoundedAttemptsWhenStorageKeepsFailing() {
        val fixture = fixture(StoredSettings(workingPreset = preset()))
        fixture.settingsStore.updates.clear()
        fixture.settingsStore.updateAttempts = 0
        fixture.settingsStore.failuresRemaining = 10

        requireNotNull(viewModelStore).clear()
        viewModelStore = null
        scheduler.advanceTimeBy(3_000L)
        drain()

        assertEquals(2, fixture.settingsStore.updateAttempts)
        assertTrue(fixture.settingsStore.updates.isEmpty())
    }

    private fun fixture(
        storedSettings: StoredSettings,
        initialClockNanos: Long = 1_000_000L,
    ): Fixture {
        val clock = FakeClock(initialClockNanos)
        val audio = FakeAudioMonitor()
        val midiRepository = FakeMidiPortRepository()
        val settingsStore = FakeSettingsStore(storedSettings)
        lateinit var packetSink: MidiPacketSink
        val viewModel = IntervalTabletViewModel(
            application = Application(),
            clock = clock,
            audioEngine = audio,
            midiRepositoryFactory = { sink ->
                packetSink = sink
                midiRepository
            },
            settingsStoreFactory = { settingsStore },
            actorDispatcher = dispatcher,
            persistenceDispatcher = dispatcher,
            diagnosticsDispatcher = dispatcher,
        )
        viewModelStore = ViewModelStore().also { store -> store.put("subject", viewModel) }
        drain()
        assertTrue("Initial settings were not applied", viewModel.uiState.value.settingsLoaded)
        return Fixture(viewModel, clock, audio, midiRepository, settingsStore, packetSink)
    }

    private fun connectMidi(fixture: Fixture): ConnectedMidi {
        val source = MidiPortDescriptor(
            deviceId = 11,
            portNumber = 0,
            direction = MidiPortDirection.SOURCE,
            deviceName = "Test source",
            portName = "Out",
        )
        val destination = MidiPortDescriptor(
            deviceId = 12,
            portNumber = 1,
            direction = MidiPortDirection.DESTINATION,
            deviceName = "Test destination",
            portName = "In",
        )
        fixture.midiRepository.setPorts(listOf(source, destination))
        drain()
        fixture.viewModel.onHostStart()
        drain()
        fixture.viewModel.selectSource(source)
        fixture.viewModel.selectDestination(destination)
        drain()
        fixture.midiRepository.sentMessages.clear()
        assertEquals(source, fixture.midiRepository.state.value.selectedSource)
        assertEquals(destination, fixture.midiRepository.state.value.selectedDestination)
        return ConnectedMidi(source, destination)
    }

    private fun drain() {
        // advanceUntilIdle cannot be used: the ViewModel intentionally owns a periodic diagnostics job.
        scheduler.runCurrent()
    }

    private fun preset(
        rootPitchClass: Int = 0,
        passThroughMode: PassThroughMode = PassThroughMode.ACTIVE,
        inputChannel: Int? = null,
        tempoBpm: Int = 120,
        clockSource: StoredClockSource = StoredClockSource.INTERNAL,
    ): PerformancePresetSnapshot = PerformancePresetSnapshot(
        name = "Fixture $rootPitchClass",
        musicalContext = MusicalContextSnapshot(rootPitchClass = rootPitchClass),
        routing = RoutingSnapshot(
            passThroughMode = passThroughMode,
            inputChannel = inputChannel,
        ),
        toneRow = ToneRowSnapshot(
            entries = listOf(
                ToneRowEntrySnapshot(relativeDegree = 0, recordedMidiNote = 60, velocity = 80),
                ToneRowEntrySnapshot(relativeDegree = 1, recordedMidiNote = 62, velocity = 81),
                ToneRowEntrySnapshot(relativeDegree = 2, recordedMidiNote = 64, velocity = 82),
            ),
            intervalSequence = listOf(1),
            referenceRootPitchClass = rootPitchClass,
        ),
        transport = TransportOptionsSnapshot(
            tempoBpm = tempoBpm,
            clockSource = clockSource,
        ),
    )

    private fun ceilNanosToMillis(nanos: Long): Long {
        require(nanos >= 0L)
        return nanos / 1_000_000L + if (nanos % 1_000_000L == 0L) 0L else 1L
    }

    private data class Fixture(
        val viewModel: IntervalTabletViewModel,
        val clock: FakeClock,
        val audio: FakeAudioMonitor,
        val midiRepository: FakeMidiPortRepository,
        val settingsStore: FakeSettingsStore,
        val packetSink: MidiPacketSink,
    )

    private data class ConnectedMidi(
        val source: MidiPortDescriptor,
        val destination: MidiPortDescriptor,
    )

    private class FakeClock(var timeNanos: Long) : MonotonicClock {
        override fun nowNanos(): Long = timeNanos
    }

    private class FakeSettingsStore(initial: StoredSettings) : SettingsStore {
        private val mutableSettings = MutableStateFlow(initial)
        override val settings: Flow<StoredSettings> = mutableSettings
        val updates = mutableListOf<StoredSettings>()
        var failuresRemaining: Int = 0
        var updateAttempts: Int = 0

        override suspend fun update(settings: StoredSettings) {
            updateAttempts += 1
            if (failuresRemaining > 0) {
                failuresRemaining -= 1
                error("transient settings failure")
            }
            updates += settings
            mutableSettings.value = settings
        }
    }

    private class FakeAudioMonitor : AudioMonitor {
        override val isAvailable: Boolean = true
        override val isRunning: Boolean get() = running
        var running: Boolean = false
            private set
        var startCalls: Int = 0
            private set
        var stopCalls: Int = 0
            private set
        var closeCalls: Int = 0
            private set
        val commands = mutableListOf<AudioCommand>()
        val commandsAfterClose = mutableListOf<AudioCommand>()
        var diagnosticsSnapshot: AudioDiagnostics? = null
        var diagnosticsHook: (() -> Unit)? = null
        private var closed: Boolean = false

        override fun start(): Boolean {
            startCalls += 1
            running = true
            return true
        }

        override fun stop() {
            stopCalls += 1
            running = false
        }

        override fun send(command: AudioCommand): Boolean {
            if (closed) {
                commandsAfterClose += command
                return false
            }
            commands += command
            return true
        }

        override fun diagnostics(): AudioDiagnostics {
            diagnosticsHook?.invoke()
            return diagnosticsSnapshot ?: AudioDiagnostics(streamRunning = running)
        }

        override fun close() {
            closeCalls += 1
            closed = true
            running = false
        }
    }
}
