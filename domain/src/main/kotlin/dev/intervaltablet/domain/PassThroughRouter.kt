package dev.intervaltablet.domain

enum class PassThroughMode {
    OFF,
    ACTIVE,
    ACTIVE_LAST_NOTE,
    PASS_THRU,
}

enum class LeaseRoute { MAPPED, FORWARDED, DROPPED }

data class MidiDestinationId(val value: String) {
    init {
        require(value.isNotBlank()) { "A MIDI destination id must not be blank" }
    }

    companion object {
        val Default: MidiDestinationId = MidiDestinationId("default")
    }
}

data class IncomingNoteKey(
    val deviceId: Int,
    val portNumber: Int,
    val channel: Int,
    val note: Int,
) {
    init {
        require(portNumber >= 0)
        require(channel in 0..15)
        require(note in 0..127)
    }
}

data class RoutingLease(
    val route: LeaseRoute,
    val source: TriggerSource.Midi,
    val modeAtNoteOn: PassThroughMode,
    val destination: MidiDestinationId,
    val outputChannel: Int,
    val instanceCount: Int = 1,
) {
    init {
        require(outputChannel in 0..15)
        require(instanceCount > 0)
    }
}

typealias NoteLease = RoutingLease

data class IncomingCcKey(
    val deviceId: Int,
    val portNumber: Int,
    val channel: Int,
    val controller: Int,
) {
    init {
        require(portNumber >= 0)
        require(channel in 0..15)
        require(controller in 0..127)
    }
}

data class CcGateLease(
    val source: TriggerSource.System,
    val action: MidiAction,
    val modeAtPress: PassThroughMode,
    val destination: MidiDestinationId,
    val outputChannel: Int,
    val threshold: Int,
) {
    init {
        require(outputChannel in 0..15)
        require(threshold in 1..127)
    }
}

data class MidiRouterState(
    val mode: PassThroughMode = PassThroughMode.ACTIVE,
    val inputChannel: Int? = null,
    internal val leases: Map<IncomingNoteKey, List<RoutingLease>> = emptyMap(),
    internal val ccGates: Map<IncomingCcKey, CcGateLease> = emptyMap(),
    val lastNote: Int? = null,
) {
    init {
        require(inputChannel == null || inputChannel in 0..15)
        require(lastNote == null || lastNote in 0..127)
    }

    val activeLeaseCount: Int
        get() = leases.values.flatten().sumOf { it.instanceCount } + ccGates.size

    val activeCcGateCount: Int get() = ccGates.size

    fun leaseSnapshot(): List<RoutingLease> = leases.values.flatten()
}

sealed interface RouterEffect {
    val destination: MidiDestinationId

    data class Instrument(
        override val destination: MidiDestinationId,
        val action: InstrumentAction,
    ) : RouterEffect

    data class Midi(
        override val destination: MidiDestinationId,
        val message: MidiMessage,
    ) : RouterEffect
}

data class RouterTransition(
    val state: MidiRouterState,
    val effects: List<RouterEffect> = emptyList(),
    val consumedMappedAction: MidiAction? = null,
    /** Non-null only on the press/rising edge that must execute a non-instrument command. */
    val triggeredMappedAction: MidiAction? = null,
) {
    /** Compatibility views. New coordinators must consume [effects] to preserve global order and destination. */
    val instrumentActions: List<InstrumentAction>
        get() = effects.mapNotNull { (it as? RouterEffect.Instrument)?.action }

    val forwarded: List<MidiMessage>
        get() = effects.mapNotNull { (it as? RouterEffect.Midi)?.message }
}

class MidiRouter(val mapping: MidiMapping = DefaultMidiMap.mapping) {
    fun setMode(state: MidiRouterState, mode: PassThroughMode): MidiRouterState = state.copy(mode = mode)

    fun setInputChannel(state: MidiRouterState, channel: Int?): MidiRouterState {
        require(channel == null || channel in 0..15)
        return state.copy(inputChannel = channel)
    }

    fun withMapping(replacement: MidiMapping): MidiRouter = MidiRouter(replacement)

    fun withDefaultMapping(): MidiRouter = MidiRouter(DefaultMidiMap.mapping)

    fun route(
        state: MidiRouterState,
        deviceId: Int,
        portNumber: Int,
        message: MidiMessage,
        destination: MidiDestinationId = MidiDestinationId.Default,
        outputChannel: Int = 0,
    ): RouterTransition {
        require(outputChannel in 0..15)
        return when (message) {
            is MidiMessage.NoteOn -> routeNoteOn(
                state,
                deviceId,
                portNumber,
                message,
                destination,
                outputChannel,
            )
            is MidiMessage.NoteOff -> routeNoteOff(state, deviceId, portNumber, message, destination)
            is MidiMessage.ControlChange -> routeCc(
                state,
                deviceId,
                portNumber,
                message,
                destination,
                outputChannel,
            )
            is MidiMessage.RealTime -> if (message.status == SYSTEM_RESET) {
                routeSystemReset(state, message, destination, outputChannel)
            } else {
                routeUnmapped(state, message, destination)
            }
            is MidiMessage.Raw -> RouterTransition(
                state = state,
                effects = if (state.mode == PassThroughMode.PASS_THRU) {
                    listOf(RouterEffect.Midi(destination, message))
                } else {
                    emptyList()
                },
            )
            else -> routeUnmapped(state, message, destination)
        }
    }

    fun purgeSource(
        state: MidiRouterState,
        deviceId: Int,
        portNumber: Int,
        timestampNanos: Long,
    ): RouterTransition {
        return purge(
            state = state,
            timestampNanos = timestampNanos,
            noteMatches = { key, _ -> key.deviceId == deviceId && key.portNumber == portNumber },
            ccMatches = { key, _ -> key.deviceId == deviceId && key.portNumber == portNumber },
        ).toTransition()
    }

    fun purgeDestination(
        state: MidiRouterState,
        destination: MidiDestinationId,
        timestampNanos: Long,
    ): RouterTransition {
        return purge(
            state = state,
            timestampNanos = timestampNanos,
            noteMatches = { _, lease -> lease.destination == destination },
            ccMatches = { _, gate -> gate.destination == destination },
        ).toTransition()
    }

    /**
     * Clears any voices a newly opened destination may still retain from a previous
     * physical session. The transition is deliberately stateless and targeted so it
     * can be ordered after the old destination has been purged.
     */
    fun resetDestination(
        state: MidiRouterState,
        destination: MidiDestinationId,
        timestampNanos: Long,
    ): RouterTransition {
        return RouterTransition(
            state = state,
            // The disconnected session may have forwarded voices on any incoming channel,
            // and its leases are intentionally gone by the time it reopens. Reset all 16
            // channels rather than guessing from the current instrument configuration.
            effects = (0..15).flatMap { channel ->
                listOf(
                    RouterEffect.Midi(
                        destination,
                        MidiMessage.ControlChange(channel, ALL_NOTES_OFF, 0, timestampNanos),
                    ),
                    RouterEffect.Midi(
                        destination,
                        MidiMessage.ControlChange(channel, ALL_SOUND_OFF, 0, timestampNanos),
                    ),
                )
            },
        )
    }

    fun panic(
        state: MidiRouterState,
        timestampNanos: Long,
        fallbackDestination: MidiDestinationId = MidiDestinationId.Default,
        fallbackOutputChannel: Int = 0,
    ): RouterTransition {
        require(fallbackOutputChannel in 0..15)
        val purged = purge(
            state = state,
            timestampNanos = timestampNanos,
            noteMatches = { _, _ -> true },
            ccMatches = { _, _ -> true },
        )
        val remainingControls = purged.controlEffects.filterNot { effect ->
            effect is RouterEffect.Midi &&
                effect.destination == fallbackDestination &&
                (effect.message as? MidiMessage.ControlChange)?.channel == fallbackOutputChannel
        }
        return RouterTransition(
            state = purged.state,
            effects = purged.releaseEffects +
                RouterEffect.Instrument(fallbackDestination, InstrumentAction.Panic(timestampNanos)) +
                remainingControls,
        )
    }

    private fun routeNoteOn(
        state: MidiRouterState,
        deviceId: Int,
        portNumber: Int,
        message: MidiMessage.NoteOn,
        destination: MidiDestinationId,
        outputChannel: Int,
    ): RouterTransition {
        val key = IncomingNoteKey(deviceId, portNumber, message.channel, message.note)
        val source = TriggerSource.Midi(deviceId, portNumber, message.channel, message.note)
        val accepted = acceptsChannel(state, message.channel)
        val action = if (accepted) mapping.noteAction(message.channel, message.note) else null
        val reservedInPassThru = action == MidiAction.Panic || action == MidiAction.TogglePassThrough

        if (state.mode == PassThroughMode.PASS_THRU && !reservedInPassThru) {
            val lease = RoutingLease(
                route = LeaseRoute.FORWARDED,
                source = source,
                modeAtNoteOn = state.mode,
                destination = destination,
                outputChannel = message.channel,
            )
            return RouterTransition(
                state = addLease(state, key, lease).copy(lastNote = message.note),
                effects = listOf(
                    RouterEffect.Instrument(destination, InstrumentAction.AnchorExternal(message.note)),
                    RouterEffect.Midi(destination, message),
                ),
            )
        }

        if (action != null) {
            if (action == MidiAction.Panic) {
                return panic(state, message.timestampNanos, destination, outputChannel)
                    .copy(consumedMappedAction = action, triggeredMappedAction = action)
            }
            if (action == MidiAction.TogglePassThrough) {
                val nextMode = if (state.mode == PassThroughMode.PASS_THRU) {
                    PassThroughMode.ACTIVE
                } else {
                    PassThroughMode.PASS_THRU
                }
                val consumedLease = RoutingLease(
                    route = LeaseRoute.DROPPED,
                    source = source,
                    modeAtNoteOn = state.mode,
                    destination = destination,
                    outputChannel = outputChannel,
                )
                return RouterTransition(
                    state = addLease(state.copy(mode = nextMode), key, consumedLease),
                    consumedMappedAction = action,
                    triggeredMappedAction = action,
                )
            }

            val previous = removeMappedLeases(state, key, message.timestampNanos)
            val lease = RoutingLease(
                route = LeaseRoute.MAPPED,
                source = source,
                modeAtNoteOn = state.mode,
                destination = destination,
                outputChannel = outputChannel,
            )
            return RouterTransition(
                state = addLease(previous.state, key, lease),
                effects = previous.effects + action.toInstrumentActions(
                    source,
                    message.velocity,
                    message.timestampNanos,
                ).map { RouterEffect.Instrument(destination, it) },
                consumedMappedAction = action,
                triggeredMappedAction = action,
            )
        }

        val forwarded = passesUnmapped(state.mode)
        val route = if (forwarded) LeaseRoute.FORWARDED else LeaseRoute.DROPPED
        val lease = RoutingLease(
            route = route,
            source = source,
            modeAtNoteOn = state.mode,
            destination = destination,
            outputChannel = message.channel,
        )
        val anchors = accepted && state.mode == PassThroughMode.ACTIVE_LAST_NOTE
        return RouterTransition(
            state = addLease(state, key, lease).copy(
                lastNote = if (anchors) message.note else state.lastNote,
            ),
            effects = buildList {
                if (anchors) add(RouterEffect.Instrument(destination, InstrumentAction.AnchorExternal(message.note)))
                if (forwarded) add(RouterEffect.Midi(destination, message))
            },
        )
    }

    private fun routeNoteOff(
        state: MidiRouterState,
        deviceId: Int,
        portNumber: Int,
        message: MidiMessage.NoteOff,
        currentDestination: MidiDestinationId,
    ): RouterTransition {
        val key = IncomingNoteKey(deviceId, portNumber, message.channel, message.note)
        val leasesForKey = state.leases[key].orEmpty()
        if (leasesForKey.isEmpty()) {
            val mappedAction = if (
                state.mode != PassThroughMode.PASS_THRU && acceptsChannel(state, message.channel)
            ) {
                mapping.noteAction(message.channel, message.note)
            } else {
                null
            }
            if (mappedAction != null) {
                val source = TriggerSource.Midi(deviceId, portNumber, message.channel, message.note)
                return RouterTransition(
                    state = state,
                    effects = if (mappedAction.holdsGeneratedNotes()) {
                        listOf(
                            RouterEffect.Instrument(
                                currentDestination,
                                InstrumentAction.Release(source, message.velocity, message.timestampNanos),
                            ),
                        )
                    } else {
                        emptyList()
                    },
                    consumedMappedAction = mappedAction,
                )
            }
            return RouterTransition(
                state = state,
                effects = if (passesUnmapped(state.mode)) {
                    listOf(RouterEffect.Midi(currentDestination, message))
                } else {
                    emptyList()
                },
            )
        }

        val lease = leasesForKey.first()
        val remainingFirst = lease.instanceCount - 1
        val remaining = if (remainingFirst == 0) {
            leasesForKey.drop(1)
        } else {
            listOf(lease.copy(instanceCount = remainingFirst)) + leasesForKey.drop(1)
        }
        val nextLeases = if (remaining.isEmpty()) state.leases - key else state.leases + (key to remaining)
        val effect = when (lease.route) {
            LeaseRoute.MAPPED -> RouterEffect.Instrument(
                lease.destination,
                InstrumentAction.Release(lease.source, message.velocity, message.timestampNanos),
            )
            LeaseRoute.FORWARDED -> RouterEffect.Midi(lease.destination, message)
            LeaseRoute.DROPPED -> null
        }
        return RouterTransition(
            state = state.copy(leases = nextLeases),
            effects = listOfNotNull(effect),
        )
    }

    private fun routeCc(
        state: MidiRouterState,
        deviceId: Int,
        portNumber: Int,
        message: MidiMessage.ControlChange,
        destination: MidiDestinationId,
        outputChannel: Int,
    ): RouterTransition {
        val key = IncomingCcKey(deviceId, portNumber, message.channel, message.controller)
        val existingGate = state.ccGates[key]
        if (existingGate != null) {
            if (message.value >= existingGate.threshold) {
                return RouterTransition(state, consumedMappedAction = existingGate.action)
            }
            return RouterTransition(
                state = state.copy(ccGates = state.ccGates - key),
                effects = if (existingGate.action.holdsGeneratedNotes()) {
                    listOf(
                        RouterEffect.Instrument(
                            existingGate.destination,
                            InstrumentAction.Release(existingGate.source, 0, message.timestampNanos),
                        ),
                    )
                } else {
                    emptyList()
                },
                consumedMappedAction = existingGate.action,
            )
        }

        val accepted = acceptsChannel(state, message.channel)
        val mappedAction = if (accepted) mapping.ccAction(message.channel, message.controller) else null
        val action = mappedAction.takeIf {
            state.mode != PassThroughMode.PASS_THRU ||
                it == MidiAction.Panic ||
                it == MidiAction.TogglePassThrough
        }
        if (action == null) return routeUnmapped(state, message, destination)

        val threshold = mapping.ccThreshold(message.channel, message.controller)
        if (message.value < threshold) {
            return RouterTransition(state, consumedMappedAction = action)
        }

        val source = TriggerSource.System("cc:$deviceId:$portNumber:${message.channel}:${message.controller}")
        if (action == MidiAction.Panic) {
            return panic(state, message.timestampNanos, destination, outputChannel)
                .copy(consumedMappedAction = action, triggeredMappedAction = action)
        }
        if (action == MidiAction.TogglePassThrough) {
            val nextMode = if (state.mode == PassThroughMode.PASS_THRU) {
                PassThroughMode.ACTIVE
            } else {
                PassThroughMode.PASS_THRU
            }
            val gate = CcGateLease(
                source = source,
                action = action,
                modeAtPress = state.mode,
                destination = destination,
                outputChannel = outputChannel,
                threshold = threshold,
            )
            return RouterTransition(
                state = state.copy(
                    mode = nextMode,
                    ccGates = state.ccGates + (key to gate),
                ),
                consumedMappedAction = action,
                triggeredMappedAction = action,
            )
        }

        val actions = action.toInstrumentActions(source, DEFAULT_CC_VELOCITY, message.timestampNanos)
        // Every CC binding is edge-triggered. Held musical actions additionally emit a
        // Release when this gate crosses below its threshold; one-shot controls only clear it.
        val gate = CcGateLease(
            source = source,
            action = action,
            modeAtPress = state.mode,
            destination = destination,
            outputChannel = outputChannel,
            threshold = threshold,
        )
        return RouterTransition(
            state = state.copy(ccGates = state.ccGates + (key to gate)),
            effects = actions.map { RouterEffect.Instrument(destination, it) },
            consumedMappedAction = action,
            triggeredMappedAction = action,
        )
    }

    private fun routeSystemReset(
        state: MidiRouterState,
        message: MidiMessage.RealTime,
        destination: MidiDestinationId,
        outputChannel: Int,
    ): RouterTransition {
        val shouldForward = passesUnmapped(state.mode)
        val panic = panic(state, message.timestampNanos, destination, outputChannel)
        return panic.copy(
            effects = panic.effects + if (shouldForward) {
                listOf(RouterEffect.Midi(destination, message))
            } else {
                emptyList()
            },
        )
    }

    private fun routeUnmapped(
        state: MidiRouterState,
        message: MidiMessage,
        destination: MidiDestinationId,
    ): RouterTransition {
        return RouterTransition(
            state = state,
            effects = if (passesUnmapped(state.mode)) {
                listOf(RouterEffect.Midi(destination, message))
            } else {
                emptyList()
            },
        )
    }

    private fun addLease(
        state: MidiRouterState,
        key: IncomingNoteKey,
        lease: RoutingLease,
    ): MidiRouterState {
        val existing = state.leases[key].orEmpty()
        val last = existing.lastOrNull()
        val updated = if (
            last != null &&
            last.route == lease.route &&
            last.source == lease.source &&
            last.modeAtNoteOn == lease.modeAtNoteOn &&
            last.destination == lease.destination &&
            last.outputChannel == lease.outputChannel
        ) {
            existing.dropLast(1) + last.copy(instanceCount = last.instanceCount + 1)
        } else {
            existing + lease
        }
        return state.copy(leases = state.leases + (key to updated))
    }

    private fun removeMappedLeases(
        state: MidiRouterState,
        key: IncomingNoteKey,
        timestampNanos: Long,
    ): RemovedMappedLeases {
        val existing = state.leases[key].orEmpty()
        val removed = existing.filter { it.route == LeaseRoute.MAPPED }
        val retained = existing.filterNot { it.route == LeaseRoute.MAPPED }
        val nextLeases = when {
            retained.isEmpty() -> state.leases - key
            else -> state.leases + (key to retained)
        }
        val effects = removed.flatMap { lease ->
            List(lease.instanceCount) {
                RouterEffect.Instrument(
                    lease.destination,
                    InstrumentAction.Release(lease.source, 0, timestampNanos),
                )
            }
        }
        return RemovedMappedLeases(state.copy(leases = nextLeases), effects)
    }

    private fun purge(
        state: MidiRouterState,
        timestampNanos: Long,
        noteMatches: (IncomingNoteKey, RoutingLease) -> Boolean,
        ccMatches: (IncomingCcKey, CcGateLease) -> Boolean,
    ): PurgeResult {
        val releaseEffects = mutableListOf<RouterEffect>()
        val channels = linkedSetOf<Pair<MidiDestinationId, Int>>()
        val nextNoteLeases = linkedMapOf<IncomingNoteKey, List<RoutingLease>>()

        state.leases.entries
            .sortedWith(
                compareBy<Map.Entry<IncomingNoteKey, List<RoutingLease>>>(
                    { it.key.deviceId },
                    { it.key.portNumber },
                    { it.key.channel },
                    { it.key.note },
                ),
            )
            .forEach { (key, leasesForKey) ->
                val retained = mutableListOf<RoutingLease>()
                leasesForKey.forEach { lease ->
                    if (!noteMatches(key, lease)) {
                        retained += lease
                    } else {
                        when (lease.route) {
                            LeaseRoute.MAPPED -> {
                                repeat(lease.instanceCount) {
                                    releaseEffects += RouterEffect.Instrument(
                                        lease.destination,
                                        InstrumentAction.Release(lease.source, 0, timestampNanos),
                                    )
                                }
                                channels += lease.destination to lease.outputChannel
                            }
                            LeaseRoute.FORWARDED -> {
                                repeat(lease.instanceCount) {
                                    releaseEffects += RouterEffect.Midi(
                                        lease.destination,
                                        MidiMessage.NoteOff(
                                            lease.outputChannel,
                                            lease.source.note,
                                            0,
                                            timestampNanos,
                                        ),
                                    )
                                }
                                channels += lease.destination to lease.outputChannel
                            }
                            LeaseRoute.DROPPED -> Unit
                        }
                    }
                }
                if (retained.isNotEmpty()) nextNoteLeases[key] = retained
            }

        val nextCcGates = linkedMapOf<IncomingCcKey, CcGateLease>()
        state.ccGates.entries
            .sortedWith(
                compareBy<Map.Entry<IncomingCcKey, CcGateLease>>(
                    { it.key.deviceId },
                    { it.key.portNumber },
                    { it.key.channel },
                    { it.key.controller },
                ),
            )
            .forEach { (key, gate) ->
                if (!ccMatches(key, gate)) {
                    nextCcGates[key] = gate
                } else if (gate.action.holdsGeneratedNotes()) {
                    releaseEffects += RouterEffect.Instrument(
                        gate.destination,
                        InstrumentAction.Release(gate.source, 0, timestampNanos),
                    )
                    channels += gate.destination to gate.outputChannel
                }
            }

        val controlEffects = channels
            .sortedWith(compareBy<Pair<MidiDestinationId, Int>>({ it.first.value }, { it.second }))
            .flatMap { (destination, channel) ->
                listOf(
                    RouterEffect.Midi(
                        destination,
                        MidiMessage.ControlChange(channel, ALL_NOTES_OFF, 0, timestampNanos),
                    ),
                    RouterEffect.Midi(
                        destination,
                        MidiMessage.ControlChange(channel, ALL_SOUND_OFF, 0, timestampNanos),
                    ),
                )
            }

        return PurgeResult(
            state = state.copy(leases = nextNoteLeases, ccGates = nextCcGates),
            releaseEffects = releaseEffects,
            controlEffects = controlEffects,
        )
    }

    private fun acceptsChannel(state: MidiRouterState, channel: Int): Boolean {
        return state.inputChannel == null || state.inputChannel == channel
    }

    private fun passesUnmapped(mode: PassThroughMode): Boolean {
        return mode == PassThroughMode.ACTIVE ||
            mode == PassThroughMode.ACTIVE_LAST_NOTE ||
            mode == PassThroughMode.PASS_THRU
    }

    private data class RemovedMappedLeases(
        val state: MidiRouterState,
        val effects: List<RouterEffect>,
    )

    private data class PurgeResult(
        val state: MidiRouterState,
        val releaseEffects: List<RouterEffect>,
        val controlEffects: List<RouterEffect>,
    ) {
        fun toTransition(): RouterTransition = RouterTransition(
            state = state,
            effects = releaseEffects + controlEffects,
        )
    }

    private companion object {
        const val DEFAULT_CC_VELOCITY: Int = 64
        const val SYSTEM_RESET: Int = 0xFF
        const val ALL_NOTES_OFF: Int = 123
        const val ALL_SOUND_OFF: Int = 120
    }
}
