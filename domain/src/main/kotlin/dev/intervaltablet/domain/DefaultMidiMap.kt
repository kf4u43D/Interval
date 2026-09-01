package dev.intervaltablet.domain

/** Factory-owned default so Reset Mapping never reuses a user-mutated map instance. */
object DefaultMidiMap {
    val mapping: MidiMapping
        get() = MidiMapping(
            buildMap {
                note(56, MidiAction.ChromaticShift(-1))
                note(57, MidiAction.Move(-9))
                note(58, MidiAction.Home(sound = false))
                note(59, MidiAction.Move(-8))
                note(60, MidiAction.Move(-7))
                note(61, MidiAction.Same)
                note(62, MidiAction.Move(-6))
                note(63, MidiAction.Undo)
                note(64, MidiAction.Move(-5))
                note(65, MidiAction.Move(-4))
                note(66, MidiAction.UndoThenMove(-1))
                note(67, MidiAction.Move(-3))
                note(68, MidiAction.Chromatic(-1))
                note(69, MidiAction.Move(-2))
                note(70, MidiAction.Random)
                note(71, MidiAction.Move(-1))
                note(72, MidiAction.Move(0))
                note(73, MidiAction.Same)
                note(74, MidiAction.Home(sound = true))
                note(75, MidiAction.SamePitch)
                note(76, MidiAction.Move(0))
                note(77, MidiAction.Move(+1))
                note(78, MidiAction.Random)
                note(79, MidiAction.Move(+2))
                note(80, MidiAction.Chromatic(+1))
                note(81, MidiAction.Move(+3))
                note(82, MidiAction.UndoThenMove(+1))
                note(83, MidiAction.Move(+4))
                note(84, MidiAction.Move(+5))
                note(85, MidiAction.Undo)
                note(86, MidiAction.Move(+6))
                note(87, MidiAction.Same)
                note(88, MidiAction.Move(+7))
                note(89, MidiAction.Move(+8))
                note(90, MidiAction.Home(sound = false))
                note(91, MidiAction.Move(+9))
                note(92, MidiAction.ChromaticShift(+1))
                for (allOff in 93..96) note(allOff, MidiAction.Panic)
                cc(88, MidiAction.TogglePassThrough)
            },
        )

    private fun MutableMap<MidiBindingKey, MidiAction>.note(number: Int, action: MidiAction) {
        put(MidiBindingKey(MidiBindingKey.Kind.NOTE, number, channel = null), action)
    }

    private fun MutableMap<MidiBindingKey, MidiAction>.cc(number: Int, action: MidiAction) {
        put(MidiBindingKey(MidiBindingKey.Kind.CC, number, channel = null), action)
    }
}
