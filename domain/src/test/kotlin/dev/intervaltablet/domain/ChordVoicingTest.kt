package dev.intervaltablet.domain

import kotlin.math.max
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordVoicingTest {
    private val reducer = IntervalReducer()
    private val source = TriggerSource.Touch(11)

    @Test
    fun libraryMatchesAllTenDocumentedDefinitions() {
        val expected = linkedMapOf(
            ChordLibrary.off to listOf(ChordTone.Degree(0)),
            ChordLibrary.octaves to listOf(ChordTone.Degree(0), ChordTone.Octave(-1), ChordTone.Octave(-2)),
            ChordLibrary.third to degrees(0, -2, 0),
            ChordLibrary.sixth to degrees(0, -5, 0),
            ChordLibrary.triad to degrees(0, -2, -4),
            ChordLibrary.triad2 to degrees(0, -3, -5),
            ChordLibrary.triad3 to degrees(0, -2, -5),
            ChordLibrary.jazz to degrees(0, -3, -9),
            ChordLibrary.copland to degrees(0, -6, -12),
            ChordLibrary.wide to degrees(0, -11, -22),
        )

        assertEquals(10, ChordLibrary.all.size)
        for ((definition, tones) in expected) {
            assertEquals(tones, definition.tones)
        }
    }

    @Test
    fun everyVoicingPreservesToneOrderDuplicatesAndVelocityRules() {
        val velocities = listOf(1, 2, 64, 127)
        for (chord in ChordLibrary.all) {
            for (velocity in velocities) {
                val config = InstrumentConfig(
                    range = MidiNoteRange(0, 127),
                    solfegeWrap = true,
                    outputChannel = 9,
                    chord = chord,
                    padArticulation = PadArticulation.STACKED,
                )
                val initial = reducer.initialState(config)
                val transition = reducer.reduce(
                    initial,
                    InstrumentAction.PressInterval(source, 0, velocity, timestampNanos = 42L),
                )
                val actual = transition.state.activeBySource.getValue(source)
                val expected = expectedVoicing(config, initial.currentNote, velocity)

                assertEquals("notes for ${chord.id} at velocity $velocity", expected, actual)
                assertEquals(
                    expected.map { it.note },
                    transition.events.filterIsInstance<OutputEvent.MidiOut>()
                        .map { it.message }
                        .filterIsInstance<MidiMessage.NoteOn>()
                        .map { it.note },
                )
            }
        }
    }

    @Test
    fun outOfRangeHarmoniesAreOmittedWithoutClampOrWrap() {
        for (wrap in listOf(false, true)) {
            for (chord in ChordLibrary.all) {
                val config = InstrumentConfig(
                    range = MidiNoteRange(60, 71),
                    solfegeWrap = wrap,
                    chord = chord,
                    padArticulation = PadArticulation.STACKED,
                )
                val initial = reducer.initialState(config)
                assertEquals(60, initial.currentNote)

                val transition = reducer.reduce(
                    initial,
                    InstrumentAction.PressInterval(source, 0, 100),
                )
                val actual = transition.state.activeBySource.getValue(source)
                val expected = expectedVoicing(config, lead = 60, velocity = 100)
                assertEquals("${chord.id}, wrap=$wrap", expected, actual)
                assertTrue(actual.all { config.range.contains(it.note) })
            }
        }

        val triad = InstrumentConfig(
            range = MidiNoteRange(60, 71),
            solfegeWrap = true,
            chord = ChordLibrary.triad,
            padArticulation = PadArticulation.STACKED,
        )
        val played = reducer.reduce(
            reducer.initialState(triad),
            InstrumentAction.PressInterval(source, 0, 100),
        )
        assertEquals(listOf(60), played.state.activeBySource.getValue(source).map { it.note })
    }

    private fun expectedVoicing(
        config: InstrumentConfig,
        lead: Int,
        velocity: Int,
    ): List<ActiveNoteInstance> {
        return config.chord.tones.mapIndexedNotNull { index, tone ->
            val note = when (tone) {
                is ChordTone.Degree -> degreeWithoutRange(lead, tone.steps, config)
                is ChordTone.Octave -> lead + tone.octaves * 12
            }
            note.takeIf(config.range::contains)?.let {
                ActiveNoteInstance(
                    note = it,
                    velocity = if (index == 0) velocity else max(1, velocity / 2),
                    channel = config.outputChannel,
                )
            }
        }
    }

    private fun degreeWithoutRange(anchor: Int, steps: Int, config: InstrumentConfig): Int {
        if (steps == 0) return anchor
        var note = anchor
        val direction = steps.compareTo(0)
        repeat(kotlin.math.abs(steps)) {
            do {
                note += direction
            } while (floorMod(note - config.rootPitchClass, 12) !in config.scale.offsets)
        }
        return note
    }

    private fun degrees(vararg steps: Int): List<ChordTone> = steps.map(ChordTone::Degree)
}
