package dev.intervaltablet.data

import dev.intervaltablet.domain.ClockSource
import dev.intervaltablet.domain.ToneRowEntry
import dev.intervaltablet.domain.ToneRowMode
import dev.intervaltablet.domain.ToneRowPlayMode
import dev.intervaltablet.domain.ToneRowState
import dev.intervaltablet.domain.TransportMode
import dev.intervaltablet.domain.TransportState

fun ToneRowState.toPersistenceSnapshot(): ToneRowSnapshot = ToneRowSnapshot(
    entries = entries.map { entry ->
        ToneRowEntrySnapshot(
            relativeDegree = entry.relativeDegree,
            recordedMidiNote = entry.recordedMidiNote,
            velocity = entry.velocity,
        )
    },
    intervalSequence = intervalSequence,
    playMode = playMode.toSnapshotMode(),
    inverted = inverted,
    transpositionSemitones = transpositionSemitones,
    translation = translation,
    octaveOffset = octaveOffset,
    randomState = randomState,
    referenceRootPitchClass = referenceRootPitchClass,
    referenceScaleId = referenceScaleId,
)

/** Restores content and transformations, but never active playback or transient cursors. */
fun ToneRowSnapshot.toStoppedDomainState(): ToneRowState = ToneRowState(
    mode = ToneRowMode.IDLE,
    entries = entries.map { entry ->
        ToneRowEntry(
            relativeDegree = entry.relativeDegree,
            recordedMidiNote = entry.recordedMidiNote,
            velocity = entry.velocity,
        )
    },
    rowIndex = 0,
    intervalSequence = intervalSequence,
    sequenceIndex = 0,
    playMode = playMode.toDomainMode(),
    inverted = inverted,
    transpositionSemitones = transpositionSemitones,
    translation = translation,
    octaveOffset = octaveOffset,
    randomState = randomState,
    referenceRootPitchClass = referenceRootPitchClass,
    referenceScaleId = referenceScaleId,
)

fun TransportState.toPersistenceSnapshot(): TransportOptionsSnapshot = TransportOptionsSnapshot(
    tempoBpm = tempoBpm,
    noteDurationPercent = noteDurationPercent,
    clocksPerStep = clocksPerStep,
    clockSource = when (clockSource) {
        ClockSource.INTERNAL -> StoredClockSource.INTERNAL
        ClockSource.MIDI -> StoredClockSource.MIDI
    },
)

/** Transport position and deadlines are intentionally not persisted or resumed. */
fun TransportOptionsSnapshot.toStoppedDomainState(): TransportState = TransportState(
    mode = TransportMode.STOPPED,
    clockSource = when (clockSource) {
        StoredClockSource.INTERNAL -> ClockSource.INTERNAL
        StoredClockSource.MIDI -> ClockSource.MIDI
    },
    clocksPerStep = clocksPerStep,
    tempoBpm = tempoBpm,
    noteDurationPercent = noteDurationPercent,
)

private fun ToneRowPlayMode.toSnapshotMode(): ToneRowPlaybackSnapshotMode = when (this) {
    ToneRowPlayMode.PRIME -> ToneRowPlaybackSnapshotMode.PRIME
    ToneRowPlayMode.RETRO -> ToneRowPlaybackSnapshotMode.RETRO
    ToneRowPlayMode.RANDOM -> ToneRowPlaybackSnapshotMode.RANDOM
    ToneRowPlayMode.PENDULUM -> ToneRowPlaybackSnapshotMode.PENDULUM
}

private fun ToneRowPlaybackSnapshotMode.toDomainMode(): ToneRowPlayMode = when (this) {
    ToneRowPlaybackSnapshotMode.PRIME -> ToneRowPlayMode.PRIME
    ToneRowPlaybackSnapshotMode.RETRO -> ToneRowPlayMode.RETRO
    ToneRowPlaybackSnapshotMode.RANDOM -> ToneRowPlayMode.RANDOM
    ToneRowPlaybackSnapshotMode.PENDULUM -> ToneRowPlayMode.PENDULUM
}
