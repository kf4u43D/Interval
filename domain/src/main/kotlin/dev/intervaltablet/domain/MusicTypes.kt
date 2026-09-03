package dev.intervaltablet.domain

import kotlin.math.abs

/** Inclusive MIDI note range. */
data class MidiNoteRange(val min: Int = 36, val max: Int = 95) {
    init {
        require(min in 0..127) { "min must be a MIDI note" }
        require(max in 0..127) { "max must be a MIDI note" }
        require(min <= max) { "min must be <= max" }
    }

    val center: Int get() = min + (max - min) / 2
    fun contains(note: Int): Boolean = note in min..max
}

data class ScaleDefinition(
    val id: String,
    val displayName: String,
    val offsets: List<Int>,
) {
    init {
        require(id.isNotBlank())
        require(displayName.isNotBlank())
        require(offsets.isNotEmpty())
        require(offsets.size <= 12)
        require(offsets.all { it in 0..11 })
        require(offsets.distinct().size == offsets.size)
        require(offsets == offsets.sorted())
        require(offsets.first() == 0) { "A scale must include its root at offset 0" }
    }
}

object ScaleLibrary {
    val chromatic = ScaleDefinition("chromatic", "Chromatic", (0..11).toList())
    val major = ScaleDefinition("major", "Major", listOf(0, 2, 4, 5, 7, 9, 11))
    val naturalMinor = ScaleDefinition("natural_minor", "Natural Minor", listOf(0, 2, 3, 5, 7, 8, 10))
    val harmonicMinor = ScaleDefinition("harmonic_minor", "Harmonic Minor", listOf(0, 2, 3, 5, 7, 8, 11))
    val melodicMinor = ScaleDefinition("melodic_minor", "Melodic Minor", listOf(0, 2, 3, 5, 7, 9, 11))
    val dorian = ScaleDefinition("dorian", "Dorian", listOf(0, 2, 3, 5, 7, 9, 10))
    val phrygian = ScaleDefinition("phrygian", "Phrygian", listOf(0, 1, 3, 5, 7, 8, 10))
    val lydian = ScaleDefinition("lydian", "Lydian", listOf(0, 2, 4, 6, 7, 9, 11))
    val mixolydian = ScaleDefinition("mixolydian", "Mixolydian", listOf(0, 2, 4, 5, 7, 9, 10))
    val locrian = ScaleDefinition("locrian", "Locrian", listOf(0, 1, 3, 5, 6, 8, 10))
    val majorPentatonic = ScaleDefinition("major_pentatonic", "Major Pentatonic", listOf(0, 2, 4, 7, 9))
    val minorPentatonic = ScaleDefinition("minor_pentatonic", "Minor Pentatonic", listOf(0, 3, 5, 7, 10))
    val blues = ScaleDefinition("blues", "Blues", listOf(0, 3, 5, 6, 7, 10))

    val all: List<ScaleDefinition> = listOf(
        major,
        naturalMinor,
        harmonicMinor,
        melodicMinor,
        dorian,
        phrygian,
        lydian,
        mixolydian,
        locrian,
        majorPentatonic,
        minorPentatonic,
        blues,
        chromatic,
    )

    fun byId(id: String): ScaleDefinition = all.firstOrNull { it.id == id } ?: major
}

enum class PitchMoveBoundary {
    NONE,
    CLAMPED,
    WRAPPED,
}

data class PitchMoveResult(
    val note: Int,
    val boundary: PitchMoveBoundary,
)

/**
 * Ordered notes belonging to a scale inside a MIDI range.
 *
 * The class is immutable and safe to recreate whenever key/scale/range changes.
 */
class PitchGrid(
    rootPitchClass: Int,
    val scale: ScaleDefinition,
    val range: MidiNoteRange,
    val wrap: Boolean,
) {
    val rootPitchClass: Int = floorMod(rootPitchClass, 12)
    private val notes: IntArray = (range.min..range.max)
        .filter { note -> floorMod(note - this.rootPitchClass, 12) in scale.offsets }
        .toIntArray()

    init {
        require(notes.isNotEmpty()) { "Scale and range must expose at least one note" }
    }

    val size: Int get() = notes.size

    fun allNotes(): List<Int> = notes.toList()

    fun contains(note: Int): Boolean = notes.binarySearch(note) >= 0

    fun indexOfExact(note: Int): Int = notes.binarySearch(note)

    fun noteAt(index: Int): Int = notes[normalizeIndex(index.toLong())]

    fun nearest(note: Int): Int = notes.minBy { abs(it - note) }

    /** Zero-based degree within the scale octave, or null when [note] is outside this grid. */
    fun degreeIndexOf(note: Int): Int? {
        if (!range.contains(note)) return null
        val index = scale.offsets.indexOf(floorMod(note - rootPitchClass, 12))
        return index.takeIf { it >= 0 }
    }

    fun home(): Int {
        val roots = notes.filter { floorMod(it, 12) == rootPitchClass }
        return roots.minByOrNull { abs(it - range.center) } ?: nearest(range.center)
    }

    /**
     * Preview a scale-degree movement and report whether a range boundary changed its target.
     * A non-scale anchor consumes the first step reaching the scale in the requested direction.
     */
    fun previewMove(anchor: Int, steps: Int): PitchMoveResult {
        if (steps == 0) {
            val target = if (range.contains(anchor)) anchor else nearest(anchor)
            return PitchMoveResult(
                note = target,
                boundary = if (target == anchor) PitchMoveBoundary.NONE else PitchMoveBoundary.CLAMPED,
            )
        }

        val exact = notes.binarySearch(anchor)
        val rawIndex = if (exact >= 0) {
            exact.toLong() + steps.toLong()
        } else if (steps > 0) {
            val firstAbove = notes.indexOfFirst { it > anchor }
            val start = if (firstAbove >= 0) firstAbove else notes.size
            start.toLong() + steps.toLong() - 1L
        } else {
            val firstBelow = notes.indexOfLast { it < anchor }
            val start = if (firstBelow >= 0) firstBelow else -1
            start.toLong() + steps.toLong() + 1L
        }

        val boundary = if (rawIndex in 0L..notes.lastIndex.toLong()) {
            PitchMoveBoundary.NONE
        } else if (wrap) {
            PitchMoveBoundary.WRAPPED
        } else {
            PitchMoveBoundary.CLAMPED
        }
        return PitchMoveResult(notes[normalizeIndex(rawIndex)], boundary)
    }

    /** Move by scale degrees. A non-scale anchor consumes the first step reaching the scale. */
    fun move(anchor: Int, steps: Int): Int = previewMove(anchor, steps).note

    /**
     * Resolve a degree movement without applying this grid's range policy.
     * Used for chord harmonies, which must be omitted rather than clamped or wrapped.
     */
    internal fun moveUnbounded(anchor: Int, steps: Int): Int {
        if (steps == 0) return anchor
        val direction = steps.compareTo(0)
        var candidate = anchor
        var remaining = kotlin.math.abs(steps.toLong())
        while (remaining > 0L) {
            do {
                candidate += direction
            } while (floorMod(candidate - rootPitchClass, 12) !in scale.offsets)
            remaining -= 1L
        }
        return candidate
    }

    fun moveChromatic(anchor: Int, semitones: Int): Int {
        val raw = anchor.toLong() + semitones.toLong()
        if (!wrap) return raw.coerceIn(range.min.toLong(), range.max.toLong()).toInt()
        val span = (range.max - range.min + 1).toLong()
        val remainder = (raw - range.min.toLong()) % span
        return range.min + (if (remainder < 0L) remainder + span else remainder).toInt()
    }

    /** Relative scale index around Home, useful for a 12-TET Tone Row representation. */
    fun relativeDegree(note: Int): Int {
        val homeIndex = notes.binarySearch(home())
        val noteIndex = notes.binarySearch(if (contains(note)) note else nearest(note))
        return noteIndex - homeIndex
    }

    fun noteFromRelativeDegree(relativeDegree: Int): Int {
        val homeIndex = notes.binarySearch(home())
        return notes[normalizeIndex(homeIndex.toLong() + relativeDegree.toLong())]
    }

    private fun normalizeIndex(index: Long): Int {
        return if (wrap) {
            val remainder = index % notes.size.toLong()
            (if (remainder < 0L) remainder + notes.size else remainder).toInt()
        } else {
            index.coerceIn(0L, notes.lastIndex.toLong()).toInt()
        }
    }
}

fun floorMod(value: Int, modulus: Int): Int {
    require(modulus > 0)
    val remainder = value % modulus
    return if (remainder < 0) remainder + modulus else remainder
}

private val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

fun midiNoteName(note: Int): String {
    require(note in 0..127)
    val octave = note / 12 - 1
    return "${NOTE_NAMES[note % 12]}$octave"
}
