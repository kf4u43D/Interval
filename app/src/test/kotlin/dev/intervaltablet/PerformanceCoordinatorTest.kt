package dev.intervaltablet

import dev.intervaltablet.domain.ClockSource
import dev.intervaltablet.domain.ChordLibrary
import dev.intervaltablet.domain.InstrumentAction
import dev.intervaltablet.domain.InstrumentConfig
import dev.intervaltablet.domain.MidiAction
import dev.intervaltablet.domain.MidiBindingKey
import dev.intervaltablet.domain.MidiDestinationId
import dev.intervaltablet.domain.MidiMapping
import dev.intervaltablet.domain.MidiMessage
import dev.intervaltablet.domain.PassThroughMode
import dev.intervaltablet.domain.PadArticulation
import dev.intervaltablet.domain.ScaleLibrary
import dev.intervaltablet.domain.ToneRowAction
import dev.intervaltablet.domain.ToneRowEntry
import dev.intervaltablet.domain.ToneRowMode
import dev.intervaltablet.domain.ToneRowPlayMode
import dev.intervaltablet.domain.ToneRowState
import dev.intervaltablet.domain.TransportAction
import dev.intervaltablet.domain.TransportMode
import dev.intervaltablet.domain.TriggerSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceCoordinatorTest {
    private val coordinator = PerformanceCoordinator()

    @Test
    fun touchAndMappedMidiTraverseTheSameReducer() {
        val mapping = MidiMapping(
            mapOf(MidiBindingKey(MidiBindingKey.Kind.NOTE, 40) to MidiAction.Move(1)),
        )
        val initial = PerformanceCoordinatorState.initial().copy(mapping = mapping)
        val touch = coordinator.reduce(
            initial,
            PerformanceCommand.Instrument(
                InstrumentAction.PressInterval(TriggerSource.Touch(1), 1, 90, 10),
            ),
        )
        val midi = coordinator.reduce(
            initial,
            PerformanceCommand.MidiMessages(7, 0, listOf(MidiMessage.NoteOn(0, 40, 90, 10))),
        )
        assertEquals(touch.state.instrument.currentNote, midi.state.instrument.currentNote)
        assertEquals(
            touch.effects.filterIsInstance<PerformanceEffect.Midi>().map { it.message },
            midi.effects.filterIsInstance<PerformanceEffect.Midi>().map { it.message },
        )
    }

    @Test
    fun mixedMappedAndForwardedMessagesKeepPacketOrder() {
        val mapping = MidiMapping(
            mapOf(MidiBindingKey(MidiBindingKey.Kind.NOTE, 40) to MidiAction.Move(1)),
        )
        val initial = PerformanceCoordinatorState.initial().copy(mapping = mapping)
        val transition = coordinator.reduce(
            initial,
            PerformanceCommand.MidiMessages(
                7,
                0,
                listOf(
                    MidiMessage.NoteOn(0, 40, 100, 1),
                    MidiMessage.ControlChange(0, 10, 22, 2),
                ),
            ),
        )
        val messages = transition.effects.filterIsInstance<PerformanceEffect.Midi>().map { it.message }
        assertTrue(messages.first() is MidiMessage.NoteOn)
        assertEquals(MidiMessage.ControlChange(0, 10, 22, 2), messages.last())
    }

    @Test
    fun destinationSwitchReleasesOldRouteBeforeUsingNewRoute() {
        val old = MidiDestinationId("old")
        val next = MidiDestinationId("next")
        var state = stateWithManualRow().copy(currentDestination = old)
        state = coordinator.reduce(
            state,
            PerformanceCommand.Instrument(
                InstrumentAction.PressInterval(TriggerSource.Touch(9), 0, 90, 1),
            ),
        ).state
        val switched = coordinator.reduce(state, PerformanceCommand.SwitchDestination(next, 2))
        assertEquals(next, switched.state.currentDestination)
        assertEquals(0, switched.state.instrument.activeInstanceCount)
        assertTrue(
            switched.effects.filterIsInstance<PerformanceEffect.Midi>()
                .filter { it.message is MidiMessage.NoteOff }
                .all { it.destination == old },
        )
        val messages = switched.effects.filterIsInstance<PerformanceEffect.Midi>().map { it.message }
        assertTrue(messages.first() is MidiMessage.NoteOff)
        assertEquals(
            listOf(123, 120) + (0..15).flatMap { listOf(123, 120) },
            messages.filterIsInstance<MidiMessage.ControlChange>().map { it.controller },
        )
        val controls = switched.effects.filterIsInstance<PerformanceEffect.Midi>()
            .filter { it.message is MidiMessage.ControlChange }
        assertEquals(listOf(old, old) + List(32) { next }, controls.map { it.destination })
    }

    @Test
    fun switchingToTheLogicalDefaultOnlyPurgesThePhysicalDestination() {
        val old = MidiDestinationId("old")
        val state = PerformanceCoordinatorState.initial().copy(currentDestination = old)

        val switched = coordinator.reduce(
            state,
            PerformanceCommand.SwitchDestination(MidiDestinationId.Default, 9),
        )

        assertEquals(MidiDestinationId.Default, switched.state.currentDestination)
        val controls = switched.effects.filterIsInstance<PerformanceEffect.Midi>()
            .filter { it.message is MidiMessage.ControlChange }
        assertEquals(listOf(old, old), controls.map { it.destination })
        assertEquals(
            listOf(123, 120),
            controls.map { (it.message as MidiMessage.ControlChange).controller },
        )
    }

    @Test
    fun panicClearsMappedLeasesAndInstrumentInstances() {
        var state = PerformanceCoordinatorState.initial().copy(
            router = PerformanceCoordinatorState.initial().router.copy(mode = PassThroughMode.ACTIVE),
        )
        state = coordinator.reduce(
            state,
            PerformanceCommand.MidiMessages(2, 1, listOf(MidiMessage.NoteOn(0, 77, 100, 1))),
        ).state
        val panic = coordinator.reduce(state, PerformanceCommand.Panic(2))
        assertEquals(0, panic.state.router.activeLeaseCount)
        assertEquals(0, panic.state.instrument.activeInstanceCount)
    }

    @Test
    fun autoTickReleasesPreviousVoiceBeforeStartingOneReplacement() {
        val started = coordinator.reduce(
            stateWithManualRow(),
            PerformanceCommand.ToneRow(ToneRowAction.StartAuto(restart = true), 10),
        )
        val firstSource = requireNotNull(started.state.toneRowAutoSource)
        assertEquals(1, started.state.instrument.activeInstanceCount)

        val due = requireNotNull(started.state.transport.nextInternalTickNanos)
        val ticked = coordinator.reduce(
            started.state,
            PerformanceCommand.Transport(TransportAction.InternalClock(due)),
        )
        val replacementSource = requireNotNull(ticked.state.toneRowAutoSource)
        val noteEdges = ticked.midiMessages().filter {
            it is MidiMessage.NoteOff || it is MidiMessage.NoteOn
        }

        assertEquals(listOf(MidiMessage.NoteOff::class, MidiMessage.NoteOn::class), noteEdges.map { it::class })
        assertTrue(firstSource != replacementSource)
        assertEquals(1, ticked.state.instrument.activeInstanceCount)
        assertEquals(setOf(replacementSource), ticked.state.instrument.activeBySource.keys)

        val staleRelease = coordinator.reduce(
            ticked.state,
            PerformanceCommand.Instrument(InstrumentAction.Release(firstSource, timestampNanos = due + 1)),
        )
        assertTrue(staleRelease.midiMessages().none { it is MidiMessage.NoteOff })
        assertEquals(replacementSource, staleRelease.state.toneRowAutoSource)
        assertEquals(1, staleRelease.state.instrument.activeInstanceCount)
    }

    @Test
    fun pauseReleasesAutoVoiceAndResumeWaitsForNextTick() {
        val started = coordinator.reduce(
            stateWithManualRow(),
            PerformanceCommand.ToneRow(ToneRowAction.StartAuto(restart = true), 10),
        )
        val paused = coordinator.reduce(
            started.state,
            PerformanceCommand.ToneRow(ToneRowAction.PauseToggle, 20),
        )

        assertEquals(ToneRowMode.PAUSED, paused.state.toneRow.mode)
        assertEquals(TransportMode.PAUSED, paused.state.transport.mode)
        assertEquals(0, paused.state.instrument.activeInstanceCount)
        assertTrue(paused.midiMessages().any { it is MidiMessage.NoteOff })

        val resumed = coordinator.reduce(
            paused.state,
            PerformanceCommand.ToneRow(ToneRowAction.PauseToggle, 30),
        )
        assertEquals(ToneRowMode.AUTO_PLAYING, resumed.state.toneRow.mode)
        assertEquals(TransportMode.PLAYING, resumed.state.transport.mode)
        assertTrue(resumed.midiMessages().none { it is MidiMessage.NoteOn })
        assertEquals(0, resumed.state.instrument.activeInstanceCount)

        val nextTick = requireNotNull(resumed.state.transport.nextInternalTickNanos)
        val ticked = coordinator.reduce(
            resumed.state,
            PerformanceCommand.Transport(TransportAction.InternalClock(nextTick)),
        )
        assertTrue(ticked.midiMessages().any { it is MidiMessage.NoteOn })
        assertEquals(1, ticked.state.instrument.activeInstanceCount)
    }

    @Test
    fun localStopReturnsIdleWhileMidiStopRetainsPausedPositionForContinue() {
        val localStarted = coordinator.reduce(
            stateWithManualRow(),
            PerformanceCommand.ToneRow(ToneRowAction.StartAuto(restart = true), 1),
        )
        val localStopped = coordinator.reduce(
            localStarted.state,
            PerformanceCommand.ToneRow(ToneRowAction.Stop, 2),
        )
        assertEquals(ToneRowMode.IDLE, localStopped.state.toneRow.mode)
        assertEquals(TransportMode.STOPPED, localStopped.state.transport.mode)
        assertEquals(0, localStopped.state.instrument.activeInstanceCount)

        val midiInitial = stateWithManualRow().copy(
            transport = stateWithManualRow().transport.copy(
                clockSource = ClockSource.MIDI,
                clocksPerStep = 2,
            ),
        )
        val midiStopped = coordinator.reduce(
            midiInitial,
            PerformanceCommand.MidiMessages(
                deviceId = 8,
                portNumber = 0,
                messages = listOf(
                    MidiMessage.RealTime(0xFA, 10),
                    MidiMessage.RealTime(0xF8, 11),
                    MidiMessage.RealTime(0xF8, 12),
                    MidiMessage.RealTime(0xFC, 13),
                ),
            ),
        )
        assertEquals(ToneRowMode.PAUSED, midiStopped.state.toneRow.mode)
        assertEquals(TransportMode.STOPPED, midiStopped.state.transport.mode)
        assertEquals(1, midiStopped.state.toneRow.rowIndex)
        assertEquals(0, midiStopped.state.instrument.activeInstanceCount)

        val continued = coordinator.reduce(
            midiStopped.state,
            PerformanceCommand.MidiMessages(
                8,
                0,
                listOf(MidiMessage.RealTime(0xFB, 14)),
            ),
        )
        assertEquals(ToneRowMode.AUTO_PLAYING, continued.state.toneRow.mode)
        assertEquals(TransportMode.PLAYING, continued.state.transport.mode)
        assertEquals(1, continued.state.toneRow.rowIndex)
        assertTrue(continued.midiMessages().none { it is MidiMessage.NoteOn })
    }

    @Test
    fun activeMidiClockIsForwardedBeforeItsLocalMusicalEffects() {
        val initial = stateWithManualRow().copy(
            router = stateWithManualRow().router.copy(mode = PassThroughMode.ACTIVE),
            transport = stateWithManualRow().transport.copy(
                clockSource = ClockSource.MIDI,
                clocksPerStep = 2,
            ),
        )
        val transition = coordinator.reduce(
            initial,
            PerformanceCommand.MidiMessages(
                deviceId = 5,
                portNumber = 1,
                messages = listOf(
                    MidiMessage.RealTime(0xFA, 1),
                    MidiMessage.RealTime(0xF8, 2),
                    MidiMessage.RealTime(0xF8, 3),
                ),
            ),
        )

        assertEquals(
            listOf("rt:fa", "on", "rt:f8", "rt:f8", "off", "on"),
            transition.midiMessages().map { it.edgeLabel() },
        )
        assertEquals(1, transition.state.transport.stepCounter)
        assertEquals(1, transition.state.instrument.activeInstanceCount)

        val stopped = coordinator.reduce(
            transition.state,
            PerformanceCommand.MidiMessages(5, 1, listOf(MidiMessage.RealTime(0xFC, 4))),
        )
        assertEquals(listOf("rt:fc", "off"), stopped.midiMessages().map { it.edgeLabel() })

        val continued = coordinator.reduce(
            stopped.state,
            PerformanceCommand.MidiMessages(5, 1, listOf(MidiMessage.RealTime(0xFB, 5))),
        )
        assertEquals(listOf("rt:fb"), continued.midiMessages().map { it.edgeLabel() })
    }

    @Test
    fun passThruForwardsRealtimeWithoutDrivingLocalTransportOrToneRow() {
        val initial = stateWithManualRow().copy(
            router = stateWithManualRow().router.copy(mode = PassThroughMode.PASS_THRU),
            transport = stateWithManualRow().transport.copy(
                clockSource = ClockSource.MIDI,
                clocksPerStep = 2,
            ),
        )
        val transition = coordinator.reduce(
            initial,
            PerformanceCommand.MidiMessages(
                deviceId = 5,
                portNumber = 1,
                messages = listOf(
                    MidiMessage.RealTime(0xFA, 1),
                    MidiMessage.RealTime(0xF8, 2),
                    MidiMessage.RealTime(0xF8, 3),
                    MidiMessage.RealTime(0xFC, 4),
                    MidiMessage.RealTime(0xFB, 5),
                ),
            ),
        )

        assertEquals(
            listOf("rt:fa", "rt:f8", "rt:f8", "rt:fc", "rt:fb"),
            transition.midiMessages().map { it.edgeLabel() },
        )
        assertEquals(ToneRowMode.MANUAL_PLAYBACK, transition.state.toneRow.mode)
        assertEquals(TransportMode.STOPPED, transition.state.transport.mode)
        assertEquals(0, transition.state.transport.stepCounter)
        assertEquals(0, transition.state.instrument.activeInstanceCount)
        assertTrue(transition.effects.none { it is PerformanceEffect.Audio })
    }

    @Test
    fun mappedPlayFinishesRecordingThenPausesAndResumesAutoPlayback() {
        val mapping = MidiMapping(
            mapOf(MidiBindingKey(MidiBindingKey.Kind.NOTE, 40) to MidiAction.Play),
        )
        var state = PerformanceCoordinatorState.initial().copy(
            mapping = mapping,
            toneRow = recordingRow(),
        )

        state = coordinator.reduce(
            state,
            PerformanceCommand.MidiMessages(1, 0, listOf(MidiMessage.NoteOn(0, 40, 100, 1))),
        ).state
        assertEquals(ToneRowMode.MANUAL_PLAYBACK, state.toneRow.mode)
        state = coordinator.reduce(
            state,
            PerformanceCommand.MidiMessages(1, 0, listOf(MidiMessage.NoteOff(0, 40, 0, 2))),
        ).state

        state = coordinator.reduce(
            state,
            PerformanceCommand.ToneRow(ToneRowAction.StartAuto(restart = true), 3),
        ).state
        val paused = coordinator.reduce(
            state,
            PerformanceCommand.MidiMessages(1, 0, listOf(MidiMessage.NoteOn(0, 40, 100, 4))),
        )
        assertEquals(ToneRowMode.PAUSED, paused.state.toneRow.mode)
        assertEquals(TransportMode.PAUSED, paused.state.transport.mode)
        assertEquals(0, paused.state.instrument.activeInstanceCount)

        state = coordinator.reduce(
            paused.state,
            PerformanceCommand.MidiMessages(1, 0, listOf(MidiMessage.NoteOff(0, 40, 0, 5))),
        ).state
        val resumed = coordinator.reduce(
            state,
            PerformanceCommand.MidiMessages(1, 0, listOf(MidiMessage.NoteOn(0, 40, 100, 6))),
        )
        assertEquals(ToneRowMode.AUTO_PLAYING, resumed.state.toneRow.mode)
        assertEquals(TransportMode.PLAYING, resumed.state.transport.mode)
        assertTrue(resumed.midiMessages().none { it is MidiMessage.NoteOn })
    }

    @Test
    fun mappedPlayCcTriggersOnlyOnRisingEdgesAndRearmsBelowThreshold() {
        val key = MidiBindingKey(MidiBindingKey.Kind.CC, 41)
        val mapping = MidiMapping(
            bindings = mapOf(key to MidiAction.Play),
            ccThresholds = mapOf(key to 64),
        )
        val initial = stateWithManualRow().copy(mapping = mapping)

        val firstHigh = coordinator.reduce(
            initial,
            PerformanceCommand.MidiMessages(2, 0, listOf(MidiMessage.ControlChange(0, 41, 127, 1))),
        )
        assertEquals(ToneRowMode.AUTO_PLAYING, firstHigh.state.toneRow.mode)
        assertEquals(1, firstHigh.state.router.activeCcGateCount)
        assertEquals(1, firstHigh.midiMessages().count { it is MidiMessage.NoteOn })

        val repeatedHigh = coordinator.reduce(
            firstHigh.state,
            PerformanceCommand.MidiMessages(2, 0, listOf(MidiMessage.ControlChange(0, 41, 100, 2))),
        )
        assertEquals(ToneRowMode.AUTO_PLAYING, repeatedHigh.state.toneRow.mode)
        assertEquals(firstHigh.state.toneRowVoiceCounter, repeatedHigh.state.toneRowVoiceCounter)
        assertTrue(repeatedHigh.midiMessages().none { it is MidiMessage.NoteOn || it is MidiMessage.NoteOff })

        val rearmed = coordinator.reduce(
            repeatedHigh.state,
            PerformanceCommand.MidiMessages(2, 0, listOf(MidiMessage.ControlChange(0, 41, 0, 3))),
        )
        assertEquals(0, rearmed.state.router.activeCcGateCount)
        val secondHigh = coordinator.reduce(
            rearmed.state,
            PerformanceCommand.MidiMessages(2, 0, listOf(MidiMessage.ControlChange(0, 41, 127, 4))),
        )
        assertEquals(ToneRowMode.PAUSED, secondHigh.state.toneRow.mode)
        assertEquals(TransportMode.PAUSED, secondHigh.state.transport.mode)
        assertTrue(secondHigh.midiMessages().any { it is MidiMessage.NoteOff })
    }

    @Test
    fun restartFromPausedPlayOnceRestartsTheWholeFinitePass() {
        val playOnce = coordinator.reduce(
            stateWithManualRow(),
            PerformanceCommand.ToneRow(ToneRowAction.PlayOnce, 1),
        )
        assertEquals(2, playOnce.state.toneRow.notesRemainingInPass)
        val paused = coordinator.reduce(
            playOnce.state,
            PerformanceCommand.ToneRow(ToneRowAction.PauseToggle, 2),
        )

        val restarted = coordinator.reduce(
            paused.state,
            PerformanceCommand.Instrument(
                InstrumentAction.Undo(TriggerSource.Touch(10), velocity = 96, timestampNanos = 3),
            ),
        )
        assertEquals(ToneRowMode.AUTO_PLAYING, restarted.state.toneRow.mode)
        assertEquals(TransportMode.PLAYING, restarted.state.transport.mode)
        assertTrue(restarted.state.toneRow.playOnce)
        assertEquals(0, restarted.state.toneRow.rowIndex)
        assertEquals(2, restarted.state.toneRow.notesRemainingInPass)
        assertEquals(1, restarted.midiMessages().count { it is MidiMessage.NoteOn })

        val second = coordinator.reduce(
            restarted.state,
            PerformanceCommand.ToneRow(ToneRowAction.Tick, 4),
        )
        val third = coordinator.reduce(
            second.state,
            PerformanceCommand.ToneRow(ToneRowAction.Tick, 5),
        )
        assertEquals(ToneRowMode.MANUAL_PLAYBACK, third.state.toneRow.mode)
        assertEquals(TransportMode.STOPPED, third.state.transport.mode)
        assertTrue(!third.state.toneRow.playOnce)
        assertEquals(0, third.state.toneRow.notesRemainingInPass)
        assertEquals(3, listOf(restarted, second, third).sumOf { transition ->
            transition.midiMessages().count { it is MidiMessage.NoteOn }
        })
    }

    @Test
    fun panicStopsTransportAndReleasesAnAutomaticVoice() {
        val started = coordinator.reduce(
            stateWithManualRow(),
            PerformanceCommand.ToneRow(ToneRowAction.StartAuto(restart = true), 1),
        )
        val panic = coordinator.reduce(started.state, PerformanceCommand.Panic(2))

        assertEquals(ToneRowMode.IDLE, panic.state.toneRow.mode)
        assertEquals(TransportMode.STOPPED, panic.state.transport.mode)
        assertEquals(null, panic.state.toneRowAutoSource)
        assertEquals(0, panic.state.instrument.activeInstanceCount)
        assertTrue(panic.midiMessages().first() is MidiMessage.NoteOff)
        assertEquals(listOf(123, 120), panic.midiMessages().filterIsInstance<MidiMessage.ControlChange>().map {
            it.controller
        })
    }

    @Test
    fun gridReconfigurationFinishesRecordingAndReleasesRecordedVoice() {
        var state = coordinator.reduce(
            PerformanceCoordinatorState.initial(),
            PerformanceCommand.ToneRow(ToneRowAction.StartRecording(60), 1),
        ).state
        state = coordinator.reduce(
            state,
            PerformanceCommand.Instrument(
                InstrumentAction.PressInterval(TriggerSource.Touch(4), 1, 90, 2),
            ),
        ).state
        assertEquals(ToneRowMode.RECORDING, state.toneRow.mode)
        assertEquals(1, state.instrument.activeInstanceCount)

        val reconfigured = coordinator.reduce(
            state,
            PerformanceCommand.Instrument(InstrumentAction.SetRoot(2, timestampNanos = 3)),
        )
        assertEquals(ToneRowMode.MANUAL_PLAYBACK, reconfigured.state.toneRow.mode)
        assertEquals(1, reconfigured.state.toneRow.entries.size)
        assertEquals(null, reconfigured.state.toneRow.currentRecordNote)
        assertEquals(0, reconfigured.state.toneRow.referenceRootPitchClass)
        assertEquals(2, reconfigured.state.instrument.config.rootPitchClass)
        assertEquals(0, reconfigured.state.instrument.activeInstanceCount)
        assertTrue(reconfigured.midiMessages().any { it is MidiMessage.NoteOff })
    }

    @Test
    fun manualToneRowHonorsTheExplicitCommandDestination() {
        val selected = MidiDestinationId("selected")
        val explicit = MidiDestinationId("explicit")
        val transition = coordinator.reduce(
            stateWithManualRow().copy(currentDestination = selected),
            PerformanceCommand.Instrument(
                action = InstrumentAction.PressInterval(TriggerSource.Touch(5), 1, 100, 1),
                destination = explicit,
            ),
        )

        assertEquals(selected, transition.state.currentDestination)
        assertEquals(1, transition.state.toneRow.rowIndex)
        assertTrue(transition.effects.filterIsInstance<PerformanceEffect.Midi>().isNotEmpty())
        assertTrue(transition.effects.filterIsInstance<PerformanceEffect.Midi>().all {
            it.destination == explicit
        })
    }

    @Test
    fun longAutoRunKeepsHistoryAndOwnedInstancesBounded() {
        var state = coordinator.reduce(
            stateWithManualRow(),
            PerformanceCommand.ToneRow(ToneRowAction.StartAuto(restart = true), 1),
        ).state

        repeat(512) { index ->
            state = coordinator.reduce(
                state,
                PerformanceCommand.ToneRow(ToneRowAction.Tick, index.toLong() + 2L),
            ).state
            assertEquals(1, state.instrument.activeInstanceCount)
            assertEquals(1, state.instrument.activeBySource.size)
            assertEquals(state.toneRowAutoSource, state.instrument.activeBySource.keys.single())
        }

        assertEquals(emptyList<Int>(), state.instrument.previousDistinctNotes)
        assertEquals(513L, state.toneRowVoiceCounter)
        assertEquals(emptyMap<TriggerSource, Int>(), state.activeStepsBySource)
    }

    @Test
    fun midiStartFinishesAnInProgressRecordingBeforeStartingAutoPlayback() {
        val initial = PerformanceCoordinatorState.initial().copy(
            toneRow = recordingRow(),
            transport = PerformanceCoordinatorState.initial().transport.copy(clockSource = ClockSource.MIDI),
        )

        val started = coordinator.reduce(
            initial,
            PerformanceCommand.MidiMessages(3, 0, listOf(MidiMessage.RealTime(0xFA, 100))),
        )

        assertEquals(ToneRowMode.AUTO_PLAYING, started.state.toneRow.mode)
        assertEquals(TransportMode.PLAYING, started.state.transport.mode)
        assertEquals(1, started.state.toneRow.entries.size)
        assertEquals(1, started.state.instrument.activeInstanceCount)
        assertEquals(listOf("rt:fa", "on"), started.midiMessages().map { it.edgeLabel() })
    }

    @Test
    fun automaticVoiceKeepsItsOwnedDestinationUntilEveryRelease() {
        val selected = MidiDestinationId("selected")
        val owned = MidiDestinationId("owned")
        val paused = stateWithManualRow().copy(
            currentDestination = selected,
            toneRow = playableRow().copy(mode = ToneRowMode.PAUSED),
            transport = stateWithManualRow().transport.copy(mode = TransportMode.PAUSED),
        )
        val restarted = coordinator.reduce(
            paused,
            PerformanceCommand.Instrument(
                InstrumentAction.Undo(TriggerSource.Touch(88), velocity = 96, timestampNanos = 10),
                destination = owned,
            ),
        )
        assertEquals(owned, restarted.state.toneRowAutoDestination)
        assertTrue(restarted.effects.filterIsInstance<PerformanceEffect.Midi>().all { it.destination == owned })
        assertEquals(owned, restarted.effects.filterIsInstance<PerformanceEffect.ReleaseAt>().single().destination)

        val replaced = coordinator.reduce(
            restarted.state,
            PerformanceCommand.ToneRow(ToneRowAction.Tick, 20),
        )
        val edges = replaced.effects.filterIsInstance<PerformanceEffect.Midi>().filter {
            it.message is MidiMessage.NoteOff || it.message is MidiMessage.NoteOn
        }
        assertEquals(listOf(owned, selected), edges.map { it.destination })
        assertEquals(selected, replaced.state.toneRowAutoDestination)
    }

    @Test
    fun midiClockObservedPeriodDeterminesScheduledGateDuration() {
        var state = stateWithManualRow().copy(
            transport = stateWithManualRow().transport.copy(
                clockSource = ClockSource.MIDI,
                clocksPerStep = 2,
                noteDurationPercent = 50,
            ),
        )
        state = coordinator.reduce(
            state,
            PerformanceCommand.MidiMessages(4, 0, listOf(MidiMessage.RealTime(0xFA, 0))),
        ).state
        val tick = coordinator.reduce(
            state,
            PerformanceCommand.MidiMessages(
                4,
                0,
                listOf(
                    MidiMessage.RealTime(0xF8, 40_000_000),
                    MidiMessage.RealTime(0xF8, 80_000_000),
                ),
            ),
        )
        val release = tick.effects.filterIsInstance<PerformanceEffect.ReleaseAt>().last()
        assertEquals(120_000_000L, release.timestampNanos)
    }

    @Test
    fun mutedPadStillRecordsAndMovesWithoutProducingANoteOn() {
        val initial = PerformanceCoordinatorState.initial(
            InstrumentConfig(chord = ChordLibrary.triad, padArticulation = PadArticulation.MUTED),
        ).copy(
            toneRow = ToneRowState(
                mode = ToneRowMode.RECORDING,
                currentRecordNote = 60,
                recordingCapacity = 7,
            ),
        )

        val moved = coordinator.reduce(
            initial,
            PerformanceCommand.Instrument(
                InstrumentAction.PressInterval(TriggerSource.Touch(71), 1, 100, 10),
            ),
        )

        assertEquals(1, moved.state.toneRow.entries.size)
        assertEquals(1, moved.state.activeStepsBySource[TriggerSource.Touch(71)])
        assertEquals(0, moved.state.instrument.activeInstanceCount)
        assertTrue(moved.midiMessages().none { it is MidiMessage.NoteOn })
    }

    @Test
    fun scaleChangeRevoicesHeldTouchWithoutClearingItsPressedProjection() {
        val source = TriggerSource.Touch(72)
        var state = PerformanceCoordinatorState.initial(
            InstrumentConfig(chord = ChordLibrary.triad, padArticulation = PadArticulation.STACKED),
        )
        state = coordinator.reduce(
            state,
            PerformanceCommand.Instrument(
                InstrumentAction.PressInterval(source, 2, 100, timestampNanos = 10L),
            ),
        ).state

        val changed = coordinator.reduce(
            state,
            PerformanceCommand.Instrument(
                InstrumentAction.SetScale(ScaleLibrary.harmonicMinor, timestampNanos = 20L),
            ),
        )

        assertEquals(2, changed.state.activeStepsBySource[source])
        assertTrue(source in changed.state.instrument.heldPadBySource)
        assertEquals(
            listOf(64, 60, 57),
            changed.midiMessages().filterIsInstance<MidiMessage.NoteOff>().map { it.note },
        )
        assertEquals(
            listOf(63, 60, 56),
            changed.midiMessages().filterIsInstance<MidiMessage.NoteOn>().map { it.note },
        )
    }

    @Test
    fun automaticToneRowRemainsStackedWhenPadsAreMuted() {
        val initial = PerformanceCoordinatorState.initial(
            InstrumentConfig(chord = ChordLibrary.triad, padArticulation = PadArticulation.MUTED),
        ).copy(toneRow = playableRow())

        val started = coordinator.reduce(
            initial,
            PerformanceCommand.ToneRow(ToneRowAction.StartAuto(restart = true), 20),
        )

        assertEquals(3, started.midiMessages().filterIsInstance<MidiMessage.NoteOn>().size)
        assertEquals(3, started.state.instrument.activeInstanceCount)
    }

    @Test
    fun mutedPadAdvancesManualToneRowWithoutCreatingReleaseDebt() {
        val source = TriggerSource.Touch(73)
        val initial = PerformanceCoordinatorState.initial(
            InstrumentConfig(chord = ChordLibrary.triad, padArticulation = PadArticulation.MUTED),
        ).copy(toneRow = playableRow())

        val moved = coordinator.reduce(
            initial,
            PerformanceCommand.Instrument(
                InstrumentAction.PressInterval(source, steps = 1, velocity = 104, timestampNanos = 30),
            ),
        )

        assertEquals(1, moved.state.toneRow.rowIndex)
        assertEquals(62, moved.state.instrument.currentNote)
        assertEquals(0, moved.state.instrument.activeInstanceCount)
        assertTrue(moved.midiMessages().none { it is MidiMessage.NoteOn })
        val released = coordinator.reduce(
            moved.state,
            PerformanceCommand.Instrument(InstrumentAction.Release(source, timestampNanos = 31)),
        )
        assertTrue(released.midiMessages().none { it is MidiMessage.NoteOff })
        assertTrue(source !in released.state.activeStepsBySource)
    }

    @Test
    fun pausedToneRowAcceptsTouchMovementWithStoredVelocityAndKeepsTransportPaused() {
        val source = TriggerSource.Touch(74)
        val initial = stateWithManualRow().copy(
            toneRow = playableRow().copy(mode = ToneRowMode.PAUSED),
            transport = stateWithManualRow().transport.copy(mode = TransportMode.PAUSED),
        )

        val moved = coordinator.reduce(
            initial,
            PerformanceCommand.Instrument(
                InstrumentAction.PressInterval(source, steps = 1, velocity = 120, timestampNanos = 40L),
            ),
        )

        assertEquals(ToneRowMode.PAUSED, moved.state.toneRow.mode)
        assertEquals(TransportMode.PAUSED, moved.state.transport.mode)
        assertEquals(1, moved.state.toneRow.rowIndex)
        assertEquals(1, moved.state.toneRow.lastManualSteps)
        val noteOn = moved.midiMessages().filterIsInstance<MidiMessage.NoteOn>().single()
        assertEquals(62, noteOn.note)
        assertEquals(88, noteOn.velocity)

        val released = coordinator.reduce(
            moved.state,
            PerformanceCommand.Instrument(InstrumentAction.Release(source, timestampNanos = 41L)),
        )
        assertEquals(listOf(62), released.midiMessages().filterIsInstance<MidiMessage.NoteOff>().map { it.note })
        assertTrue(source !in released.state.activeStepsBySource)
    }

    @Test
    fun mappedMidiVelocityOverridesStoredToneRowVelocityAndSameRepeatsTheLastManualMove() {
        val mapping = MidiMapping(
            mapOf(
                MidiBindingKey(MidiBindingKey.Kind.NOTE, 40) to MidiAction.Move(1),
                MidiBindingKey(MidiBindingKey.Kind.NOTE, 41) to MidiAction.Same,
            ),
        )
        var state = stateWithManualRow().copy(mapping = mapping)

        val moved = coordinator.reduce(
            state,
            PerformanceCommand.MidiMessages(7, 0, listOf(MidiMessage.NoteOn(2, 40, 117, 1L))),
        )
        assertEquals(1, moved.state.toneRow.rowIndex)
        assertEquals(117, moved.midiMessages().filterIsInstance<MidiMessage.NoteOn>().single().velocity)
        state = coordinator.reduce(
            moved.state,
            PerformanceCommand.MidiMessages(7, 0, listOf(MidiMessage.NoteOff(2, 40, 0, 2L))),
        ).state

        val same = coordinator.reduce(
            state,
            PerformanceCommand.MidiMessages(7, 0, listOf(MidiMessage.NoteOn(2, 41, 109, 3L))),
        )
        assertEquals(2, same.state.toneRow.rowIndex)
        assertEquals(1, same.state.toneRow.lastManualSteps)
        val sameNote = same.midiMessages().filterIsInstance<MidiMessage.NoteOn>().single()
        assertEquals(64, sameNote.note)
        assertEquals(109, sameNote.velocity)
        assertEquals(
            1,
            same.state.activeStepsBySource[TriggerSource.Midi(7, 0, 2, 41)],
        )
    }

    @Test
    fun mappedSamePitchRepeatsTheActualSemitoneRatioOutsideTheScale() {
        val mapping = MidiMapping(
            mapOf(
                MidiBindingKey(MidiBindingKey.Kind.NOTE, 40) to MidiAction.Move(1),
                MidiBindingKey(MidiBindingKey.Kind.NOTE, 41) to MidiAction.SamePitch,
            ),
        )
        var state = PerformanceCoordinatorState.initial().copy(mapping = mapping)

        repeat(2) { index ->
            state = coordinator.reduce(
                state,
                PerformanceCommand.MidiMessages(
                    8,
                    0,
                    listOf(MidiMessage.NoteOn(0, 40, 90, (index * 2 + 1).toLong())),
                ),
            ).state
            state = coordinator.reduce(
                state,
                PerformanceCommand.MidiMessages(
                    8,
                    0,
                    listOf(MidiMessage.NoteOff(0, 40, 0, (index * 2 + 2).toLong())),
                ),
            ).state
        }

        val samePitch = coordinator.reduce(
            state,
            PerformanceCommand.MidiMessages(8, 0, listOf(MidiMessage.NoteOn(0, 41, 100, 5L))),
        )
        assertEquals(66, samePitch.state.instrument.currentNote)
        assertEquals(66, samePitch.midiMessages().filterIsInstance<MidiMessage.NoteOn>().single().note)
    }

    @Test
    fun randomAndChromaticShiftMappingsStayImmediateWithoutChangingToneRowTransforms() {
        val mapping = MidiMapping(
            mapOf(
                MidiBindingKey(MidiBindingKey.Kind.NOTE, 50) to MidiAction.ChromaticShift(1),
                MidiBindingKey(MidiBindingKey.Kind.NOTE, 51) to MidiAction.Random,
            ),
        )
        val initial = stateWithManualRow().copy(mapping = mapping)
        val held = coordinator.reduce(
            initial,
            PerformanceCommand.MidiMessages(9, 0, listOf(MidiMessage.NoteOn(0, 50, 127, 1L))),
        )
        assertTrue(held.midiMessages().none { it is MidiMessage.NoteOn })
        assertEquals(1, held.state.instrument.activeChromaticShiftSemitones)

        val random = coordinator.reduce(
            held.state,
            PerformanceCommand.MidiMessages(9, 0, listOf(MidiMessage.NoteOn(0, 51, 99, 2L))),
        )
        val noteOn = random.midiMessages().filterIsInstance<MidiMessage.NoteOn>().single()
        assertEquals(
            random.state.instrument.config.grid().moveChromatic(random.state.instrument.currentNote, 1),
            noteOn.note,
        )
        assertEquals(ToneRowPlayMode.PRIME, random.state.toneRow.playMode)
        assertEquals(0, random.state.toneRow.transpositionSemitones)

        val releasedShift = coordinator.reduce(
            random.state,
            PerformanceCommand.MidiMessages(9, 0, listOf(MidiMessage.NoteOff(0, 50, 0, 3L))),
        )
        assertEquals(0, releasedShift.state.instrument.activeChromaticShiftSemitones)
        assertTrue(releasedShift.midiMessages().none { it is MidiMessage.NoteOff })
    }

    @Test
    fun heldNoteAndCcShiftsKeepTheirOriginalLeasesAcrossAMappingReplacement() {
        val noteKey = MidiBindingKey(MidiBindingKey.Kind.NOTE, 52)
        val ccKey = MidiBindingKey(MidiBindingKey.Kind.CC, 53)
        val originalMapping = MidiMapping(
            bindings = mapOf(
                noteKey to MidiAction.ChromaticShift(1),
                ccKey to MidiAction.ChromaticShift(2),
            ),
            ccThresholds = mapOf(ccKey to 64),
        )
        val originalDestination = MidiDestinationId("shift-lease-origin")
        val initial = PerformanceCoordinatorState.initial().copy(
            mapping = originalMapping,
            currentDestination = originalDestination,
        )

        val heldNote = coordinator.reduce(
            initial,
            PerformanceCommand.MidiMessages(
                deviceId = 11,
                portNumber = 1,
                messages = listOf(MidiMessage.NoteOn(3, 52, 100, 1L)),
            ),
        )
        val heldCc = coordinator.reduce(
            heldNote.state,
            PerformanceCommand.MidiMessages(
                deviceId = 11,
                portNumber = 1,
                messages = listOf(MidiMessage.ControlChange(3, 53, 100, 2L)),
            ),
        )
        assertEquals(3, heldCc.state.instrument.activeChromaticShiftSemitones)
        assertEquals(2, heldCc.state.router.activeLeaseCount)
        assertEquals(1, heldCc.state.router.activeCcGateCount)
        val noteLease = heldCc.state.router.leaseSnapshot().single()
        assertEquals(TriggerSource.Midi(11, 1, 3, 52), noteLease.source)
        assertEquals(originalDestination, noteLease.destination)
        assertTrue(heldCc.midiMessages().none { it is MidiMessage.NoteOff })

        val replacement = MidiMapping(emptyMap())
        val remapped = coordinator.reduce(
            heldCc.state,
            PerformanceCommand.SetMapping(replacement),
        )
        assertEquals(replacement, remapped.state.mapping)
        assertEquals(3, remapped.state.instrument.activeChromaticShiftSemitones)
        assertEquals(2, remapped.state.router.activeLeaseCount)
        assertEquals(originalDestination, remapped.state.router.leaseSnapshot().single().destination)

        val releasedNote = coordinator.reduce(
            remapped.state,
            PerformanceCommand.MidiMessages(
                deviceId = 11,
                portNumber = 1,
                messages = listOf(MidiMessage.NoteOff(3, 52, 0, 3L)),
            ),
        )
        assertEquals(2, releasedNote.state.instrument.activeChromaticShiftSemitones)
        assertEquals(1, releasedNote.state.router.activeLeaseCount)
        assertEquals(1, releasedNote.state.router.activeCcGateCount)
        assertTrue(releasedNote.midiMessages().none { it is MidiMessage.NoteOff })

        val releasedCc = coordinator.reduce(
            releasedNote.state,
            PerformanceCommand.MidiMessages(
                deviceId = 11,
                portNumber = 1,
                messages = listOf(MidiMessage.ControlChange(3, 53, 0, 4L)),
            ),
        )
        assertEquals(0, releasedCc.state.instrument.activeChromaticShiftSemitones)
        assertEquals(0, releasedCc.state.router.activeLeaseCount)
        assertEquals(0, releasedCc.state.router.activeCcGateCount)
        assertTrue(releasedCc.midiMessages().none { it is MidiMessage.NoteOff })
    }

    @Test
    fun recordCancelsAnExistingTakeForMappedAndDirectStartRequests() {
        val mapping = MidiMapping(
            mapOf(MidiBindingKey(MidiBindingKey.Kind.NOTE, 42) to MidiAction.Record),
        )
        val recording = PerformanceCoordinatorState.initial().copy(
            mapping = mapping,
            toneRow = recordingRow(),
        )

        val mapped = coordinator.reduce(
            recording,
            PerformanceCommand.MidiMessages(10, 0, listOf(MidiMessage.NoteOn(0, 42, 100, 1L))),
        )
        assertEquals(ToneRowMode.IDLE, mapped.state.toneRow.mode)
        assertTrue(mapped.state.toneRow.entries.isEmpty())
        assertEquals(null, mapped.state.toneRow.currentRecordNote)

        val direct = coordinator.reduce(
            recording,
            PerformanceCommand.ToneRow(ToneRowAction.StartRecording(60), 2L),
        )
        assertEquals(ToneRowMode.IDLE, direct.state.toneRow.mode)
        assertTrue(direct.state.toneRow.entries.isEmpty())
    }

    @Test
    fun strumToneKeepsNavigationStableAndOwnsItsTargetedRelease() {
        val destination = MidiDestinationId("strum-destination")
        val initial = PerformanceCoordinatorState.initial(
            InstrumentConfig(chord = ChordLibrary.triad, padArticulation = PadArticulation.MUTED),
        )
        val navigated = coordinator.reduce(
            initial,
            PerformanceCommand.Instrument(
                InstrumentAction.PressInterval(TriggerSource.Touch(72), 1, 90, 1),
            ),
        ).state
        val beforeNote = navigated.instrument.currentNote
        val beforeHistory = navigated.instrument.previousDistinctNotes
        val source = TriggerSource.System("strum-test")

        val struck = coordinator.reduce(
            navigated,
            PerformanceCommand.Instrument(
                InstrumentAction.StrumTone(source, voiceIndex = 1, velocity = 117, timestampNanos = 2),
                destination = destination,
            ),
        )
        val noteOn = struck.midiMessages().filterIsInstance<MidiMessage.NoteOn>().single()
        assertEquals(117, noteOn.velocity)
        assertEquals(beforeNote, struck.state.instrument.currentNote)
        assertEquals(beforeHistory, struck.state.instrument.previousDistinctNotes)
        assertTrue(struck.effects.filterIsInstance<PerformanceEffect.Midi>().all { it.destination == destination })

        val released = coordinator.reduce(
            struck.state,
            PerformanceCommand.Instrument(
                InstrumentAction.Release(source, timestampNanos = 3),
                destination = destination,
            ),
        )
        assertEquals(noteOn.note, released.midiMessages().filterIsInstance<MidiMessage.NoteOff>().single().note)
        assertEquals(0, released.state.instrument.activeInstanceCount)
    }

    private fun stateWithManualRow(): PerformanceCoordinatorState {
        return PerformanceCoordinatorState.initial().copy(toneRow = playableRow())
    }

    private fun playableRow(): ToneRowState {
        return ToneRowState(
            mode = ToneRowMode.MANUAL_PLAYBACK,
            entries = listOf(
                ToneRowEntry(relativeDegree = 0, recordedMidiNote = 60, velocity = 96),
                ToneRowEntry(relativeDegree = 1, recordedMidiNote = 62, velocity = 88),
                ToneRowEntry(relativeDegree = 2, recordedMidiNote = 64, velocity = 80),
            ),
        )
    }

    private fun recordingRow(): ToneRowState {
        return ToneRowState(
            mode = ToneRowMode.RECORDING,
            entries = listOf(ToneRowEntry(relativeDegree = 0, recordedMidiNote = 60, velocity = 96)),
            currentRecordNote = 62,
            recordingCapacity = 7,
        )
    }

    private fun PerformanceCoordinatorTransition.midiMessages(): List<MidiMessage> {
        return effects.filterIsInstance<PerformanceEffect.Midi>().map { it.message }
    }

    private fun MidiMessage.edgeLabel(): String {
        return when (this) {
            is MidiMessage.RealTime -> "rt:${status.toString(16)}"
            is MidiMessage.NoteOn -> "on"
            is MidiMessage.NoteOff -> "off"
            is MidiMessage.ControlChange -> "cc"
            else -> "other"
        }
    }
}
