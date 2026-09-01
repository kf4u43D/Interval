package dev.intervaltablet.domain

const val MIDI_MAPPING_DEFAULT_CC_THRESHOLD: Int = 64

enum class MidiLearnChannelMode {
    RECEIVED,
    OMNI,
}

/** A complete, UI-independent view of one binding, including its effective CC threshold. */
data class MidiBindingAssignment(
    val key: MidiBindingKey,
    val action: MidiAction,
    val ccThreshold: Int? = null,
) {
    init {
        when (key.kind) {
            MidiBindingKey.Kind.NOTE -> require(ccThreshold == null) {
                "A note binding cannot define a CC threshold"
            }
            MidiBindingKey.Kind.CC -> require(
                ccThreshold != null && ccThreshold in 1..127,
            ) {
                "A CC binding requires a threshold in 1..127"
            }
        }
    }
}

data class MidiMappingConflict(
    val existing: MidiBindingAssignment,
)

/**
 * Exact and Omni bindings deliberately coexist. This metadata lets presentation code explain
 * which fallback is shadowed without treating the overlap as a destructive conflict.
 */
data class MidiMappingOverlap(
    val omniFallback: MidiBindingAssignment? = null,
    val exactOverrides: List<MidiBindingAssignment> = emptyList(),
)

data class MidiMappingCandidate(
    val binding: MidiBindingAssignment,
    val receivedChannel: Int,
    val conflict: MidiMappingConflict? = null,
    val overlap: MidiMappingOverlap = MidiMappingOverlap(),
) {
    init {
        require(receivedChannel in 0..15)
        require(binding.key.channel == null || binding.key.channel == receivedChannel) {
            "A learned binding can target only the received channel or Omni"
        }
    }

    val channelMode: MidiLearnChannelMode
        get() = if (binding.key.channel == null) {
            MidiLearnChannelMode.OMNI
        } else {
            MidiLearnChannelMode.RECEIVED
        }
}

sealed interface MidiMappingCapture {
    data object Idle : MidiMappingCapture

    data class Armed(val action: MidiAction) : MidiMappingCapture

    data class Captured(val candidate: MidiMappingCandidate) : MidiMappingCapture
}

sealed interface MidiMappingEditorState {
    data object Closed : MidiMappingEditorState

    data class Editing(
        val baseline: MidiMapping,
        val draft: MidiMapping,
        val capture: MidiMappingCapture = MidiMappingCapture.Idle,
    ) : MidiMappingEditorState {
        val hasChanges: Boolean get() = draft != baseline
    }
}

sealed interface MidiMappingEditorAction {
    data class Open(val mapping: MidiMapping) : MidiMappingEditorAction

    data class Arm(val action: MidiAction) : MidiMappingEditorAction

    /** Parsed domain messages only; timestamps are deliberately not retained by the editor. */
    data class Receive(val message: MidiMessage) : MidiMappingEditorAction

    data class SetCandidateChannelMode(val mode: MidiLearnChannelMode) : MidiMappingEditorAction

    data class SetCandidateThreshold(val threshold: Int) : MidiMappingEditorAction {
        init {
            require(threshold in 1..127)
        }
    }

    data class SetCandidateAction(val action: MidiAction) : MidiMappingEditorAction

    data object AddCandidate : MidiMappingEditorAction

    data object ReplaceCandidate : MidiMappingEditorAction

    data class DeleteBinding(val key: MidiBindingKey) : MidiMappingEditorAction

    data object ResetDraft : MidiMappingEditorAction

    /**
     * [currentMapping] is supplied by the owner at the serialized commit boundary. It prevents
     * an editor opened from an old preset/session from overwriting a newer mapping silently.
     */
    data class Save(val currentMapping: MidiMapping) : MidiMappingEditorAction

    data object CancelCapture : MidiMappingEditorAction

    data object Cancel : MidiMappingEditorAction
}

enum class MidiMappingSaveRejection {
    UNRESOLVED_CAPTURE,
    STALE_BASELINE,
}

sealed interface MidiMappingEditorEvent {
    /** The app adapter should silence existing leases before accepting learned input. */
    data object PanicRequested : MidiMappingEditorEvent

    data class CandidateCaptured(val candidate: MidiMappingCandidate) : MidiMappingEditorEvent

    data class ReplacementRequired(val conflict: MidiMappingConflict) : MidiMappingEditorEvent

    data object DraftChanged : MidiMappingEditorEvent

    data class CommitRequested(
        val expectedBaseline: MidiMapping,
        val replacement: MidiMapping,
    ) : MidiMappingEditorEvent

    data class SaveRejected(val reason: MidiMappingSaveRejection) : MidiMappingEditorEvent

    data object Cancelled : MidiMappingEditorEvent
}

data class MidiMappingEditorTransition(
    val state: MidiMappingEditorState,
    val events: List<MidiMappingEditorEvent> = emptyList(),
    /** True means the app must not route, forward or interpret this input as a preset recall. */
    val inputConsumed: Boolean = false,
)

/** Pure transactional editor. Android, persistence and presentation consume only its events. */
class MidiMappingEditorReducer {
    fun initialState(): MidiMappingEditorState = MidiMappingEditorState.Closed

    fun reduce(
        state: MidiMappingEditorState,
        action: MidiMappingEditorAction,
    ): MidiMappingEditorTransition {
        return when (state) {
            MidiMappingEditorState.Closed -> reduceClosed(action)
            is MidiMappingEditorState.Editing -> reduceEditing(state, action)
        }
    }

    private fun reduceClosed(
        action: MidiMappingEditorAction,
    ): MidiMappingEditorTransition {
        return when (action) {
            is MidiMappingEditorAction.Open -> open(action.mapping)
            is MidiMappingEditorAction.Arm,
            is MidiMappingEditorAction.Receive,
            is MidiMappingEditorAction.SetCandidateChannelMode,
            is MidiMappingEditorAction.SetCandidateThreshold,
            is MidiMappingEditorAction.SetCandidateAction,
            MidiMappingEditorAction.AddCandidate,
            MidiMappingEditorAction.ReplaceCandidate,
            is MidiMappingEditorAction.DeleteBinding,
            MidiMappingEditorAction.ResetDraft,
            is MidiMappingEditorAction.Save,
            MidiMappingEditorAction.CancelCapture,
            MidiMappingEditorAction.Cancel,
            -> MidiMappingEditorTransition(MidiMappingEditorState.Closed)
        }
    }

    private fun reduceEditing(
        state: MidiMappingEditorState.Editing,
        action: MidiMappingEditorAction,
    ): MidiMappingEditorTransition {
        return when (action) {
            is MidiMappingEditorAction.Open -> open(action.mapping)
            is MidiMappingEditorAction.Arm -> MidiMappingEditorTransition(
                state = state.copy(capture = MidiMappingCapture.Armed(action.action)),
                events = listOf(MidiMappingEditorEvent.PanicRequested),
            )
            is MidiMappingEditorAction.Receive -> receive(state, action.message)
            is MidiMappingEditorAction.SetCandidateChannelMode -> setCandidateChannelMode(
                state,
                action.mode,
            )
            is MidiMappingEditorAction.SetCandidateThreshold -> setCandidateThreshold(
                state,
                action.threshold,
            )
            is MidiMappingEditorAction.SetCandidateAction -> setCandidateAction(
                state,
                action.action,
            )
            MidiMappingEditorAction.AddCandidate -> acceptCandidate(state, replace = false)
            MidiMappingEditorAction.ReplaceCandidate -> acceptCandidate(state, replace = true)
            is MidiMappingEditorAction.DeleteBinding -> deleteBinding(state, action.key)
            MidiMappingEditorAction.ResetDraft -> MidiMappingEditorTransition(
                state = state.copy(
                    draft = snapshot(DefaultMidiMap.mapping),
                    capture = MidiMappingCapture.Idle,
                ),
                events = listOf(MidiMappingEditorEvent.DraftChanged),
            )
            is MidiMappingEditorAction.Save -> save(state, action.currentMapping)
            MidiMappingEditorAction.CancelCapture -> MidiMappingEditorTransition(
                state.copy(capture = MidiMappingCapture.Idle),
            )
            MidiMappingEditorAction.Cancel -> MidiMappingEditorTransition(
                state = MidiMappingEditorState.Closed,
                events = listOf(MidiMappingEditorEvent.Cancelled),
            )
        }
    }

    private fun open(mapping: MidiMapping): MidiMappingEditorTransition {
        return MidiMappingEditorTransition(
            MidiMappingEditorState.Editing(
                baseline = snapshot(mapping),
                draft = snapshot(mapping),
            ),
        )
    }

    private fun receive(
        state: MidiMappingEditorState.Editing,
        message: MidiMessage,
    ): MidiMappingEditorTransition {
        return when (val capture = state.capture) {
            MidiMappingCapture.Idle -> MidiMappingEditorTransition(state)
            is MidiMappingCapture.Armed -> receiveWhileArmed(state, capture, message)
            is MidiMappingCapture.Captured -> MidiMappingEditorTransition(
                state = state,
                // Keep the first candidate stable and shield all Note/CC traffic until the
                // musician accepts it or explicitly cancels the capture.
                inputConsumed = message.isLearnableChannelInput(),
            )
        }
    }

    private fun receiveWhileArmed(
        state: MidiMappingEditorState.Editing,
        capture: MidiMappingCapture.Armed,
        message: MidiMessage,
    ): MidiMappingEditorTransition {
        val assignment = when (message) {
            is MidiMessage.NoteOn -> MidiBindingAssignment(
                key = MidiBindingKey(
                    kind = MidiBindingKey.Kind.NOTE,
                    number = message.note,
                    channel = message.channel,
                ),
                action = capture.action,
            )
            is MidiMessage.ControlChange -> MidiBindingAssignment(
                key = MidiBindingKey(
                    kind = MidiBindingKey.Kind.CC,
                    number = message.controller,
                    channel = message.channel,
                ),
                action = capture.action,
                ccThreshold = MIDI_MAPPING_DEFAULT_CC_THRESHOLD,
            )
            is MidiMessage.NoteOff -> return MidiMappingEditorTransition(
                state = state,
                inputConsumed = true,
            )
            is MidiMessage.ProgramChange,
            is MidiMessage.SongSelect,
            is MidiMessage.PitchBend,
            is MidiMessage.ChannelPressure,
            is MidiMessage.PolyPressure,
            is MidiMessage.RealTime,
            is MidiMessage.Raw,
            -> return MidiMappingEditorTransition(state)
        }
        val candidate = analyzeCandidate(
            mapping = state.draft,
            assignment = assignment,
            receivedChannel = requireNotNull(assignment.key.channel),
        )
        return MidiMappingEditorTransition(
            state = state.copy(capture = MidiMappingCapture.Captured(candidate)),
            events = listOf(MidiMappingEditorEvent.CandidateCaptured(candidate)),
            inputConsumed = true,
        )
    }

    private fun setCandidateChannelMode(
        state: MidiMappingEditorState.Editing,
        mode: MidiLearnChannelMode,
    ): MidiMappingEditorTransition {
        val captured = state.capture as? MidiMappingCapture.Captured
            ?: return MidiMappingEditorTransition(state)
        val current = captured.candidate
        val key = current.binding.key.copy(
            channel = when (mode) {
                MidiLearnChannelMode.RECEIVED -> current.receivedChannel
                MidiLearnChannelMode.OMNI -> null
            },
        )
        return state.withCandidate(current.binding.copy(key = key), current.receivedChannel)
    }

    private fun setCandidateThreshold(
        state: MidiMappingEditorState.Editing,
        threshold: Int,
    ): MidiMappingEditorTransition {
        val captured = state.capture as? MidiMappingCapture.Captured
            ?: return MidiMappingEditorTransition(state)
        val current = captured.candidate
        if (current.binding.key.kind != MidiBindingKey.Kind.CC) {
            return MidiMappingEditorTransition(state)
        }
        return state.withCandidate(
            current.binding.copy(ccThreshold = threshold),
            current.receivedChannel,
        )
    }

    private fun setCandidateAction(
        state: MidiMappingEditorState.Editing,
        action: MidiAction,
    ): MidiMappingEditorTransition {
        val captured = state.capture as? MidiMappingCapture.Captured
            ?: return MidiMappingEditorTransition(state)
        val current = captured.candidate
        return state.withCandidate(
            current.binding.copy(action = action),
            current.receivedChannel,
        )
    }

    private fun acceptCandidate(
        state: MidiMappingEditorState.Editing,
        replace: Boolean,
    ): MidiMappingEditorTransition {
        val captured = state.capture as? MidiMappingCapture.Captured
            ?: return MidiMappingEditorTransition(state)
        val candidate = captured.candidate
        if (!replace && candidate.conflict != null) {
            return MidiMappingEditorTransition(
                state = state,
                events = listOf(
                    MidiMappingEditorEvent.ReplacementRequired(candidate.conflict),
                ),
            )
        }
        return MidiMappingEditorTransition(
            state = state.copy(
                draft = state.draft.upsert(candidate.binding),
                capture = MidiMappingCapture.Idle,
            ),
            events = listOf(MidiMappingEditorEvent.DraftChanged),
        )
    }

    private fun deleteBinding(
        state: MidiMappingEditorState.Editing,
        key: MidiBindingKey,
    ): MidiMappingEditorTransition {
        if (key !in state.draft.bindings) return MidiMappingEditorTransition(state)
        val nextDraft = MidiMapping(
            bindings = state.draft.bindings - key,
            ccThresholds = state.draft.ccThresholds - key,
        )
        return MidiMappingEditorTransition(
            state = state.withDraft(nextDraft),
            events = listOf(MidiMappingEditorEvent.DraftChanged),
        )
    }

    private fun save(
        state: MidiMappingEditorState.Editing,
        currentMapping: MidiMapping,
    ): MidiMappingEditorTransition {
        if (state.capture != MidiMappingCapture.Idle) {
            return MidiMappingEditorTransition(
                state = state,
                events = listOf(
                    MidiMappingEditorEvent.SaveRejected(
                        MidiMappingSaveRejection.UNRESOLVED_CAPTURE,
                    ),
                ),
            )
        }
        if (state.baseline != currentMapping) {
            return MidiMappingEditorTransition(
                state = state,
                events = listOf(
                    MidiMappingEditorEvent.SaveRejected(
                        MidiMappingSaveRejection.STALE_BASELINE,
                    ),
                ),
            )
        }
        return MidiMappingEditorTransition(
            state = MidiMappingEditorState.Closed,
            events = listOf(
                MidiMappingEditorEvent.CommitRequested(
                    expectedBaseline = snapshot(state.baseline),
                    replacement = snapshot(state.draft),
                ),
            ),
        )
    }

    private fun MidiMappingEditorState.Editing.withCandidate(
        assignment: MidiBindingAssignment,
        receivedChannel: Int,
    ): MidiMappingEditorTransition {
        val candidate = analyzeCandidate(draft, assignment, receivedChannel)
        return MidiMappingEditorTransition(
            state = copy(capture = MidiMappingCapture.Captured(candidate)),
        )
    }

    private fun MidiMappingEditorState.Editing.withDraft(
        replacement: MidiMapping,
    ): MidiMappingEditorState.Editing {
        val nextCapture = when (val current = capture) {
            MidiMappingCapture.Idle -> current
            is MidiMappingCapture.Armed -> current
            is MidiMappingCapture.Captured -> MidiMappingCapture.Captured(
                analyzeCandidate(
                    mapping = replacement,
                    assignment = current.candidate.binding,
                    receivedChannel = current.candidate.receivedChannel,
                ),
            )
        }
        return copy(draft = replacement, capture = nextCapture)
    }

    private fun analyzeCandidate(
        mapping: MidiMapping,
        assignment: MidiBindingAssignment,
        receivedChannel: Int,
    ): MidiMappingCandidate {
        val key = assignment.key
        val conflict = mapping.assignment(key)?.let(::MidiMappingConflict)
        val overlap = if (key.channel == null) {
            MidiMappingOverlap(
                exactOverrides = mapping.bindings.keys
                    .asSequence()
                    .filter { other ->
                        other.kind == key.kind &&
                            other.number == key.number &&
                            other.channel != null
                    }
                    .sortedBy { requireNotNull(it.channel) }
                    .mapNotNull { candidateKey -> mapping.assignment(candidateKey) }
                    .toList(),
            )
        } else {
            val omniKey = MidiBindingKey(key.kind, key.number, channel = null)
            MidiMappingOverlap(omniFallback = mapping.assignment(omniKey))
        }
        return MidiMappingCandidate(
            binding = assignment,
            receivedChannel = receivedChannel,
            conflict = conflict,
            overlap = overlap,
        )
    }

    private fun MidiMapping.assignment(key: MidiBindingKey): MidiBindingAssignment? {
        val action = bindings[key] ?: return null
        return MidiBindingAssignment(
            key = key,
            action = action,
            ccThreshold = if (key.kind == MidiBindingKey.Kind.CC) {
                ccThresholds[key] ?: MIDI_MAPPING_DEFAULT_CC_THRESHOLD
            } else {
                null
            },
        )
    }

    private fun MidiMapping.upsert(assignment: MidiBindingAssignment): MidiMapping {
        val key = assignment.key
        val thresholds = (ccThresholds - key).toMutableMap()
        if (
            key.kind == MidiBindingKey.Kind.CC &&
            assignment.ccThreshold != MIDI_MAPPING_DEFAULT_CC_THRESHOLD
        ) {
            thresholds[key] = requireNotNull(assignment.ccThreshold)
        }
        return MidiMapping(
            bindings = bindings + (key to assignment.action),
            ccThresholds = thresholds,
        )
    }

    private fun snapshot(mapping: MidiMapping): MidiMapping {
        return MidiMapping(
            bindings = mapping.bindings.toMap(),
            ccThresholds = mapping.ccThresholds.toMap(),
        )
    }

    private fun MidiMessage.isLearnableChannelInput(): Boolean {
        return this is MidiMessage.NoteOn ||
            this is MidiMessage.NoteOff ||
            this is MidiMessage.ControlChange
    }
}
