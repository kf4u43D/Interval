import dev.intervaltablet.domain.*
import dev.intervaltablet.midi.MidiMessageParser
import dev.intervaltablet.midi.toByteArray

private fun checkCondition(condition: Boolean, message: String) {
    if (!condition) error(message)
}

private fun verifyInstrumentDomain() {
    val reducer = IntervalReducer()
    var state = reducer.initialState()
    checkCondition(state.currentNote == 60, "Home C major should be C4, got ${state.currentNote}")

    val touch = TriggerSource.Touch(1)
    var transition = reducer.reduce(state, InstrumentAction.PressInterval(touch, 1, 100))
    state = transition.state
    checkCondition(state.currentNote == 62, "+1 in C major should produce D4")
    checkCondition(state.activeInstanceCount == 1, "One active note expected")
    checkCondition(transition.events.filterIsInstance<OutputEvent.MidiOut>().size == 1, "One MIDI Note On expected")

    transition = reducer.reduce(state, InstrumentAction.Release(touch))
    state = transition.state
    checkCondition(state.activeInstanceCount == 0, "Release must clear source")

    transition = reducer.reduce(state, InstrumentAction.PressInterval(touch, 0, 100))
    state = transition.state
    val historyAfterZero = state.previousDistinctNotes.size
    state = reducer.reduce(state, InstrumentAction.Release(touch)).state
    state = reducer.reduce(state, InstrumentAction.Undo(touch, 100)).state
    checkCondition(state.currentNote == 60, "Undo should return to previous distinct note")
    checkCondition(historyAfterZero == 1, "Zero move must not add history")

    state = reducer.reduce(state, InstrumentAction.Release(touch)).state
    state = reducer.reduce(state, InstrumentAction.SetChord(ChordLibrary.triad)).state
    transition = reducer.reduce(state, InstrumentAction.PressInterval(touch, 4, 100))
    checkCondition(transition.state.activeInstanceCount == 3, "Triad must create three instances")

    val router = MidiRouter()
    var routerState = MidiRouterState(mode = PassThroughMode.ACTIVE_LAST_NOTE)
    val external = MidiMessage.NoteOn(channel = 0, note = 48, velocity = 80)
    val routedExternal = router.route(routerState, deviceId = 1, portNumber = 0, message = external)
    routerState = routedExternal.state
    checkCondition(routedExternal.forwarded == listOf(external), "Unmapped note should pass in Active Last Note")
    checkCondition(routedExternal.instrumentActions.single() == InstrumentAction.AnchorExternal(48), "Last note anchor expected")

    val mapped = MidiMessage.NoteOn(channel = 0, note = 77, velocity = 90)
    val routedMapped = router.route(routerState, 1, 0, mapped)
    checkCondition(routedMapped.instrumentActions.first() is InstrumentAction.PressInterval, "Mapped +1 action expected")

    val exactMapping = MidiMapping(
        mapOf(
            MidiBindingKey(MidiBindingKey.Kind.NOTE, 60, null) to MidiAction.Move(1),
            MidiBindingKey(MidiBindingKey.Kind.NOTE, 60, 2) to MidiAction.Move(3),
        ),
    )
    checkCondition(exactMapping.noteAction(2, 60) == MidiAction.Move(3), "Channel-specific mapping must win")
    checkCondition(exactMapping.noteAction(1, 60) == MidiAction.Move(1), "Omni mapping must remain available")

    val grid = InstrumentConfig().grid()
    val rowReducer = ToneRowReducer(grid)
    var row = rowReducer.reduce(ToneRowState(), ToneRowAction.StartRecording(60)).state
    repeat(7) {
        row = rowReducer.reduce(row, ToneRowAction.RecordMove(1, 90)).state
    }
    checkCondition(row.entries.size == 7, "C major row should contain seven pitch classes")
    checkCondition(row.mode == ToneRowMode.MANUAL_PLAYBACK, "Row should finish automatically")
    checkCondition(row.entries.map { floorMod(it.recordedMidiNote, 12) }.distinct().size == 7, "Row classes must be unique")

    val clock = MidiClockReducer()
    var transport = clock.onRealtime(TransportState(), 0xFA).state
    var ticks = 0
    repeat(6) {
        val clockTransition = clock.onRealtime(transport, 0xF8)
        transport = clockTransition.state
        ticks += clockTransition.events.count { it == TransportEvent.Tick }
    }
    checkCondition(ticks == 1, "Six MIDI clocks should emit one default step")
}

private fun verifyMidiCodec() {
    val parser = MidiMessageParser()
    val first = parser.consume(
        byteArrayOf(0x90.toByte(), 60, 100, 0xF8.toByte(), 61, 0),
        offset = 0,
        count = 6,
        timestampNanos = 42L,
    )
    checkCondition(first.size == 3, "Running status with interleaved clock should decode three messages")
    checkCondition(first[0] == MidiMessage.NoteOn(0, 60, 100, 42L), "First Note On decode failed")
    checkCondition(first[1] == MidiMessage.RealTime(0xF8, 42L), "Realtime byte must not disrupt running status")
    checkCondition(first[2] == MidiMessage.NoteOff(0, 61, 0, 42L), "Velocity-zero Note On must normalize to Note Off")

    parser.reset()
    checkCondition(parser.consume(byteArrayOf(0xE2.toByte(), 0x01), 0, 2, 99L).isEmpty(), "Split message should remain pending")
    val bend = parser.consume(byteArrayOf(0x40), 0, 1, 100L).single()
    checkCondition(bend == MidiMessage.PitchBend(2, 8193, 100L), "Split pitch bend decode failed")
    checkCondition(bend.toByteArray()?.contentEquals(byteArrayOf(0xE2.toByte(), 0x01, 0x40)) == true, "Pitch bend encode failed")

    parser.reset()
    val sysex = parser.consume(
        byteArrayOf(0xF0.toByte(), 0x7D, 0x01, 0xF8.toByte(), 0xF7.toByte()),
        0,
        5,
        123L,
    )
    checkCondition(sysex[0] == MidiMessage.RealTime(0xF8, 123L), "Realtime inside SysEx should be emitted immediately")
    checkCondition(sysex[1] == MidiMessage.Raw(listOf(0xF0, 0x7D, 0x01, 0xF7), 123L), "Complete SysEx packet decode failed")
}

fun main() {
    verifyInstrumentDomain()
    verifyMidiCodec()
    println("Domain and MIDI codec smoke tests: OK")
}
