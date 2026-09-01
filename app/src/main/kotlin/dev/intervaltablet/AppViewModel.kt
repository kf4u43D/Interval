package dev.intervaltablet

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.intervaltablet.audio.AudioDiagnostics
import dev.intervaltablet.audio.AudioMonitor
import dev.intervaltablet.audio.NativeAudioEngine
import dev.intervaltablet.data.MidiMappingSerializer
import dev.intervaltablet.data.PRESET_SLOT_COUNT
import dev.intervaltablet.data.PerformancePresetSnapshot
import dev.intervaltablet.data.PresetBank
import dev.intervaltablet.data.PresetMidiPolicy
import dev.intervaltablet.data.PresetRecallDecision
import dev.intervaltablet.data.RoutingSnapshot
import dev.intervaltablet.data.SettingsRepository
import dev.intervaltablet.data.SettingsStore
import dev.intervaltablet.data.StoredSettings
import dev.intervaltablet.data.MusicalContextSnapshot
import dev.intervaltablet.data.toPersistenceSnapshot
import dev.intervaltablet.data.toStoppedDomainState
import dev.intervaltablet.domain.AudioCommand
import dev.intervaltablet.domain.ChordDefinition
import dev.intervaltablet.domain.ChordLibrary
import dev.intervaltablet.domain.ClockSource
import dev.intervaltablet.domain.DefaultMidiMap
import dev.intervaltablet.domain.InstrumentAction
import dev.intervaltablet.domain.InstrumentConfig
import dev.intervaltablet.domain.MidiDestinationId
import dev.intervaltablet.domain.MidiMessage
import dev.intervaltablet.domain.MidiNoteRange
import dev.intervaltablet.domain.MidiRouterState
import dev.intervaltablet.domain.PadArticulation
import dev.intervaltablet.domain.PassThroughMode
import dev.intervaltablet.domain.PitchMoveResult
import dev.intervaltablet.domain.ScaleDefinition
import dev.intervaltablet.domain.ScaleLibrary
import dev.intervaltablet.domain.SynthPatch
import dev.intervaltablet.domain.ToneRowAction
import dev.intervaltablet.domain.ToneRowMode
import dev.intervaltablet.domain.ToneRowPlayMode
import dev.intervaltablet.domain.TransportAction
import dev.intervaltablet.domain.TransportMode
import dev.intervaltablet.domain.TriggerSource
import dev.intervaltablet.domain.midiNoteName
import dev.intervaltablet.midi.AndroidMidiRepository
import dev.intervaltablet.midi.MidiConnectionState
import dev.intervaltablet.midi.MidiConnectionParser
import dev.intervaltablet.midi.MidiConnectionPhase
import dev.intervaltablet.midi.MidiInputPacket
import dev.intervaltablet.midi.MidiPacketSink
import dev.intervaltablet.midi.MidiPortDescriptor
import dev.intervaltablet.midi.MidiPortDirection
import dev.intervaltablet.midi.MidiPortRepository
import dev.intervaltablet.midi.MidiRepositoryEvent
import dev.intervaltablet.midi.MidiRepositoryState
import dev.intervaltablet.ui.ToneRowUiClockSource
import dev.intervaltablet.ui.ToneRowUiIntent
import dev.intervaltablet.ui.ToneRowUiPlaybackMode
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class AppUiState(
    val performance: PerformanceCoordinatorState = PerformanceCoordinatorState.initial(),
    val midi: MidiRepositoryState = MidiRepositoryState(),
    val audioMonitorEnabled: Boolean = true,
    val audioAvailable: Boolean = false,
    val audioRunning: Boolean = false,
    val audioDiagnostics: AudioDiagnostics = AudioDiagnostics(),
    val synthPatch: SynthPatch = SynthPatch(),
    val performanceLock: Boolean = false,
    val settingsLoaded: Boolean = false,
    val hostStarted: Boolean = false,
    val statusMessage: String? = null,
    val droppedCoordinatorCommands: Long = 0,
    val presetBank: PresetBank = PresetBank(),
    val selectedPresetSlot: Int = 0,
) {
    val instrument get() = performance.instrument
    val passThroughMode: PassThroughMode get() = performance.router.mode
    val inputChannel: Int? get() = performance.router.inputChannel
    val outputChannel: Int get() = instrument.config.outputChannel
    val currentNoteName: String get() = midiNoteName(instrument.currentNote)
    val currentDegree: Int? get() = instrument.config.grid().degreeIndexOf(instrument.currentNote)?.plus(1)
    val scaleName: String get() = instrument.config.scale.displayName
    val chordName: String get() = instrument.config.chord.displayName
    val rootName: String get() = midiNoteName(instrument.config.rootPitchClass + 60).dropLast(1)
    val activeStepCounts: Map<Int, Int> get() = performance.activeStepCounts
    val mappingCustomized: Boolean get() = performance.mapping != DefaultMidiMap.mapping

    fun targetPreview(steps: Int): PitchMoveResult {
        val anchor = navigationAnchor(instrument.currentNote, instrument.lastExternalNote, steps)
        return instrument.config.grid().previewMove(anchor, steps)
    }

    fun targetName(steps: Int): String = midiNoteName(targetPreview(steps).note)
}

private data class PersistenceSource(
    val performance: PerformanceCoordinatorState,
    val audioMonitorEnabled: Boolean,
    val synthPatch: SynthPatch,
    val performanceLock: Boolean,
    val preferredSourceIdentity: String?,
    val preferredDestinationIdentity: String?,
    val presetBank: PresetBank,
    val selectedPresetSlot: Int,
)

private data class PersistenceRequest(
    val version: Long,
    val source: PersistenceSource,
)

class IntervalTabletViewModel @JvmOverloads constructor(
    application: Application,
    private val clock: MonotonicClock = SystemMonotonicClock,
    private val audioEngine: AudioMonitor = NativeAudioEngine(),
    midiRepositoryFactory: ((MidiPacketSink) -> MidiPortRepository)? = null,
    settingsStoreFactory: ((Application) -> SettingsStore)? = null,
    private val actorDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
    private val persistenceDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val diagnosticsDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AndroidViewModel(application) {
    private val coordinator = PerformanceCoordinator()
    private var performanceState = PerformanceCoordinatorState.initial()
    private var midiState = MidiRepositoryState()
    private val connectionParser = MidiConnectionParser()
    private val lastMidiTimestampByDestination = mutableMapOf<MidiDestinationId, Long>()

    private var audioMonitorEnabled: Boolean = true
    private var audioRunning: Boolean = false
    private var audioDiagnostics: AudioDiagnostics = AudioDiagnostics()
    private var synthPatch: SynthPatch = SynthPatch()
    private var previewedSynthPatch: SynthPatch? = null
    private var performanceLock: Boolean = false
    private var settingsLoaded: Boolean = false
    private var hostStarted: Boolean = false
    private var statusMessage: String? = null
    private var preferredSourceIdentity: String? = null
    private var preferredDestinationIdentity: String? = null
    private var pendingSourceSessionId: String? = null
    private var pendingDestinationSessionId: String? = null
    private var presetBank: PresetBank = PresetBank()
    private var selectedPresetSlot: Int = 0
    private var internalClockJob: Job? = null
    private var toneRowReleaseJob: Job? = null
    private var internalClockGeneration: Long = 0L
    private var scheduledInternalTickNanos: Long? = null
    private var persistenceDirty: Boolean = false
    private var persistenceVersion: Long = 0L
    private var persistenceErrorVersion: Long? = null
    private var persistenceErrorStatus: String? = null
    private var audioLifecycleGeneration: Long = 0L
    private var lastDiagnosticsSequence: Long = 0L

    private val droppedCommands = AtomicLong(0)
    private val overflowRecoveryQueued = AtomicBoolean(false)
    private val diagnosticsInFlight = AtomicBoolean(false)
    private val diagnosticsSequence = AtomicLong(0L)
    private val closing = AtomicBoolean(false)
    private val oneShotSequence = AtomicLong(0)
    private val actorGuard = Any()
    private val mailbox = Channel<MailboxCommand>(capacity = MAILBOX_CAPACITY)
    private val persistence = Channel<PersistenceRequest>(
        capacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val persistenceSupervisor = SupervisorJob()
    private val persistenceScope = CoroutineScope(persistenceSupervisor + persistenceDispatcher)
    private val persistenceWorker: Job

    private val mutableUiState = MutableStateFlow(AppUiState(audioAvailable = audioEngine.isAvailable))
    val uiState: StateFlow<AppUiState> = mutableUiState.asStateFlow()

    private val settingsRepository: SettingsStore =
        settingsStoreFactory?.invoke(application) ?: SettingsRepository(application)
    private val midiRepository: MidiPortRepository =
        midiRepositoryFactory?.invoke(MidiPacketSink(::offerPacket))
            ?: AndroidMidiRepository(
                context = application,
                packetSink = MidiPacketSink(::offerPacket),
            )

    init {
        viewModelScope.launch(actorDispatcher) {
            for (command in mailbox) {
                synchronized(actorGuard) {
                    if (!closing.get()) {
                        handle(command)
                        if (performanceState.toneRowAutoSource == null) {
                            toneRowReleaseJob?.cancel()
                            toneRowReleaseJob = null
                        }
                        flushPersistenceIfDirty()
                        reconcileInternalClock()
                        publish()
                    }
                }
            }
        }
        persistenceWorker = persistenceScope.launch {
            try {
                runPersistenceWorker()
            } finally {
                persistenceSupervisor.complete()
            }
        }
        viewModelScope.launch(actorDispatcher) {
            midiRepository.state
                .distinctUntilChangedBy(MidiRepositoryState::operationalKey)
                .collect { state -> mailbox.send(MailboxCommand.MidiStateChanged(state)) }
        }
        viewModelScope.launch(actorDispatcher) {
            midiRepository.events.collect { event -> mailbox.send(MailboxCommand.MidiEventReceived(event)) }
        }
        viewModelScope.launch(persistenceDispatcher) {
            val stored = runCatching { settingsRepository.settings.first() }.getOrElse { StoredSettings() }
            mailbox.send(MailboxCommand.ApplyStoredSettings(stored))
        }
        viewModelScope.launch(actorDispatcher) {
            while (isActive) {
                delay(DIAGNOSTICS_PERIOD_MILLIS)
                requestDiagnosticsSample()
            }
        }
    }

    fun pressInterval(pointerId: Long, steps: Int) {
        require(steps in -4..4)
        enqueueControl(
            MailboxCommand.TouchPressed(
                source = TriggerSource.Touch(pointerId),
                steps = steps,
                timestampNanos = clock.nowNanos(),
            ),
        )
    }

    fun releaseInterval(pointerId: Long) {
        enqueueControl(
            MailboxCommand.Reduce(
                listOf(
                    PerformanceCommand.Instrument(
                        InstrumentAction.Release(
                            source = TriggerSource.Touch(pointerId),
                            timestampNanos = clock.nowNanos(),
                        ),
                    ),
                ),
            ),
        )
    }

    fun triggerInterval(steps: Int) {
        require(steps in -4..4)
        val source = TriggerSource.System("ui:interval:$steps:${oneShotSequence.incrementAndGet()}")
        enqueueControl(
            MailboxCommand.OneShotPressed(
                source = source,
                intent = OneShotIntent.Interval(steps),
                timestampNanos = clock.nowNanos(),
            ),
        )
    }

    fun undo() = triggerOneShot(OneShotIntent.Undo)

    fun home() = triggerOneShot(OneShotIntent.Home)

    fun panic() {
        enqueueControl(MailboxCommand.Reduce(listOf(PerformanceCommand.Panic(clock.nowNanos()))))
    }

    fun setScale(scale: ScaleDefinition) {
        reduceAndPersist(InstrumentAction.SetScale(scale, clock.nowNanos()))
    }

    fun setRoot(rootPitchClass: Int) {
        reduceAndPersist(InstrumentAction.SetRoot(rootPitchClass, clock.nowNanos()))
    }

    fun setChord(chord: ChordDefinition) {
        reduceAndPersist(InstrumentAction.SetChord(chord, clock.nowNanos()))
    }

    fun setPadArticulation(mode: PadArticulation) {
        reduceAndPersist(InstrumentAction.SetPadArticulation(mode, clock.nowNanos()))
    }

    fun strumTone(index: Int, velocity: Int) {
        if (index < 0 || velocity !in 1..127) return
        val source = TriggerSource.System("ui:strum:${oneShotSequence.incrementAndGet()}")
        enqueueControl(
            MailboxCommand.OneShotPressed(
                source = source,
                intent = OneShotIntent.StrumTone(index, velocity),
                timestampNanos = clock.nowNanos(),
            ),
        )
    }

    fun setRange(range: MidiNoteRange) {
        reduceAndPersist(InstrumentAction.SetRange(range, clock.nowNanos()))
    }

    fun setWrap(enabled: Boolean) {
        reduceAndPersist(InstrumentAction.SetWrap(enabled, clock.nowNanos()))
    }

    fun setOutputChannel(channel: Int) {
        reduceAndPersist(InstrumentAction.SetOutputChannel(channel, clock.nowNanos()))
    }

    fun setInputChannel(channel: Int?) {
        enqueueControl(
            MailboxCommand.Reduce(
                commands = listOf(PerformanceCommand.SetInputChannel(channel)),
                persistAfter = true,
            ),
        )
    }

    fun setPassThroughMode(mode: PassThroughMode) {
        enqueueControl(
            MailboxCommand.Reduce(
                commands = listOf(PerformanceCommand.SetPassThroughMode(mode)),
                persistAfter = true,
            ),
        )
    }

    fun resetMidiMapping() {
        enqueueControl(
            MailboxCommand.Reduce(
                commands = listOf(PerformanceCommand.SetMapping(DefaultMidiMap.mapping)),
                persistAfter = true,
                completionStatus = "Mapping MIDI réinitialisé",
            ),
        )
    }

    fun selectSource(descriptor: MidiPortDescriptor?) {
        enqueueControl(MailboxCommand.SelectSource(descriptor, rememberChoice = true))
    }

    fun selectDestination(descriptor: MidiPortDescriptor?) {
        enqueueControl(MailboxCommand.SelectDestination(descriptor, rememberChoice = true))
    }

    fun toggleAudioMonitor() {
        enqueueControl(MailboxCommand.ToggleAudioEnabled)
    }

    fun setSynthPatch(patch: SynthPatch) {
        enqueueControl(MailboxCommand.SetSynthPatch(patch))
    }

    fun previewSynthPatch(patch: SynthPatch) {
        enqueueControl(MailboxCommand.PreviewSynthPatch(patch))
    }

    fun togglePerformanceLock() {
        enqueueControl(MailboxCommand.TogglePerformanceLock)
    }

    fun dismissStatus() {
        enqueueControl(MailboxCommand.DismissStatus)
    }

    fun onToneRowIntent(intent: ToneRowUiIntent) {
        enqueueControl(MailboxCommand.ToneRowIntentReceived(intent))
    }

    fun onHostStart() {
        enqueueControl(MailboxCommand.HostStarted)
    }

    fun onHostStop() {
        enqueueControl(MailboxCommand.HostStopped)
    }

    private fun triggerOneShot(intent: OneShotIntent) {
        val source = TriggerSource.System("ui:one-shot:${oneShotSequence.incrementAndGet()}")
        enqueueControl(
            MailboxCommand.OneShotPressed(
                source = source,
                intent = intent,
                timestampNanos = clock.nowNanos(),
            ),
        )
    }

    private fun scheduleOneShotRelease(source: TriggerSource.System, durationMillis: Long) {
        viewModelScope.launch(actorDispatcher) {
            delay(durationMillis)
            enqueueControl(
                MailboxCommand.Reduce(
                    listOf(
                        PerformanceCommand.Instrument(
                            InstrumentAction.Release(source, timestampNanos = clock.nowNanos()),
                        ),
                    ),
                ),
            )
        }
    }

    private fun reduceAndPersist(action: InstrumentAction) {
        enqueueControl(
            MailboxCommand.Reduce(
                commands = listOf(PerformanceCommand.Instrument(action)),
                persistAfter = true,
            ),
        )
    }

    private fun offerPacket(packet: MidiInputPacket): Boolean {
        if (closing.get()) return false
        if (mailbox.trySend(MailboxCommand.MidiPacketReceived(packet)).isSuccess) return true
        droppedCommands.incrementAndGet()
        scheduleOverflowRecovery()
        return false
    }

    /** UI, lifecycle and repository control messages are never discarded under MIDI load. */
    private fun enqueueControl(command: MailboxCommand) {
        if (closing.get()) return
        if (mailbox.trySend(command).isSuccess) return
        viewModelScope.launch(actorDispatcher) {
            runCatching { mailbox.send(command) }
        }
    }

    private fun scheduleOverflowRecovery() {
        if (closing.get()) return
        if (!overflowRecoveryQueued.compareAndSet(false, true)) return
        viewModelScope.launch(actorDispatcher) {
            runCatching { mailbox.send(MailboxCommand.RecoverFromOverflow(clock.nowNanos())) }
        }
    }

    private fun handle(command: MailboxCommand) {
        when (command) {
            is MailboxCommand.Reduce -> if (settingsLoaded) handleReduce(command)
            is MailboxCommand.TouchPressed -> if (settingsLoaded) {
                handleReduce(
                    MailboxCommand.Reduce(
                        commands = listOf(
                            PerformanceCommand.Instrument(
                                InstrumentAction.PressInterval(
                                    source = command.source,
                                    steps = command.steps,
                                    velocity = performanceState.instrument.config.defaultVelocity,
                                    timestampNanos = command.timestampNanos,
                                ),
                            ),
                        ),
                    ),
                )
            }
            is MailboxCommand.OneShotPressed -> if (settingsLoaded) {
                val velocity = performanceState.instrument.config.defaultVelocity
                val action = when (val intent = command.intent) {
                    is OneShotIntent.Interval -> InstrumentAction.PressInterval(
                        source = command.source,
                        steps = intent.steps,
                        velocity = velocity,
                        timestampNanos = command.timestampNanos,
                    )
                    OneShotIntent.Undo -> InstrumentAction.Undo(
                        source = command.source,
                        velocity = velocity,
                        timestampNanos = command.timestampNanos,
                    )
                    OneShotIntent.Home -> InstrumentAction.Home(
                        source = command.source,
                        sound = true,
                        velocity = velocity,
                        timestampNanos = command.timestampNanos,
                    )
                    is OneShotIntent.StrumTone -> InstrumentAction.StrumTone(
                        source = command.source,
                        voiceIndex = intent.index,
                        velocity = intent.velocity,
                        timestampNanos = command.timestampNanos,
                    )
                }
                handleReduce(
                    MailboxCommand.Reduce(
                        commands = listOf(PerformanceCommand.Instrument(action)),
                    ),
                )
                scheduleOneShotRelease(
                    source = command.source,
                    durationMillis = if (command.intent is OneShotIntent.StrumTone) {
                        STRUM_DURATION_MILLIS
                    } else {
                        ONE_SHOT_DURATION_MILLIS
                    },
                )
            }
            is MailboxCommand.MidiPacketReceived -> handleMidiPacket(command.packet)
            is MailboxCommand.MidiStateChanged -> handleMidiState(command.state)
            is MailboxCommand.MidiEventReceived -> handleMidiEvent(command.event)
            is MailboxCommand.ApplyStoredSettings -> applyStoredSettings(command.settings)
            is MailboxCommand.SelectSource -> if (settingsLoaded) {
                handleSelectSource(command.descriptor, command.rememberChoice)
            }
            is MailboxCommand.SelectDestination -> if (settingsLoaded) {
                handleSelectDestination(command.descriptor, command.rememberChoice)
            }
            is MailboxCommand.ToneRowIntentReceived -> if (settingsLoaded) {
                handleToneRowIntent(command.intent)
            }
            is MailboxCommand.InternalClockDue -> handleInternalClockDue(command)
            MailboxCommand.ToggleAudioEnabled -> if (settingsLoaded) {
                handleAudioEnabled(!audioMonitorEnabled)
            }
            is MailboxCommand.SetSynthPatch -> if (settingsLoaded) {
                handleSynthPatch(command.patch)
            }
            is MailboxCommand.PreviewSynthPatch -> if (settingsLoaded) {
                handleSynthPatchPreview(command.patch)
            }
            MailboxCommand.TogglePerformanceLock -> if (settingsLoaded) {
                performanceLock = !performanceLock
                markPersistenceDirty()
            }
            MailboxCommand.HostStarted -> handleHostStarted()
            MailboxCommand.HostStopped -> handleHostStopped()
            is MailboxCommand.DiagnosticsSampled -> applyDiagnosticsSample(command)
            MailboxCommand.DismissStatus -> statusMessage = null
            is MailboxCommand.PersistenceFailed -> handlePersistenceFailure(command)
            is MailboxCommand.PersistenceSucceeded -> handlePersistenceSuccess(command)
            is MailboxCommand.RecoverFromOverflow -> {
                overflowRecoveryQueued.set(false)
                applyPerformanceCommand(PerformanceCommand.Panic(command.timestampNanos))
                connectionParser.resetConnection()
                statusMessage = "File d’événements saturée : Panic de récupération exécuté"
            }
        }
    }

    private fun handlePersistenceFailure(command: MailboxCommand.PersistenceFailed) {
        val currentFailure = persistenceErrorVersion
        if (currentFailure != null && command.version < currentFailure) return
        val message = "Réglages non enregistrés : ${command.detail}"
        persistenceErrorVersion = command.version
        persistenceErrorStatus = message
        statusMessage = message
    }

    private fun handlePersistenceSuccess(command: MailboxCommand.PersistenceSucceeded) {
        val failedVersion = persistenceErrorVersion ?: return
        if (command.version < failedVersion) return
        if (statusMessage == persistenceErrorStatus) statusMessage = null
        persistenceErrorVersion = null
        persistenceErrorStatus = null
    }

    private fun handleReduce(command: MailboxCommand.Reduce) {
        command.commands.forEach(::applyPerformanceCommand)
        if (command.persistAfter) markPersistenceDirty()
        command.completionStatus?.let { statusMessage = it }
    }

    private fun handleToneRowIntent(intent: ToneRowUiIntent) {
        if (performanceLock && intent.isSecondaryArrangementIntent()) return
        val timestamp = clock.nowNanos().coerceAtLeast(0L)
        when (intent) {
            ToneRowUiIntent.Record -> applyPerformanceCommand(
                PerformanceCommand.ToneRow(
                    ToneRowAction.StartRecording(performanceState.instrument.currentNote),
                    timestamp,
                ),
            )
            ToneRowUiIntent.PlayPause -> {
                val action = when (performanceState.toneRow.mode) {
                    ToneRowMode.RECORDING -> ToneRowAction.FinishRecording
                    ToneRowMode.AUTO_PLAYING,
                    ToneRowMode.PAUSED,
                    -> ToneRowAction.PauseToggle
                    ToneRowMode.IDLE,
                    ToneRowMode.MANUAL_PLAYBACK,
                    -> ToneRowAction.StartAuto(restart = true)
                }
                applyPerformanceCommand(PerformanceCommand.ToneRow(action, timestamp))
            }
            ToneRowUiIntent.Stop -> applyPerformanceCommand(
                PerformanceCommand.ToneRow(ToneRowAction.Stop, timestamp),
            )
            ToneRowUiIntent.Restart -> applyPerformanceCommand(
                PerformanceCommand.ToneRow(ToneRowAction.Restart, timestamp),
            )
            ToneRowUiIntent.PlayOnce -> applyPerformanceCommand(
                PerformanceCommand.ToneRow(ToneRowAction.PlayOnce, timestamp),
            )
            is ToneRowUiIntent.SetPlaybackMode -> applyPerformanceCommand(
                PerformanceCommand.ToneRow(
                    ToneRowAction.SetPlayMode(
                        when (intent.mode) {
                            ToneRowUiPlaybackMode.PRIME -> ToneRowPlayMode.PRIME
                            ToneRowUiPlaybackMode.RETRO -> ToneRowPlayMode.RETRO
                            ToneRowUiPlaybackMode.RANDOM -> ToneRowPlayMode.RANDOM
                            ToneRowUiPlaybackMode.PENDULUM -> ToneRowPlayMode.PENDULUM
                        },
                    ),
                    timestamp,
                ),
            )
            ToneRowUiIntent.ToggleInversion -> applyPerformanceCommand(
                PerformanceCommand.ToneRow(
                    ToneRowAction.SetInverted(!performanceState.toneRow.inverted),
                    timestamp,
                ),
            )
            is ToneRowUiIntent.ChangeTranspositionSemitones -> applyPerformanceCommand(
                PerformanceCommand.ToneRow(
                    ToneRowAction.SetTransposition(
                        (performanceState.toneRow.transpositionSemitones + intent.delta).coerceIn(-127, 127),
                    ),
                    timestamp,
                ),
            )
            is ToneRowUiIntent.ChangeTranslationDegrees -> applyPerformanceCommand(
                PerformanceCommand.ToneRow(
                    ToneRowAction.SetTranslation(
                        (performanceState.toneRow.translation + intent.delta).coerceIn(-127, 127),
                    ),
                    timestamp,
                ),
            )
            is ToneRowUiIntent.ChangeOctave -> applyPerformanceCommand(
                PerformanceCommand.ToneRow(
                    ToneRowAction.SetOctaveOffset(performanceState.toneRow.octaveOffset + intent.delta),
                    timestamp,
                ),
            )
            ToneRowUiIntent.ResetTransformations -> applyPerformanceCommand(
                PerformanceCommand.ToneRow(ToneRowAction.ResetTransformations, timestamp),
            )
            is ToneRowUiIntent.AddSequenceStep -> applyPerformanceCommand(
                PerformanceCommand.ToneRow(ToneRowAction.AddSequenceStep(intent.movement), timestamp),
            )
            is ToneRowUiIntent.DeleteSequenceStep -> applyPerformanceCommand(
                PerformanceCommand.ToneRow(ToneRowAction.DeleteSequenceStep(intent.index), timestamp),
            )
            is ToneRowUiIntent.SelectSequenceStep -> applyPerformanceCommand(
                PerformanceCommand.ToneRow(ToneRowAction.SelectSequenceStep(intent.index), timestamp),
            )
            is ToneRowUiIntent.ChangeTempo -> applyPerformanceCommand(
                PerformanceCommand.Transport(
                    TransportAction.SetTempo(
                        (performanceState.transport.tempoBpm + intent.deltaBpm).coerceIn(20, 300),
                    ),
                ),
            )
            is ToneRowUiIntent.ChangeClockDivision -> {
                val currentIndex = CLOCK_DIVISIONS.indices.minBy {
                    kotlin.math.abs(CLOCK_DIVISIONS[it] - performanceState.transport.clocksPerStep)
                }
                val nextIndex = (currentIndex + intent.delta.sign()).coerceIn(CLOCK_DIVISIONS.indices)
                applyPerformanceCommand(
                    PerformanceCommand.Transport(
                        TransportAction.SetClocksPerStep(CLOCK_DIVISIONS[nextIndex]),
                    ),
                )
            }
            is ToneRowUiIntent.ChangeNoteDuration -> applyPerformanceCommand(
                PerformanceCommand.Transport(
                    TransportAction.SetNoteDuration(
                        (performanceState.transport.noteDurationPercent + intent.deltaPercent).coerceIn(1, 100),
                    ),
                ),
            )
            is ToneRowUiIntent.SetClockSource -> applyPerformanceCommand(
                PerformanceCommand.Transport(
                    TransportAction.SetClockSource(
                        source = when (intent.source) {
                            ToneRowUiClockSource.INTERNAL -> ClockSource.INTERNAL
                            ToneRowUiClockSource.MIDI -> ClockSource.MIDI
                        },
                        timestampNanos = timestamp,
                    ),
                ),
            )
            is ToneRowUiIntent.SelectPreset -> {
                val nextSlot = (intent.slot - 1).coerceIn(0, PRESET_SLOT_COUNT - 1)
                if (nextSlot != selectedPresetSlot) {
                    selectedPresetSlot = nextSlot
                    markPersistenceDirty()
                }
            }
            ToneRowUiIntent.SavePreset -> {
                presetBank = presetBank.save(
                    selectedPresetSlot,
                    currentPresetSnapshot("Preset ${selectedPresetSlot + 1}"),
                )
                markPersistenceDirty()
                statusMessage = "Preset ${selectedPresetSlot + 1} enregistré"
            }
            ToneRowUiIntent.RecallPreset -> {
                presetBank.recall(selectedPresetSlot)?.let { preset ->
                    restorePreset(preset, selectedPresetSlot, timestamp)
                    statusMessage = "Preset ${selectedPresetSlot + 1} rappelé"
                }
            }
            ToneRowUiIntent.DeletePreset -> {
                presetBank = presetBank.delete(selectedPresetSlot)
                markPersistenceDirty()
                statusMessage = "Preset ${selectedPresetSlot + 1} supprimé"
            }
        }
    }

    private fun reconcileInternalClock() {
        val due = performanceState.transport.nextInternalTickNanos?.takeIf {
            settingsLoaded &&
                hostStarted &&
                performanceState.transport.mode == TransportMode.PLAYING &&
                performanceState.transport.clockSource == ClockSource.INTERNAL
        }
        if (due == scheduledInternalTickNanos) return

        internalClockJob?.cancel()
        internalClockJob = null
        scheduledInternalTickNanos = due
        internalClockGeneration = if (internalClockGeneration == Long.MAX_VALUE) 0L else internalClockGeneration + 1L
        if (due == null) return

        val generation = internalClockGeneration
        internalClockJob = viewModelScope.launch(actorDispatcher) {
            val remaining = due - clock.nowNanos()
            if (remaining > 0L) delay(ceilNanosToMillis(remaining))
            enqueueControl(MailboxCommand.InternalClockDue(generation, due))
        }
    }

    private fun handleInternalClockDue(command: MailboxCommand.InternalClockDue) {
        if (command.generation != internalClockGeneration || command.dueNanos != scheduledInternalTickNanos) return
        scheduledInternalTickNanos = null
        internalClockJob = null
        if (
            !settingsLoaded ||
            !hostStarted ||
            performanceState.transport.mode != TransportMode.PLAYING ||
            performanceState.transport.clockSource != ClockSource.INTERNAL
        ) {
            return
        }
        applyPerformanceCommand(
            PerformanceCommand.Transport(
                TransportAction.InternalClock(maxOf(command.dueNanos, clock.nowNanos(), 0L)),
            ),
        )
    }

    private fun handleMidiPacket(packet: MidiInputPacket) {
        val connection = midiState.sourceConnection
        if (!settingsLoaded || !hostStarted || connection.phase != MidiConnectionPhase.OPEN) return
        if (!connection.matches(packet.source, packet.generation)) return

        val messages = connectionParser.consume(packet)
        messages.forEach { message ->
            when (
                val recall = PresetMidiPolicy.resolve(
                    message = message,
                    bank = presetBank,
                    inputChannel = performanceState.router.inputChannel,
                    allowRecall = performanceState.router.mode != PassThroughMode.PASS_THRU,
                )
            ) {
                is PresetRecallDecision.Consumed -> {
                    restorePreset(recall.preset, recall.slot, message.timestampNanos.coerceAtLeast(0L))
                    statusMessage = "Preset ${recall.slot + 1} rappelé par MIDI"
                }
                is PresetRecallDecision.NotFound,
                PresetRecallDecision.NotApplicable,
                -> applyPerformanceCommand(
                    PerformanceCommand.MidiMessages(
                        deviceId = packet.source.deviceId,
                        portNumber = packet.source.portNumber,
                        messages = listOf(message),
                    ),
                )
            }
        }
    }

    private fun handleMidiState(incoming: MidiRepositoryState) {
        val previous = midiState
        // Operational state and sampled counters are produced independently. Never let an older
        // queued topology snapshot move a cumulative diagnostic counter backwards.
        val state = incoming.copy(
            receivedPacketCount = maxOf(incoming.receivedPacketCount, previous.receivedPacketCount),
            sentMessageCount = maxOf(incoming.sentMessageCount, previous.sentMessageCount),
            droppedInputPacketCount = maxOf(
                incoming.droppedInputPacketCount,
                previous.droppedInputPacketCount,
            ),
            droppedOutputMessageCount = maxOf(
                incoming.droppedOutputMessageCount,
                previous.droppedOutputMessageCount,
            ),
        )
        val outputOverflowIncreased =
            state.droppedOutputMessageCount > previous.droppedOutputMessageCount
        val previousSource = previous.sourceConnection
        if (previousSource.phase == MidiConnectionPhase.OPEN &&
            !state.sourceConnection.continues(previousSource)
        ) {
            previousSource.descriptor?.let { descriptor ->
                applyPerformanceCommand(
                    PerformanceCommand.PurgeSource(
                        descriptor.deviceId,
                        descriptor.portNumber,
                        clock.nowNanos(),
                    ),
                )
            }
            connectionParser.resetConnection()
        } else if (!state.sourceConnection.sameIdentity(previousSource)) {
            connectionParser.resetConnection()
        }

        val previousDestination = previous.destinationConnection
        if (previousDestination.phase == MidiConnectionPhase.OPEN &&
            !state.destinationConnection.continues(previousDestination)
        ) {
            val previousId = previousDestination.descriptor?.stableSessionId?.let(::MidiDestinationId)
            if (previousId != null) {
                if (performanceState.currentDestination == previousId) {
                    applyPerformanceCommand(
                        PerformanceCommand.SwitchDestination(MidiDestinationId.Default, clock.nowNanos()),
                    )
                }
                lastMidiTimestampByDestination.remove(previousId)
            }
        }

        midiState = state
        when (state.sourceConnection.phase) {
            MidiConnectionPhase.OPEN,
            MidiConnectionPhase.ERROR,
            MidiConnectionPhase.LOST,
            -> pendingSourceSessionId = null
            MidiConnectionPhase.CLOSED,
            MidiConnectionPhase.OPENING,
            -> Unit
        }
        when (state.destinationConnection.phase) {
            MidiConnectionPhase.OPEN,
            MidiConnectionPhase.ERROR,
            MidiConnectionPhase.LOST,
            -> pendingDestinationSessionId = null
            MidiConnectionPhase.CLOSED,
            MidiConnectionPhase.OPENING,
            -> Unit
        }
        state.lastError?.let { statusMessage = it }
        if (outputOverflowIncreased) {
            // The repository already reset the physical port. Clear the deterministic
            // leases as well; comparing cumulative counters keeps this recovery idempotent.
            applyPerformanceCommand(PerformanceCommand.Panic(clock.nowNanos()))
            statusMessage = "Sortie MIDI saturée : Panic de récupération exécuté"
        }

        state.selectedDestination
            ?.takeIf { state.destinationConnection.phase == MidiConnectionPhase.OPEN }
            ?.let { selected ->
            val destination = MidiDestinationId(selected.stableSessionId)
            if (performanceState.currentDestination != destination) {
                applyPerformanceCommand(PerformanceCommand.SwitchDestination(destination, clock.nowNanos()))
            }
        }
        reconcilePreferredPorts()
    }

    /**
     * Native diagnostics may briefly wait on stream lifecycle work. Only the request is made
     * from the actor; collection runs on an I/O dispatcher and at most one sample can be in
     * flight. The completed immutable sample rejoins the musical mailbox in FIFO order.
     */
    private fun requestDiagnosticsSample() {
        if (closing.get() || !diagnosticsInFlight.compareAndSet(false, true)) return
        val sequence = diagnosticsSequence.incrementAndGet()
        val audioGeneration = audioLifecycleGeneration
        viewModelScope.launch(diagnosticsDispatcher) {
            val diagnostics = runCatching { audioEngine.diagnostics() }.getOrNull()
            val latest = midiRepository.state.value
            enqueueControl(
                MailboxCommand.DiagnosticsSampled(
                    sequence = sequence,
                    audioLifecycleGeneration = audioGeneration,
                    audio = diagnostics,
                    receivedPacketCount = latest.receivedPacketCount,
                    sentMessageCount = latest.sentMessageCount,
                    droppedInputPacketCount = latest.droppedInputPacketCount,
                ),
            )
        }
    }

    private fun applyDiagnosticsSample(sample: MailboxCommand.DiagnosticsSampled) {
        diagnosticsInFlight.set(false)
        if (sample.sequence <= lastDiagnosticsSequence) return
        lastDiagnosticsSequence = sample.sequence
        if (sample.audioLifecycleGeneration == audioLifecycleGeneration) {
            sample.audio?.let { diagnostics ->
                val wasRunning = audioRunning
                val previousRestartCount = audioDiagnostics.restartCount
                audioDiagnostics = diagnostics
                audioRunning = hostStarted && audioMonitorEnabled && diagnostics.streamRunning
                val recovered = audioRunning && (
                    !wasRunning || diagnostics.restartCount > previousRestartCount
                )
                if (recovered && !sendSynthPatch()) {
                    advanceAudioLifecycleGeneration()
                    audioEngine.send(AudioCommand.Panic)
                    audioEngine.stop()
                    audioRunning = false
                    statusMessage = "Paramètres audio non restaurés après reprise : moniteur arrêté"
                }
            }
        }
        midiState = midiState.copy(
            receivedPacketCount = maxOf(midiState.receivedPacketCount, sample.receivedPacketCount),
            sentMessageCount = maxOf(midiState.sentMessageCount, sample.sentMessageCount),
            droppedInputPacketCount = maxOf(
                midiState.droppedInputPacketCount,
                sample.droppedInputPacketCount,
            ),
        )
    }

    private fun handleMidiEvent(event: MidiRepositoryEvent) {
        when (event) {
            is MidiRepositoryEvent.ConnectionLost -> {
                val connection = midiState.connection(event.direction)
                if (!connection.matches(event.descriptor, event.generation)) return
                when (event.direction) {
                    MidiPortDirection.SOURCE -> {
                        applyPerformanceCommand(
                            PerformanceCommand.PurgeSource(
                                event.descriptor.deviceId,
                                event.descriptor.portNumber,
                                clock.nowNanos(),
                            ),
                        )
                        connectionParser.resetConnection()
                    }
                    MidiPortDirection.DESTINATION -> {
                        val lostDestination = MidiDestinationId(event.descriptor.stableSessionId)
                        if (performanceState.currentDestination == lostDestination) {
                            applyPerformanceCommand(
                                PerformanceCommand.SwitchDestination(
                                    MidiDestinationId.Default,
                                    clock.nowNanos(),
                                ),
                            )
                        }
                    }
                }
                statusMessage = event.detail ?: "Connexion MIDI perdue"
            }
            is MidiRepositoryEvent.OpenFailed -> {
                val connection = midiState.connection(event.direction)
                if (!connection.matches(event.descriptor, event.generation)) return
                if (event.direction == MidiPortDirection.SOURCE) connectionParser.resetConnection()
                if (event.direction == MidiPortDirection.DESTINATION) {
                    val failedDestination = MidiDestinationId(event.descriptor.stableSessionId)
                    if (performanceState.currentDestination == failedDestination) {
                        applyPerformanceCommand(
                            PerformanceCommand.SwitchDestination(
                                MidiDestinationId.Default,
                                clock.nowNanos(),
                            ),
                        )
                    }
                }
                statusMessage = event.detail
            }
            is MidiRepositoryEvent.SendFailed -> {
                if (!midiState.destinationConnection.matches(event.descriptor, event.generation)) return
                val failedDestination = MidiDestinationId(event.descriptor.stableSessionId)
                if (performanceState.currentDestination == failedDestination) {
                    applyPerformanceCommand(
                        PerformanceCommand.SwitchDestination(MidiDestinationId.Default, clock.nowNanos()),
                    )
                }
                statusMessage = event.detail
            }
            is MidiRepositoryEvent.InputOverflow -> {
                if (!midiState.sourceConnection.matches(event.descriptor, event.generation)) return
                // The rejected packet already queued one guaranteed RecoverFromOverflow command.
                statusMessage = "Entrée MIDI saturée : récupération en cours"
            }
            is MidiRepositoryEvent.PortsAdded -> reconnectAddedPorts(event.ports)
            is MidiRepositoryEvent.PortsRemoved -> Unit
        }
    }

    private fun reconnectAddedPorts(ports: List<MidiPortDescriptor>) {
        if (!settingsLoaded || !hostStarted) return
        if (midiState.selectedSource == null && pendingSourceSessionId == null &&
            midiState.sourceConnection.phase in setOf(
                MidiConnectionPhase.CLOSED,
                MidiConnectionPhase.LOST,
                MidiConnectionPhase.ERROR,
            )
        ) {
            ports.firstOrNull {
                it.direction == MidiPortDirection.SOURCE &&
                    it.persistentIdentity.reconnectKey == preferredSourceIdentity
            }?.let { handleSelectSource(it, rememberChoice = false) }
        }
        if (midiState.selectedDestination == null && pendingDestinationSessionId == null &&
            midiState.destinationConnection.phase in setOf(
                MidiConnectionPhase.CLOSED,
                MidiConnectionPhase.LOST,
                MidiConnectionPhase.ERROR,
            )
        ) {
            ports.firstOrNull {
                it.direction == MidiPortDirection.DESTINATION &&
                    it.persistentIdentity.reconnectKey == preferredDestinationIdentity
            }?.let { handleSelectDestination(it, rememberChoice = false) }
        }
    }

    private fun handleSelectSource(descriptor: MidiPortDescriptor?, rememberChoice: Boolean) {
        val connection = midiState.sourceConnection
        val requestedSession = descriptor?.stableSessionId
        val sameLiveRequest = connection.descriptor?.stableSessionId == requestedSession &&
            connection.phase in setOf(MidiConnectionPhase.OPEN, MidiConnectionPhase.OPENING)
        if (sameLiveRequest || (descriptor == null && connection.phase == MidiConnectionPhase.CLOSED)) {
            if (rememberChoice) {
                updatePreferredSource(descriptor?.persistentIdentity?.reconnectKey)
            }
            return
        }

        connection.descriptor?.let { current ->
            applyPerformanceCommand(
                PerformanceCommand.PurgeSource(current.deviceId, current.portNumber, clock.nowNanos()),
            )
        }
        connectionParser.resetConnection()
        pendingSourceSessionId = descriptor?.stableSessionId
        midiRepository.selectSource(descriptor)
        if (rememberChoice) {
            updatePreferredSource(descriptor?.persistentIdentity?.reconnectKey)
        }
    }

    private fun handleSelectDestination(descriptor: MidiPortDescriptor?, rememberChoice: Boolean) {
        val connection = midiState.destinationConnection
        val requestedSession = descriptor?.stableSessionId
        val sameLiveRequest = connection.descriptor?.stableSessionId == requestedSession &&
            connection.phase in setOf(MidiConnectionPhase.OPEN, MidiConnectionPhase.OPENING)
        if (sameLiveRequest || (descriptor == null && connection.phase == MidiConnectionPhase.CLOSED)) {
            if (rememberChoice) {
                updatePreferredDestination(descriptor?.persistentIdentity?.reconnectKey)
            }
            return
        }

        applyPerformanceCommand(
            PerformanceCommand.SwitchDestination(MidiDestinationId.Default, clock.nowNanos()),
        )
        pendingDestinationSessionId = descriptor?.stableSessionId
        midiRepository.selectDestination(descriptor)
        if (rememberChoice) {
            updatePreferredDestination(descriptor?.persistentIdentity?.reconnectKey)
        }
    }

    private fun updatePreferredSource(identity: String?) {
        if (identity == preferredSourceIdentity) return
        preferredSourceIdentity = identity
        markPersistenceDirty()
    }

    private fun updatePreferredDestination(identity: String?) {
        if (identity == preferredDestinationIdentity) return
        preferredDestinationIdentity = identity
        markPersistenceDirty()
    }

    private fun currentPresetSnapshot(
        name: String,
        state: PerformanceCoordinatorState = performanceState,
        sourceIdentity: String? = preferredSourceIdentity,
        destinationIdentity: String? = preferredDestinationIdentity,
    ): PerformancePresetSnapshot {
        val config = state.instrument.config
        return PerformancePresetSnapshot(
            name = name,
            musicalContext = MusicalContextSnapshot(
                rootPitchClass = config.rootPitchClass,
                scaleId = config.scale.id,
                chordId = config.chord.id,
                padArticulation = config.padArticulation,
                rangeMin = config.range.min,
                rangeMax = config.range.max,
                solfegeWrap = config.solfegeWrap,
            ),
            routing = RoutingSnapshot(
                passThroughMode = state.router.mode,
                inputChannel = state.router.inputChannel,
                outputChannel = config.outputChannel,
                preferredSourceIdentity = sourceIdentity,
                preferredDestinationIdentity = destinationIdentity,
            ),
            toneRow = state.toneRow.toPersistenceSnapshot(),
            transport = state.transport.toPersistenceSnapshot(),
            midiMapping = state.mapping,
        )
    }

    /** Panic is dispatched before the new immutable snapshot is installed. */
    private fun restorePreset(
        preset: PerformancePresetSnapshot,
        slot: Int?,
        timestampNanos: Long,
    ) {
        applyPerformanceCommand(PerformanceCommand.Panic(timestampNanos))
        val destination = performanceState.currentDestination
        val context = preset.musicalContext
        val config = runCatching {
            InstrumentConfig(
                rootPitchClass = context.rootPitchClass,
                scale = ScaleLibrary.byId(context.scaleId),
                range = MidiNoteRange(context.rangeMin, context.rangeMax),
                solfegeWrap = context.solfegeWrap,
                outputChannel = preset.routing.outputChannel,
                chord = ChordLibrary.byId(context.chordId),
                padArticulation = context.padArticulation,
            )
        }.getOrElse {
            InstrumentConfig(
                rootPitchClass = context.rootPitchClass,
                scale = ScaleLibrary.byId(context.scaleId),
                outputChannel = preset.routing.outputChannel,
                chord = ChordLibrary.byId(context.chordId),
                padArticulation = context.padArticulation,
            )
        }
        performanceState = PerformanceCoordinatorState.initial(config).copy(
            router = MidiRouterState(
                mode = preset.routing.passThroughMode,
                inputChannel = preset.routing.inputChannel,
            ),
            mapping = preset.midiMapping,
            currentDestination = destination,
            toneRow = preset.toneRow.toStoppedDomainState(),
            transport = preset.transport.toStoppedDomainState(),
        )
        preferredSourceIdentity = preset.routing.preferredSourceIdentity
        preferredDestinationIdentity = preset.routing.preferredDestinationIdentity
        selectedPresetSlot = slot?.coerceIn(0, PRESET_SLOT_COUNT - 1) ?: selectedPresetSlot
        if (settingsLoaded) markPersistenceDirty()
    }

    private fun applyStoredSettings(stored: StoredSettings) {
        if (settingsLoaded) return
        val mapping = stored.serializedMidiMapping
            ?.let(MidiMappingSerializer::decode)
            ?: DefaultMidiMap.mapping
        val migratedPreset = PerformancePresetSnapshot(
            name = "Working Session",
            musicalContext = MusicalContextSnapshot(
                rootPitchClass = stored.rootPitchClass,
                scaleId = stored.scaleId,
                chordId = stored.chordId,
                padArticulation = stored.padArticulation,
                rangeMin = stored.rangeMin,
                rangeMax = stored.rangeMax,
                solfegeWrap = stored.solfegeWrap,
            ),
            routing = RoutingSnapshot(
                passThroughMode = stored.passThroughMode,
                inputChannel = stored.inputChannel,
                outputChannel = stored.outputChannel,
                preferredSourceIdentity = stored.preferredSourceIdentity,
                preferredDestinationIdentity = stored.preferredDestinationIdentity,
            ),
            midiMapping = mapping,
        )
        presetBank = stored.presetBank
        selectedPresetSlot = stored.selectedPresetSlot ?: 0
        restorePreset(
            preset = stored.workingPreset ?: migratedPreset,
            slot = selectedPresetSlot,
            timestampNanos = clock.nowNanos().coerceAtLeast(0L),
        )
        audioMonitorEnabled = stored.audioMonitorEnabled
        synthPatch = stored.synthPatch
        performanceLock = stored.performanceLock
        settingsLoaded = true
        advanceAudioLifecycleGeneration()
        if (hostStarted && audioMonitorEnabled) audioRunning = startAudioMonitorWithPatch()
        reconcilePreferredPorts()
    }

    private fun handleAudioEnabled(enabled: Boolean) {
        advanceAudioLifecycleGeneration()
        previewedSynthPatch = null
        audioMonitorEnabled = enabled
        audioRunning = if (enabled && hostStarted) {
            startAudioMonitorWithPatch()
        } else {
            audioEngine.send(AudioCommand.Panic)
            audioEngine.stop()
            false
        }
        markPersistenceDirty()
    }

    private fun handleHostStarted() {
        if (hostStarted) return
        hostStarted = true
        advanceAudioLifecycleGeneration()
        previewedSynthPatch = null
        midiRepository.refreshDevices()
        if (settingsLoaded && audioMonitorEnabled) audioRunning = startAudioMonitorWithPatch()
        reconcilePreferredPorts()
    }

    private fun handleSynthPatch(patch: SynthPatch) {
        val previewWasApplied = previewedSynthPatch != null
        previewedSynthPatch = null
        if (patch == synthPatch && !previewWasApplied) return
        val patchChanged = patch != synthPatch
        synthPatch = patch
        if (audioMonitorEnabled && audioRunning && !sendSynthPatch()) {
            stopAudioAfterParameterFailure()
        }
        if (patchChanged) markPersistenceDirty()
    }

    private fun handleSynthPatchPreview(patch: SynthPatch) {
        if (!audioMonitorEnabled || !audioRunning) return
        val baseline = previewedSynthPatch ?: synthPatch
        val commands = patch.changedAudioCommandsSince(baseline)
        if (commands.isEmpty()) return
        if (sendSynthParameters(commands)) {
            previewedSynthPatch = patch
        } else {
            previewedSynthPatch = null
            stopAudioAfterParameterFailure()
        }
    }

    private fun startAudioMonitorWithPatch(): Boolean {
        previewedSynthPatch = null
        if (!audioEngine.start()) return false
        if (sendSynthPatch()) return true
        advanceAudioLifecycleGeneration()
        audioEngine.send(AudioCommand.Panic)
        audioEngine.stop()
        statusMessage = "Paramètres audio non appliqués : moniteur arrêté"
        return false
    }

    private fun sendSynthPatch(): Boolean {
        previewedSynthPatch = null
        return sendSynthParameters(synthPatch.toAudioCommands())
    }

    private fun sendSynthParameters(commands: Iterable<AudioCommand.Parameter>): Boolean {
        var accepted = true
        commands.forEach { command ->
            if (!audioEngine.send(command)) accepted = false
        }
        return accepted
    }

    private fun stopAudioAfterParameterFailure() {
        advanceAudioLifecycleGeneration()
        audioEngine.send(AudioCommand.Panic)
        audioEngine.stop()
        audioRunning = false
        statusMessage = "Paramètres audio non appliqués : moniteur arrêté"
    }

    private fun handleHostStopped() {
        if (!hostStarted) return
        advanceAudioLifecycleGeneration()
        previewedSynthPatch = null
        applyPerformanceCommand(PerformanceCommand.Panic(clock.nowNanos()))
        connectionParser.resetConnection()
        pendingSourceSessionId = null
        pendingDestinationSessionId = null
        midiRepository.selectSource(null)
        midiRepository.selectDestination(null)
        audioEngine.send(AudioCommand.Panic)
        audioEngine.stop()
        audioRunning = false
        hostStarted = false
    }

    private fun reconcilePreferredPorts() {
        if (!settingsLoaded || !hostStarted) return
        val sourceMayReconnect = midiState.sourceConnection.phase == MidiConnectionPhase.CLOSED ||
            midiState.sourceConnection.phase == MidiConnectionPhase.LOST
        if (midiState.selectedSource == null && pendingSourceSessionId == null && sourceMayReconnect) {
            val match = midiState.sources.firstOrNull {
                it.persistentIdentity.reconnectKey == preferredSourceIdentity
            }
            if (match != null) handleSelectSource(match, rememberChoice = false)
        }
        val destinationMayReconnect = midiState.destinationConnection.phase == MidiConnectionPhase.CLOSED ||
            midiState.destinationConnection.phase == MidiConnectionPhase.LOST
        if (midiState.selectedDestination == null &&
            pendingDestinationSessionId == null &&
            destinationMayReconnect
        ) {
            val match = midiState.destinations.firstOrNull {
                it.persistentIdentity.reconnectKey == preferredDestinationIdentity
            }
            if (match != null) handleSelectDestination(match, rememberChoice = false)
        }
    }

    private fun applyPerformanceCommand(command: PerformanceCommand) {
        val previous = performanceState
        val mayMutatePersistence = command.mayMutatePersistableContent(previous)
        runCatching { coordinator.reduce(performanceState, command) }
            .onSuccess { transition ->
                performanceState = transition.state
                transition.effects.forEach(::dispatchEffect)
                if (
                    mayMutatePersistence &&
                    !previous.hasSamePersistableContent(transition.state)
                ) {
                    markPersistenceDirty()
                }
            }
            .onFailure { error -> statusMessage = error.safeMessage() }
    }

    private fun dispatchEffect(effect: PerformanceEffect) {
        when (effect) {
            is PerformanceEffect.Midi -> dispatchMidiEffect(effect)
            is PerformanceEffect.Audio -> dispatchAudioEffect(effect)
            is PerformanceEffect.ReleaseAt -> {
                toneRowReleaseJob?.cancel()
                toneRowReleaseJob = viewModelScope.launch(actorDispatcher) {
                    val remainingNanos = effect.timestampNanos - clock.nowNanos()
                    if (remainingNanos > 0L) {
                        delay(ceilNanosToMillis(remainingNanos))
                    }
                    enqueueControl(
                        MailboxCommand.Reduce(
                            listOf(
                                PerformanceCommand.Instrument(
                                    InstrumentAction.Release(
                                        source = effect.source,
                                        timestampNanos = effect.timestampNanos,
                                    ),
                                    destination = effect.destination,
                                ),
                            ),
                        ),
                    )
                }
            }
        }
    }

    private fun dispatchMidiEffect(effect: PerformanceEffect.Midi) {
        val lastTimestamp = lastMidiTimestampByDestination[effect.destination] ?: 0L
        val normalizedTimestamp = maxOf(lastTimestamp, effect.message.timestampNanos, 0L)
        lastMidiTimestampByDestination[effect.destination] = normalizedTimestamp
        midiRepository.sendTo(
            effect.destination.value,
            effect.message.withTimestamp(normalizedTimestamp),
        )
    }

    private fun dispatchAudioEffect(effect: PerformanceEffect.Audio) {
        if (audioMonitorEnabled && audioRunning) audioEngine.send(effect.command)
    }

    private fun markPersistenceDirty() {
        if (!settingsLoaded) return
        persistenceDirty = true
        persistenceVersion = nextSequence(persistenceVersion)
    }

    private fun flushPersistenceIfDirty() {
        if (!settingsLoaded || !persistenceDirty) return
        val request = PersistenceRequest(
            version = persistenceVersion,
            source = capturePersistenceSource(),
        )
        if (persistence.trySend(request).isSuccess) persistenceDirty = false
    }

    private fun capturePersistenceSource(): PersistenceSource {
        return PersistenceSource(
            performance = performanceState,
            audioMonitorEnabled = audioMonitorEnabled,
            synthPatch = synthPatch,
            performanceLock = performanceLock,
            preferredSourceIdentity = preferredSourceIdentity,
            preferredDestinationIdentity = preferredDestinationIdentity,
            presetBank = presetBank,
            selectedPresetSlot = selectedPresetSlot,
        )
    }

    /**
     * A fixed coalescing window bounds continuous RANDOM/clock autosave traffic to five
     * writes per second. Unlike a resetting debounce, sustained input cannot starve storage.
     */
    private suspend fun runPersistenceWorker() {
        var failureOutstanding = false
        for (initial in persistence) {
            var latest = initial
            if (!closing.get()) delay(PERSISTENCE_COALESCE_MILLIS)
            latest = drainLatestPersistenceRequest(latest)
            var attempts = 0

            while (attempts < PERSISTENCE_MAX_ATTEMPTS) {
                latest = drainLatestPersistenceRequest(latest)

                val failure = writePersistence(latest)
                if (failure == null) {
                    if (failureOutstanding) {
                        sendPersistenceResult(MailboxCommand.PersistenceSucceeded(latest.version))
                    }
                    failureOutstanding = false
                    break
                }

                failureOutstanding = true
                sendPersistenceResult(
                    MailboxCommand.PersistenceFailed(
                        version = latest.version,
                        detail = failure,
                    ),
                )
                attempts += 1
                if (attempts < PERSISTENCE_MAX_ATTEMPTS) {
                    delay(PERSISTENCE_RETRY_MILLIS)
                }
            }
        }
    }

    private fun drainLatestPersistenceRequest(initial: PersistenceRequest): PersistenceRequest {
        var latest = initial
        while (true) {
            val newer = persistence.tryReceive().getOrNull() ?: return latest
            latest = newer
        }
    }

    private suspend fun writePersistence(request: PersistenceRequest): String? {
        val snapshot = request.source.toStoredSettings()
        val result: Result<Unit>? = withTimeoutOrNull(PERSISTENCE_WRITE_TIMEOUT_MILLIS) {
            runCatching { settingsRepository.update(snapshot) }
        }
        return when {
            result == null -> "délai d’écriture dépassé"
            result.isFailure -> requireNotNull(result.exceptionOrNull()).safeMessage()
            else -> null
        }
    }

    private suspend fun sendPersistenceResult(command: MailboxCommand) {
        if (closing.get()) return
        runCatching { mailbox.send(command) }
    }

    /** Full list projection and mapping serialization deliberately run off the musical actor. */
    private fun PersistenceSource.toStoredSettings(): StoredSettings {
        val config = performance.instrument.config
        val serializedMapping = performance.mapping
            .takeUnless { it == DefaultMidiMap.mapping }
            ?.let(MidiMappingSerializer::encode)
        return StoredSettings(
            audioMonitorEnabled = audioMonitorEnabled,
            synthPatch = synthPatch,
            rootPitchClass = config.rootPitchClass,
            scaleId = config.scale.id,
            chordId = config.chord.id,
            padArticulation = config.padArticulation,
            passThroughMode = performance.router.mode,
            rangeMin = config.range.min,
            rangeMax = config.range.max,
            solfegeWrap = config.solfegeWrap,
            inputChannel = performance.router.inputChannel,
            outputChannel = config.outputChannel,
            preferredSourceIdentity = preferredSourceIdentity,
            preferredDestinationIdentity = preferredDestinationIdentity,
            serializedMidiMapping = serializedMapping,
            performanceLock = performanceLock,
            workingPreset = currentPresetSnapshot(
                name = "Working Session",
                state = performance,
                sourceIdentity = preferredSourceIdentity,
                destinationIdentity = preferredDestinationIdentity,
            ),
            presetBank = presetBank,
            selectedPresetSlot = selectedPresetSlot,
        )
    }

    private fun advanceAudioLifecycleGeneration() {
        audioLifecycleGeneration = if (audioLifecycleGeneration == Long.MAX_VALUE) {
            0L
        } else {
            audioLifecycleGeneration + 1L
        }
    }

    private fun publish() {
        mutableUiState.value = AppUiState(
            performance = performanceState,
            midi = midiState,
            audioMonitorEnabled = audioMonitorEnabled,
            audioAvailable = audioEngine.isAvailable,
            audioRunning = audioRunning,
            audioDiagnostics = audioDiagnostics,
            synthPatch = synthPatch,
            performanceLock = performanceLock,
            settingsLoaded = settingsLoaded,
            hostStarted = hostStarted,
            statusMessage = statusMessage,
            droppedCoordinatorCommands = droppedCommands.get(),
            presetBank = presetBank,
            selectedPresetSlot = selectedPresetSlot,
        )
    }

    override fun onCleared() {
        closing.set(true)
        mailbox.close()
        synchronized(actorGuard) {
            internalClockJob?.cancel()
            internalClockJob = null
            toneRowReleaseJob?.cancel()
            toneRowReleaseJob = null
            val transition = coordinator.reduce(performanceState, PerformanceCommand.Panic(clock.nowNanos()))
            performanceState = transition.state
            transition.effects.forEach { effect ->
                when (effect) {
                    is PerformanceEffect.Midi -> dispatchMidiEffect(effect)
                    is PerformanceEffect.Audio -> dispatchAudioEffect(effect)
                    is PerformanceEffect.ReleaseAt -> Unit
                }
            }
            if (settingsLoaded && persistenceWorker.isActive) {
                persistenceVersion = nextSequence(persistenceVersion)
                persistence.trySend(
                    PersistenceRequest(
                        version = persistenceVersion,
                        source = capturePersistenceSource(),
                    ),
                )
                persistenceDirty = false
            }
        }
        // The explicitly owned worker drains the conflated final request asynchronously. Each
        // attempt is timeout-bounded; onCleared never waits on DataStore or its dispatcher.
        persistence.close()
        midiRepository.close()
        audioEngine.close()
        super.onCleared()
    }

    private sealed interface MailboxCommand {
        data class Reduce(
            val commands: List<PerformanceCommand>,
            val persistAfter: Boolean = false,
            val completionStatus: String? = null,
        ) : MailboxCommand

        data class TouchPressed(
            val source: TriggerSource.Touch,
            val steps: Int,
            val timestampNanos: Long,
        ) : MailboxCommand

        data class OneShotPressed(
            val source: TriggerSource.System,
            val intent: OneShotIntent,
            val timestampNanos: Long,
        ) : MailboxCommand

        data class MidiPacketReceived(val packet: MidiInputPacket) : MailboxCommand
        data class MidiStateChanged(val state: MidiRepositoryState) : MailboxCommand
        data class MidiEventReceived(val event: MidiRepositoryEvent) : MailboxCommand
        data class ApplyStoredSettings(val settings: StoredSettings) : MailboxCommand
        data class SelectSource(
            val descriptor: MidiPortDescriptor?,
            val rememberChoice: Boolean,
        ) : MailboxCommand
        data class SelectDestination(
            val descriptor: MidiPortDescriptor?,
            val rememberChoice: Boolean,
        ) : MailboxCommand
        data class ToneRowIntentReceived(val intent: ToneRowUiIntent) : MailboxCommand
        data class InternalClockDue(
            val generation: Long,
            val dueNanos: Long,
        ) : MailboxCommand
        data class RecoverFromOverflow(val timestampNanos: Long) : MailboxCommand
        data class PersistenceFailed(
            val version: Long,
            val detail: String,
        ) : MailboxCommand
        data class PersistenceSucceeded(val version: Long) : MailboxCommand
        data class DiagnosticsSampled(
            val sequence: Long,
            val audioLifecycleGeneration: Long,
            val audio: AudioDiagnostics?,
            val receivedPacketCount: Long,
            val sentMessageCount: Long,
            val droppedInputPacketCount: Long,
        ) : MailboxCommand
        data class SetSynthPatch(val patch: SynthPatch) : MailboxCommand
        data class PreviewSynthPatch(val patch: SynthPatch) : MailboxCommand
        data object ToggleAudioEnabled : MailboxCommand
        data object TogglePerformanceLock : MailboxCommand
        data object HostStarted : MailboxCommand
        data object HostStopped : MailboxCommand
        data object DismissStatus : MailboxCommand
    }

    private companion object {
        const val MAILBOX_CAPACITY: Int = 256
        const val DIAGNOSTICS_PERIOD_MILLIS: Long = 1_000L
        const val ONE_SHOT_DURATION_MILLIS: Long = 90L
        const val STRUM_DURATION_MILLIS: Long = 220L
        const val PERSISTENCE_COALESCE_MILLIS: Long = 200L
        const val PERSISTENCE_RETRY_MILLIS: Long = 250L
        const val PERSISTENCE_WRITE_TIMEOUT_MILLIS: Long = 1_000L
        const val PERSISTENCE_MAX_ATTEMPTS: Int = 2
        val CLOCK_DIVISIONS: IntArray = intArrayOf(1, 2, 3, 4, 6, 8, 12, 16, 24, 32, 48, 96)
    }

    private sealed interface OneShotIntent {
        data class Interval(val steps: Int) : OneShotIntent
        data class StrumTone(val index: Int, val velocity: Int) : OneShotIntent
        data object Undo : OneShotIntent
        data object Home : OneShotIntent
    }
}

private data class MidiOperationalKey(
    val sources: List<MidiPortDescriptor>,
    val destinations: List<MidiPortDescriptor>,
    val selectedSource: MidiPortDescriptor?,
    val selectedDestination: MidiPortDescriptor?,
    val sourceConnection: MidiConnectionState,
    val destinationConnection: MidiConnectionState,
    val droppedOutputMessageCount: Long,
    val lastError: String?,
)

/** Fast-path classification; false branches only touch runtime ownership/cursors/routes. */
private fun PerformanceCommand.mayMutatePersistableContent(
    state: PerformanceCoordinatorState,
): Boolean {
    return when (this) {
        is PerformanceCommand.Instrument -> when (action) {
            is InstrumentAction.SetScale,
            is InstrumentAction.SetRoot,
            is InstrumentAction.SetRange,
            is InstrumentAction.SetWrap,
            is InstrumentAction.SetChord,
            is InstrumentAction.SetPadArticulation,
            is InstrumentAction.SetOutputChannel,
            -> true
            is InstrumentAction.PressInterval -> state.toneRow.mode == ToneRowMode.RECORDING ||
                state.toneRow.mode == ToneRowMode.MANUAL_PLAYBACK
            is InstrumentAction.Undo -> state.toneRow.mode in setOf(
                ToneRowMode.MANUAL_PLAYBACK,
                ToneRowMode.AUTO_PLAYING,
                ToneRowMode.PAUSED,
            )
            is InstrumentAction.PressChromatic,
            is InstrumentAction.PressAbsolute,
            is InstrumentAction.PressPadAbsolute,
            is InstrumentAction.StrumTone,
            is InstrumentAction.UndoThenMove,
            is InstrumentAction.Release,
            is InstrumentAction.Home,
            is InstrumentAction.AnchorExternal,
            is InstrumentAction.Panic,
            -> false
        }
        is PerformanceCommand.MidiMessages,
        is PerformanceCommand.SetPassThroughMode,
        is PerformanceCommand.SetInputChannel,
        is PerformanceCommand.SetMapping,
        is PerformanceCommand.ToneRow,
        is PerformanceCommand.Transport,
        -> true
        is PerformanceCommand.SwitchDestination,
        is PerformanceCommand.PurgeSource,
        is PerformanceCommand.Panic,
        -> false
    }
}

/** Mirrors exactly the durable fields projected by PerformancePresetAdapters. */
private fun PerformanceCoordinatorState.hasSamePersistableContent(
    other: PerformanceCoordinatorState,
): Boolean {
    val leftConfig = instrument.config
    val rightConfig = other.instrument.config
    if (
        leftConfig.rootPitchClass != rightConfig.rootPitchClass ||
        leftConfig.scale.id != rightConfig.scale.id ||
        leftConfig.chord.id != rightConfig.chord.id ||
        leftConfig.padArticulation != rightConfig.padArticulation ||
        leftConfig.range != rightConfig.range ||
        leftConfig.solfegeWrap != rightConfig.solfegeWrap ||
        leftConfig.outputChannel != rightConfig.outputChannel ||
        router.mode != other.router.mode ||
        router.inputChannel != other.router.inputChannel ||
        mapping != other.mapping
    ) {
        return false
    }

    val leftToneRow = toneRow
    val rightToneRow = other.toneRow
    if (
        leftToneRow.entries != rightToneRow.entries ||
        leftToneRow.intervalSequence != rightToneRow.intervalSequence ||
        leftToneRow.playMode != rightToneRow.playMode ||
        leftToneRow.inverted != rightToneRow.inverted ||
        leftToneRow.transpositionSemitones != rightToneRow.transpositionSemitones ||
        leftToneRow.translation != rightToneRow.translation ||
        leftToneRow.octaveOffset != rightToneRow.octaveOffset ||
        leftToneRow.randomState != rightToneRow.randomState ||
        leftToneRow.referenceRootPitchClass != rightToneRow.referenceRootPitchClass ||
        leftToneRow.referenceScaleId != rightToneRow.referenceScaleId
    ) {
        return false
    }

    val leftTransport = transport
    val rightTransport = other.transport
    return leftTransport.clockSource == rightTransport.clockSource &&
        leftTransport.clocksPerStep == rightTransport.clocksPerStep &&
        leftTransport.tempoBpm == rightTransport.tempoBpm &&
        leftTransport.noteDurationPercent == rightTransport.noteDurationPercent
}

private fun MidiRepositoryState.operationalKey(): MidiOperationalKey {
    return MidiOperationalKey(
        sources = sources,
        destinations = destinations,
        selectedSource = selectedSource,
        selectedDestination = selectedDestination,
        sourceConnection = sourceConnection,
        destinationConnection = destinationConnection,
        droppedOutputMessageCount = droppedOutputMessageCount,
        lastError = lastError,
    )
}

private fun ToneRowUiIntent.isSecondaryArrangementIntent(): Boolean {
    return when (this) {
        is ToneRowUiIntent.AddSequenceStep,
        is ToneRowUiIntent.DeleteSequenceStep,
        is ToneRowUiIntent.SelectSequenceStep,
        is ToneRowUiIntent.ChangeTempo,
        is ToneRowUiIntent.ChangeClockDivision,
        is ToneRowUiIntent.ChangeNoteDuration,
        is ToneRowUiIntent.SetClockSource,
        is ToneRowUiIntent.SelectPreset,
        ToneRowUiIntent.SavePreset,
        ToneRowUiIntent.RecallPreset,
        ToneRowUiIntent.DeletePreset,
        -> true
        ToneRowUiIntent.Record,
        ToneRowUiIntent.PlayPause,
        ToneRowUiIntent.Stop,
        ToneRowUiIntent.Restart,
        ToneRowUiIntent.PlayOnce,
        is ToneRowUiIntent.SetPlaybackMode,
        ToneRowUiIntent.ToggleInversion,
        is ToneRowUiIntent.ChangeTranspositionSemitones,
        is ToneRowUiIntent.ChangeTranslationDegrees,
        is ToneRowUiIntent.ChangeOctave,
        ToneRowUiIntent.ResetTransformations,
        -> false
    }
}

private fun Int.sign(): Int = compareTo(0)

private fun ceilNanosToMillis(nanos: Long): Long {
    require(nanos >= 0L)
    val whole = nanos / 1_000_000L
    return whole + if (nanos % 1_000_000L == 0L) 0L else 1L
}

private fun nextSequence(value: Long): Long {
    return if (value == Long.MAX_VALUE) Long.MAX_VALUE else value + 1L
}

private fun MidiRepositoryState.connection(direction: MidiPortDirection): dev.intervaltablet.midi.MidiConnectionState {
    return when (direction) {
        MidiPortDirection.SOURCE -> sourceConnection
        MidiPortDirection.DESTINATION -> destinationConnection
    }
}

private fun dev.intervaltablet.midi.MidiConnectionState.matches(
    descriptor: MidiPortDescriptor,
    generation: Long,
): Boolean {
    return this.generation == generation && this.descriptor?.stableSessionId == descriptor.stableSessionId
}

private fun dev.intervaltablet.midi.MidiConnectionState.sameIdentity(
    other: dev.intervaltablet.midi.MidiConnectionState,
): Boolean {
    return generation == other.generation &&
        descriptor?.stableSessionId == other.descriptor?.stableSessionId
}

private fun dev.intervaltablet.midi.MidiConnectionState.continues(
    previous: dev.intervaltablet.midi.MidiConnectionState,
): Boolean {
    return phase == MidiConnectionPhase.OPEN && sameIdentity(previous)
}

private fun MidiMessage.withTimestamp(timestampNanos: Long): MidiMessage {
    return when (this) {
        is MidiMessage.NoteOn -> copy(timestampNanos = timestampNanos)
        is MidiMessage.NoteOff -> copy(timestampNanos = timestampNanos)
        is MidiMessage.ControlChange -> copy(timestampNanos = timestampNanos)
        is MidiMessage.ProgramChange -> copy(timestampNanos = timestampNanos)
        is MidiMessage.SongSelect -> copy(timestampNanos = timestampNanos)
        is MidiMessage.PitchBend -> copy(timestampNanos = timestampNanos)
        is MidiMessage.ChannelPressure -> copy(timestampNanos = timestampNanos)
        is MidiMessage.PolyPressure -> copy(timestampNanos = timestampNanos)
        is MidiMessage.RealTime -> copy(timestampNanos = timestampNanos)
        is MidiMessage.Raw -> copy(timestampNanos = timestampNanos)
    }
}

private fun Throwable.safeMessage(): String {
    return message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName
}
