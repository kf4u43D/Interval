package dev.intervaltablet.domain

sealed interface ChordTone {
    data class Degree(val steps: Int) : ChordTone
    data class Octave(val octaves: Int) : ChordTone
}

data class ChordDefinition(
    val id: String,
    val displayName: String,
    val tones: List<ChordTone>,
) {
    init {
        require(id.isNotBlank())
        require(tones.isNotEmpty())
        require(tones.size <= 3)
        require(tones.first() == ChordTone.Degree(0))
    }
}

object ChordLibrary {
    val off = ChordDefinition("off", "Off", listOf(ChordTone.Degree(0)))
    val octaves = ChordDefinition(
        "octaves",
        "Octaves",
        listOf(ChordTone.Degree(0), ChordTone.Octave(-1), ChordTone.Octave(-2)),
    )
    val third = degrees("third", "Third", 0, -2, 0)
    val sixth = degrees("sixth", "Sixth", 0, -5, 0)
    val triad = degrees("triad", "Triad", 0, -2, -4)
    val triad2 = degrees("triad_2", "Triad 2", 0, -3, -5)
    val triad3 = degrees("triad_3", "Triad 3", 0, -2, -5)
    val jazz = degrees("jazz", "Jazz", 0, -3, -9)
    val copland = degrees("copland", "Copland", 0, -6, -12)
    val wide = degrees("wide", "Wide", 0, -11, -22)

    val all: List<ChordDefinition> = listOf(
        off,
        octaves,
        third,
        sixth,
        triad,
        triad2,
        triad3,
        jazz,
        copland,
        wide,
    )

    fun byId(id: String): ChordDefinition = all.firstOrNull { it.id == id } ?: off

    private fun degrees(id: String, name: String, vararg values: Int): ChordDefinition {
        return ChordDefinition(id, name, values.map { ChordTone.Degree(it) })
    }
}
