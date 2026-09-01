package dev.intervaltablet.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MidiRouterTest {
    private val noteKey = MidiBindingKey(MidiBindingKey.Kind.NOTE, 60)
    private val ccKey = MidiBindingKey(MidiBindingKey.Kind.CC, 7)
    private val mapping = MidiMapping(
        bindings = mapOf(
            noteKey to MidiAction.Move(1),
            ccKey to MidiAction.Move(-1),
        ),
    )
    private val router = MidiRouter(mapping)
    private val destinationA = MidiDestinationId("destination-a")
    private val destinationB = MidiDestinationId("destination-b")

    @Test
    fun mappedNoteOffUsesOriginalLeaseAfterEveryModeTransition() {
        for (modeAtOn in PassThroughMode.entries) {
            for (modeAtOff in PassThroughMode.entries) {
                val on = router.route(
                    state = MidiRouterState(mode = modeAtOn),
                    deviceId = 1,
                    portNumber = 0,
                    message = MidiMessage.NoteOn(0, 60, 100, 10),
                    destination = destinationA,
                    outputChannel = 5,
                )
                val changed = router.setMode(on.state, modeAtOff)
                val off = router.route(
                    state = changed,
                    deviceId = 1,
                    portNumber = 0,
                    message = MidiMessage.NoteOff(0, 60, 0, 20),
                    destination = destinationB,
                    outputChannel = 9,
                )

                assertEquals("$modeAtOn -> $modeAtOff", 0, off.state.activeLeaseCount)
                val effect = off.effects.single()
                assertEquals(destinationA, effect.destination)
                if (modeAtOn == PassThroughMode.PASS_THRU) {
                    assertTrue("$modeAtOn -> $modeAtOff", effect is RouterEffect.Midi)
                } else {
                    assertTrue("$modeAtOn -> $modeAtOff", effect is RouterEffect.Instrument)
                    assertTrue((effect as RouterEffect.Instrument).action is InstrumentAction.Release)
                }
            }
        }
    }

    @Test
    fun nonMappedHeldNoteKeepsDroppedOrForwardedRouteAcrossEveryModeTransition() {
        for (modeAtOn in PassThroughMode.entries) {
            for (modeAtOff in PassThroughMode.entries) {
                val on = router.route(
                    MidiRouterState(mode = modeAtOn),
                    1,
                    0,
                    MidiMessage.NoteOn(2, 61, 80, 10),
                    destinationA,
                )
                val off = router.route(
                    router.setMode(on.state, modeAtOff),
                    1,
                    0,
                    MidiMessage.NoteOff(2, 61, 0, 20),
                    destinationB,
                )
                val wasDropped = modeAtOn == PassThroughMode.OFF
                assertEquals("$modeAtOn -> $modeAtOff", !wasDropped, off.effects.isNotEmpty())
                if (!wasDropped) assertEquals(destinationA, off.effects.single().destination)
                assertEquals(0, off.state.activeLeaseCount)
            }
        }
    }

    @Test
    fun passThruUpdatesTheInstrumentAnchorBeforeForwarding() {
        val transition = router.route(
            MidiRouterState(mode = PassThroughMode.PASS_THRU),
            3,
            2,
            MidiMessage.NoteOn(4, 100, 90, 12),
            destinationA,
        )

        assertEquals(100, transition.state.lastNote)
        assertEquals(2, transition.effects.size)
        val anchor = transition.effects[0] as RouterEffect.Instrument
        assertEquals(InstrumentAction.AnchorExternal(100), anchor.action)
        assertEquals(MidiMessage.NoteOn(4, 100, 90, 12), (transition.effects[1] as RouterEffect.Midi).message)
    }

    @Test
    fun repeatedForwardedNoteOnsAreCountedAndReleasedIndividually() {
        var state = MidiRouterState(mode = PassThroughMode.ACTIVE)
        repeat(2) {
            state = router.route(
                state,
                1,
                0,
                MidiMessage.NoteOn(3, 61, 70, it.toLong()),
                destinationA,
            ).state
        }
        assertEquals(2, state.activeLeaseCount)
        assertEquals(2, state.leaseSnapshot().single().instanceCount)

        val firstOff = router.route(state, 1, 0, MidiMessage.NoteOff(3, 61), destinationB)
        assertEquals(1, firstOff.state.activeLeaseCount)
        assertEquals(destinationA, firstOff.effects.single().destination)
        val secondOff = router.route(firstOff.state, 1, 0, MidiMessage.NoteOff(3, 61), destinationB)
        assertEquals(0, secondOff.state.activeLeaseCount)
        assertEquals(destinationA, secondOff.effects.single().destination)
    }

    @Test
    fun ccMoveUsesAThresholdGateAndReleasesOnTheOriginalRoute() {
        val low = router.route(
            MidiRouterState(),
            1,
            0,
            MidiMessage.ControlChange(0, 7, 63, 1),
            destinationA,
            6,
        )
        assertTrue(low.instrumentActions.isEmpty())
        assertEquals(0, low.state.activeCcGateCount)

        val high = router.route(
            low.state,
            1,
            0,
            MidiMessage.ControlChange(0, 7, 64, 2),
            destinationA,
            6,
        )
        assertTrue(high.instrumentActions.single() is InstrumentAction.PressInterval)
        assertEquals(1, high.state.activeCcGateCount)

        val repeatedHigh = router.route(
            high.state,
            1,
            0,
            MidiMessage.ControlChange(0, 7, 127, 3),
            destinationB,
            9,
        )
        assertTrue(repeatedHigh.effects.isEmpty())

        val released = router.route(
            router.setMode(repeatedHigh.state, PassThroughMode.PASS_THRU),
            1,
            0,
            MidiMessage.ControlChange(0, 7, 0, 4),
            destinationB,
            9,
        )
        assertEquals(0, released.state.activeCcGateCount)
        assertEquals(destinationA, released.effects.single().destination)
        assertTrue((released.effects.single() as RouterEffect.Instrument).action is InstrumentAction.Release)
    }

    @Test
    fun passThruToggleCcUsesRisingEdgesAndCanReturnToActive() {
        val toggleKey = MidiBindingKey(MidiBindingKey.Kind.CC, 88)
        val toggleRouter = MidiRouter(MidiMapping(mapOf(toggleKey to MidiAction.TogglePassThrough)))
        var state = MidiRouterState(mode = PassThroughMode.ACTIVE)

        state = toggleRouter.route(
            state,
            1,
            0,
            MidiMessage.ControlChange(0, 88, 127, 1),
            destinationA,
        ).state
        assertEquals(PassThroughMode.PASS_THRU, state.mode)
        assertEquals(1, state.activeCcGateCount)

        state = toggleRouter.route(
            state,
            1,
            0,
            MidiMessage.ControlChange(0, 88, 127, 2),
            destinationA,
        ).state
        assertEquals(PassThroughMode.PASS_THRU, state.mode)

        state = toggleRouter.route(
            state,
            1,
            0,
            MidiMessage.ControlChange(0, 88, 0, 3),
            destinationA,
        ).state
        assertEquals(0, state.activeCcGateCount)

        state = toggleRouter.route(
            state,
            1,
            0,
            MidiMessage.ControlChange(0, 88, 127, 4),
            destinationA,
        ).state
        assertEquals(PassThroughMode.ACTIVE, state.mode)
    }

    @Test
    fun mappedPanicRemainsAvailableWhilePassThruIsActive() {
        val panicRouter = MidiRouter(
            MidiMapping(
                mapOf(MidiBindingKey(MidiBindingKey.Kind.NOTE, 93) to MidiAction.Panic),
            ),
        )
        val held = panicRouter.route(
            MidiRouterState(mode = PassThroughMode.PASS_THRU),
            1,
            0,
            MidiMessage.NoteOn(2, 61, 100, 1),
            destinationA,
        ).state

        val panic = panicRouter.route(
            held,
            1,
            0,
            MidiMessage.NoteOn(2, 93, 100, 2),
            destinationA,
        )

        assertEquals(0, panic.state.activeLeaseCount)
        assertTrue(panic.effects.any { it is RouterEffect.Instrument && it.action is InstrumentAction.Panic })
        assertFalse(
            panic.forwarded.filterIsInstance<MidiMessage.NoteOn>().any { it.note == 93 },
        )
    }

    @Test
    fun channelFilterPreventsMappingAndLastNoteAnchoringButStillAllowsActiveForwarding() {
        val initial = MidiRouterState(mode = PassThroughMode.ACTIVE_LAST_NOTE, inputChannel = 2)
        val rejected = router.route(initial, 1, 0, MidiMessage.NoteOn(1, 60, 90), destinationA)
        assertEquals(listOf(MidiMessage.NoteOn(1, 60, 90)), rejected.forwarded)
        assertTrue(rejected.instrumentActions.isEmpty())
        assertEquals(null, rejected.state.lastNote)

        val accepted = router.route(initial, 1, 0, MidiMessage.NoteOn(2, 60, 90), destinationA)
        assertTrue(accepted.forwarded.isEmpty())
        assertTrue(accepted.instrumentActions.single() is InstrumentAction.PressInterval)
    }

    @Test
    fun purgeDestinationEmitsExplicitOffsThenControllersAndKeepsOtherDestinations() {
        var state = MidiRouterState(mode = PassThroughMode.ACTIVE)
        repeat(2) {
            state = router.route(
                state,
                1,
                0,
                MidiMessage.NoteOn(3, 61, 80),
                destinationA,
            ).state
        }
        state = router.route(
            state,
            1,
            0,
            MidiMessage.NoteOn(4, 62, 80),
            destinationB,
        ).state

        val purge = router.purgeDestination(state, destinationA, 999)

        assertEquals(1, purge.state.activeLeaseCount)
        assertTrue(purge.effects.all { it.destination == destinationA })
        val messages = purge.forwarded
        assertEquals(2, messages.filterIsInstance<MidiMessage.NoteOff>().size)
        assertEquals(listOf(123, 120), messages.filterIsInstance<MidiMessage.ControlChange>().map { it.controller })
        assertTrue(messages.all { it.timestampNanos == 999L })
    }

    @Test
    fun resetDestinationTargetsTheNewSessionWithoutMutatingRouterState() {
        val state = MidiRouterState(mode = PassThroughMode.ACTIVE, inputChannel = 7)

        val reset = router.resetDestination(state, destinationB, timestampNanos = 1_234)

        assertEquals(state, reset.state)
        assertEquals(32, reset.effects.size)
        assertTrue(reset.effects.all { it.destination == destinationB })
        assertEquals(
            (0..15).flatMap { channel -> listOf(channel to 123, channel to 120) },
            reset.forwarded.filterIsInstance<MidiMessage.ControlChange>()
                .map { it.channel to it.controller },
        )
        assertTrue(reset.forwarded.all { it.timestampNanos == 1_234L })
    }

    @Test
    fun panicPurgesNotesAndCcGatesBeforeTheInstrumentPanicAndChannelControllers() {
        var state = MidiRouterState(mode = PassThroughMode.ACTIVE)
        state = router.route(
            state,
            1,
            0,
            MidiMessage.NoteOn(3, 61, 80),
            destinationA,
        ).state
        state = router.route(
            state,
            2,
            0,
            MidiMessage.NoteOn(1, 60, 80),
            destinationB,
            5,
        ).state
        state = router.route(
            state,
            2,
            0,
            MidiMessage.ControlChange(1, 7, 127),
            destinationB,
            5,
        ).state

        val panic = router.panic(state, 500, destinationB, 5)

        assertEquals(0, panic.state.activeLeaseCount)
        val panicIndex = panic.effects.indexOfFirst {
            it is RouterEffect.Instrument && it.action is InstrumentAction.Panic
        }
        assertTrue(panicIndex >= 3)
        assertFalse(panic.effects.drop(panicIndex + 1).any {
            it is RouterEffect.Instrument && it.action is InstrumentAction.Release
        })
        assertEquals(
            listOf(3 to 123, 3 to 120),
            panic.forwarded.filterIsInstance<MidiMessage.ControlChange>().map { it.channel to it.controller },
        )
    }

    @Test
    fun systemResetPerformsPanicAndIsForwardedLastWhenModeAllowsIt() {
        val held = router.route(
            MidiRouterState(mode = PassThroughMode.ACTIVE),
            1,
            0,
            MidiMessage.NoteOn(2, 61, 80),
            destinationA,
        ).state
        val resetMessage = MidiMessage.RealTime(0xFF, 700)
        val reset = router.route(held, 1, 0, resetMessage, destinationA, 4)

        assertEquals(0, reset.state.activeLeaseCount)
        assertEquals(resetMessage, (reset.effects.last() as RouterEffect.Midi).message)
        assertTrue(reset.instrumentActions.any { it is InstrumentAction.Panic })
    }

    @Test
    fun rawSysexIsOnlyForwardedInPassThru() {
        val raw = MidiMessage.Raw(listOf(0xF0, 0x7D, 0xF7))
        for (mode in PassThroughMode.entries) {
            val transition = router.route(MidiRouterState(mode = mode), 1, 0, raw, destinationA)
            assertEquals(mode == PassThroughMode.PASS_THRU, transition.forwarded.isNotEmpty())
        }
    }
}
