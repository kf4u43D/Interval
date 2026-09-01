package dev.intervaltablet

import dev.intervaltablet.data.MidiMappingSerializer
import dev.intervaltablet.domain.DefaultMidiMap
import dev.intervaltablet.domain.MidiAction
import dev.intervaltablet.domain.MidiBindingKey
import dev.intervaltablet.domain.MidiMapping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MidiMappingSerializerTest {
    @Test
    fun roundTripPreservesDefaultMappingAndThresholds() {
        val encoded = MidiMappingSerializer.encode(DefaultMidiMap.mapping)
        assertEquals(DefaultMidiMap.mapping, MidiMappingSerializer.decode(encoded))
    }

    @Test
    fun roundTripPreservesChannelSpecificBinding() {
        val key = MidiBindingKey(MidiBindingKey.Kind.CC, number = 12, channel = 4)
        val mapping = MidiMapping(
            bindings = mapOf(key to MidiAction.Move(-4)),
            ccThresholds = mapOf(key to 96),
        )
        assertEquals(mapping, MidiMappingSerializer.decode(MidiMappingSerializer.encode(mapping)))
    }

    @Test
    fun invalidOrFutureSchemaIsRejectedConservatively() {
        assertNull(MidiMappingSerializer.decode("not json"))
        assertNull(MidiMappingSerializer.decode("{\"schemaVersion\":2,\"bindings\":[]}"))
    }

    @Test
    fun roundTripCoversEveryActionVariant() {
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
        val mapping = MidiMapping(
            actions.mapIndexed { index, action ->
                MidiBindingKey(MidiBindingKey.Kind.NOTE, index) to action
            }.toMap(),
        )

        assertEquals(mapping, MidiMappingSerializer.decode(MidiMappingSerializer.encode(mapping)))
    }

    @Test
    fun duplicateInvalidThresholdAndOversizedPayloadAreRejected() {
        val duplicate = """{"schemaVersion":1,"bindings":[
            {"kind":"NOTE","number":1,"action":{"type":"undo"}},
            {"kind":"NOTE","number":1,"action":{"type":"same"}}
        ]}""".trimIndent()
        val noteThreshold = """{"schemaVersion":1,"bindings":[
            {"kind":"NOTE","number":1,"threshold":64,"action":{"type":"undo"}}
        ]}""".trimIndent()

        assertNull(MidiMappingSerializer.decode(duplicate))
        assertNull(MidiMappingSerializer.decode(noteThreshold))
        assertNull(MidiMappingSerializer.decode("x".repeat(1_048_577)))
    }
}
