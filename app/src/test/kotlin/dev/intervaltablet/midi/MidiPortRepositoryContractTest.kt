package dev.intervaltablet.midi

import dev.intervaltablet.domain.MidiMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MidiPortRepositoryContractTest {
    @Test
    fun persistentIdentityMatchesAcrossAndroidSessionIdsAndCosmeticWhitespace() {
        val original = descriptor(
            deviceId = 7,
            direction = MidiPortDirection.SOURCE,
            manufacturer = "Acme Audio",
            product = "Keys 49",
            deviceName = "Acme Keys",
            portName = " DIN   OUT ",
        )
        val reconnected = original.copy(
            deviceId = 42,
            manufacturer = " ACME AUDIO ",
            product = "keys 49",
            portName = "din out",
        )

        assertTrue(original.persistentIdentity.matches(reconnected))
        assertFalse(original.persistentIdentity.matches(reconnected.copy(portNumber = 2)))
        assertFalse(
            original.persistentIdentity.matches(
                reconnected.copy(direction = MidiPortDirection.DESTINATION),
            ),
        )
    }

    @Test
    fun catalogReconciliationDeduplicatesSortsAndReportsDeltas() {
        val removed = descriptor(1, MidiPortDirection.SOURCE, deviceName = "Removed")
        val retained = descriptor(2, MidiPortDirection.DESTINATION, deviceName = "Zulu")
        val added = descriptor(3, MidiPortDirection.SOURCE, deviceName = "Alpha")

        val delta = reconcileMidiPortCatalog(
            previousSources = listOf(removed),
            previousDestinations = listOf(retained),
            discovered = listOf(retained, added, added),
        )

        assertEquals(listOf(added), delta.sources)
        assertEquals(listOf(retained), delta.destinations)
        assertEquals(listOf(added), delta.added)
        assertEquals(listOf(removed), delta.removed)
    }

    @Test
    fun fakeRepositoryPublishesCatalogDeltasAndLosesRemovedOpenPort() {
        val repository = FakeMidiPortRepository()
        val source = descriptor(1, MidiPortDirection.SOURCE)
        repository.setPorts(listOf(source))
        repository.selectSource(source)

        repository.setPorts(emptyList())

        assertEquals(
            listOf(
                MidiRepositoryEvent.PortsAdded(listOf(source)),
                MidiRepositoryEvent.PortsRemoved(listOf(source)),
            ),
            repository.publishedEvents.take(2),
        )
        val loss = repository.publishedEvents
            .filterIsInstance<MidiRepositoryEvent.ConnectionLost>()
            .single()
        assertEquals(MidiConnectionLossReason.PORT_DISAPPEARED, loss.reason)
        assertEquals(MidiConnectionPhase.LOST, repository.state.value.sourceConnection.phase)
        assertNull(repository.state.value.selectedSource)
    }

    @Test
    fun fakeRepositoryTracksConnectionGenerationsAndTargetedSends() {
        val repository = FakeMidiPortRepository()
        val first = descriptor(1, MidiPortDirection.DESTINATION, deviceName = "First")
        val second = descriptor(2, MidiPortDirection.DESTINATION, deviceName = "Second")
        repository.setPorts(listOf(first, second))

        repository.selectDestination(first)
        val firstGeneration = repository.state.value.destinationConnection.generation
        repository.sendTo(first.stableSessionId, MidiMessage.NoteOn(0, 60, 100))
        repository.selectDestination(second)
        repository.sendTo(first.stableSessionId, MidiMessage.NoteOff(0, 60))
        repository.sendTo(second.stableSessionId, MidiMessage.NoteOn(0, 62, 100))

        assertTrue(repository.state.value.destinationConnection.generation > firstGeneration)
        assertEquals(
            listOf(first.stableSessionId, second.stableSessionId),
            repository.sentMessages.map { it.destinationSessionId },
        )
    }

    @Test
    fun fakeRepositoryPublishesLossAndClearsOpenedPort() {
        val repository = FakeMidiPortRepository()
        val source = descriptor(9, MidiPortDirection.SOURCE)
        repository.setPorts(listOf(source))
        repository.selectSource(source)

        repository.simulateConnectionLoss(
            direction = MidiPortDirection.SOURCE,
            reason = MidiConnectionLossReason.DEVICE_REMOVED,
        )

        assertNull(repository.state.value.selectedSource)
        assertEquals(MidiConnectionPhase.LOST, repository.state.value.sourceConnection.phase)
        val event = repository.publishedEvents.filterIsInstance<MidiRepositoryEvent.ConnectionLost>().single()
        assertEquals(source, event.descriptor)
        assertEquals(MidiConnectionLossReason.DEVICE_REMOVED, event.reason)
    }

    @Test
    fun fakeRepositoryPublishesSendFailureBeforeDestinationLoss() {
        val repository = FakeMidiPortRepository()
        val destination = descriptor(9, MidiPortDirection.DESTINATION)
        val message = MidiMessage.NoteOn(0, 60, 100)
        repository.setPorts(listOf(destination))
        repository.selectDestination(destination)

        repository.rejectSend(message)

        val failureIndex = repository.publishedEvents.indexOfFirst {
            it is MidiRepositoryEvent.SendFailed
        }
        val lossIndex = repository.publishedEvents.indexOfFirst {
            it is MidiRepositoryEvent.ConnectionLost
        }
        assertTrue(failureIndex >= 0)
        assertTrue(lossIndex > failureIndex)
        val failure = repository.publishedEvents[failureIndex] as MidiRepositoryEvent.SendFailed
        val loss = repository.publishedEvents[lossIndex] as MidiRepositoryEvent.ConnectionLost
        assertEquals(message, failure.message)
        assertEquals(MidiConnectionLossReason.SEND_FAILED, loss.reason)
        assertNull(repository.state.value.selectedDestination)
        assertEquals(MidiConnectionPhase.LOST, repository.state.value.destinationConnection.phase)
    }

    @Test
    fun fakeCloseIsIdempotent() {
        val repository = FakeMidiPortRepository()
        repository.close()
        repository.close()

        assertEquals(1, repository.closeCount)
        assertTrue(repository.state.value.sources.isEmpty())
        assertEquals(MidiConnectionPhase.CLOSED, repository.state.value.sourceConnection.phase)
    }

    @Test
    fun destinationBufferPreservesSendSelectTotalOrder() {
        val buffer = BoundedMidiDestinationOperationBuffer(capacity = 4)
        val destination = descriptor(4, MidiPortDirection.DESTINATION)
        val first = sendOperation(note = 60, timestampNanos = 1)
        val second = sendOperation(note = 62, timestampNanos = 2)

        assertEquals(MidiDestinationOfferResult.ACCEPTED, buffer.offerSend(first))
        assertEquals(
            MidiDestinationOfferResult.ACCEPTED,
            buffer.offerSelection(MidiDestinationOperation.Select(destination)),
        )
        assertEquals(MidiDestinationOfferResult.ACCEPTED, buffer.offerSend(second))

        assertEquals(first, buffer.poll())
        assertEquals(MidiDestinationOperation.Select(destination), buffer.poll())
        assertEquals(second, buffer.poll())
        assertNull(buffer.poll())
    }

    @Test
    fun destinationBufferCollapsesConsecutiveAndOverflowSelectionsToLatestRequest() {
        val buffer = BoundedMidiDestinationOperationBuffer(capacity = 2)
        val first = descriptor(1, MidiPortDirection.DESTINATION)
        val second = descriptor(2, MidiPortDirection.DESTINATION)
        val third = descriptor(3, MidiPortDirection.DESTINATION)

        buffer.offerSelection(MidiDestinationOperation.Select(first))
        buffer.offerSelection(MidiDestinationOperation.Select(second))
        assertEquals(MidiDestinationOperation.Select(second), buffer.poll())

        buffer.offerSend(sendOperation(60, 10))
        buffer.offerSend(sendOperation(61, 11))
        buffer.offerSelection(MidiDestinationOperation.Select(second))
        buffer.offerSelection(MidiDestinationOperation.Select(third))

        assertTrue(buffer.poll() is MidiDestinationOperation.Send)
        assertTrue(buffer.poll() is MidiDestinationOperation.Send)
        assertEquals(MidiDestinationOperation.Select(third), buffer.poll())
        assertNull(buffer.poll())
    }

    @Test
    fun destinationBufferReplacesEveryLostSendWithOneConservativeResetBarrier() {
        val buffer = BoundedMidiDestinationOperationBuffer(capacity = 2)
        val next = descriptor(8, MidiPortDirection.DESTINATION)
        val first = sendOperation(60, 10)
        val second = sendOperation(61, 20)

        assertEquals(MidiDestinationOfferResult.ACCEPTED, buffer.offerSend(first))
        assertEquals(MidiDestinationOfferResult.ACCEPTED, buffer.offerSend(second))
        assertEquals(
            MidiDestinationOfferResult.DROPPED_OVERFLOW,
            buffer.offerSend(sendOperation(62, 30)),
        )
        assertEquals(
            MidiDestinationOfferResult.ACCEPTED,
            buffer.offerSelection(MidiDestinationOperation.Select(next)),
        )
        assertEquals(
            MidiDestinationOfferResult.DROPPED_OVERFLOW,
            buffer.offerSend(sendOperation(63, 40)),
        )

        assertEquals(first, buffer.poll())
        assertEquals(second, buffer.poll())
        assertEquals(MidiDestinationOperation.ResetCurrent(40, droppedMessageCount = 2), buffer.poll())
        assertEquals(MidiDestinationOperation.Select(next), buffer.poll())
        assertNull(buffer.poll())
    }

    @Test
    fun destinationBufferCloseRejectsNewOffersButRetainsAcceptedDrain() {
        val buffer = BoundedMidiDestinationOperationBuffer(capacity = 1)
        val accepted = sendOperation(60, 1)
        assertEquals(MidiDestinationOfferResult.ACCEPTED, buffer.offerSend(accepted))

        buffer.closeForOffers()

        assertEquals(
            MidiDestinationOfferResult.REJECTED_CLOSED,
            buffer.offerSend(sendOperation(61, 2)),
        )
        assertEquals(accepted, buffer.poll())
        assertNull(buffer.poll())
    }

    @Test
    fun fakeRepositoryPublishesCumulativeOutputOverflowCount() {
        val repository = FakeMidiPortRepository()

        repository.simulateOutputOverflow(2)
        repository.simulateOutputOverflow(3)

        assertEquals(5L, repository.state.value.droppedOutputMessageCount)
    }

    private fun sendOperation(note: Int, timestampNanos: Long): MidiDestinationOperation.Send {
        return MidiDestinationOperation.Send(
            expectedDestinationSessionId = "destination",
            expectedGeneration = 0,
            message = MidiMessage.NoteOn(0, note, 100, timestampNanos),
        )
    }

    private fun descriptor(
        deviceId: Int,
        direction: MidiPortDirection,
        portNumber: Int = 1,
        manufacturer: String = "Maker",
        product: String = "Product",
        deviceName: String = "Device",
        portName: String = "Port",
    ): MidiPortDescriptor {
        return MidiPortDescriptor(
            deviceId = deviceId,
            portNumber = portNumber,
            direction = direction,
            deviceName = deviceName,
            portName = portName,
            manufacturer = manufacturer,
            product = product,
        )
    }
}
