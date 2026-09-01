package dev.intervaltablet.ui

import kotlin.math.roundToInt

internal enum class StrummerOrientation {
    VERTICAL,
    HORIZONTAL,
}

internal data class StrumHit(
    val toneIndex: Int,
    val velocity: Int,
) {
    init {
        require(toneIndex >= 0)
        require(velocity in 1..127)
    }
}

/**
 * Pure geometry for the strummer surface.
 *
 * The primary axis chooses a tone while the perpendicular axis chooses MIDI velocity. Keeping
 * this mapping outside Compose makes skipped bands, reversals and edge clamping deterministic.
 */
internal data class StrummerGeometry(
    val toneCount: Int,
    val primaryExtent: Float,
    val velocityExtent: Float,
    val hysteresis: Float = 0f,
) {
    init {
        require(toneCount >= 0)
        require(primaryExtent.isFinite() && primaryExtent > 0f)
        require(velocityExtent.isFinite() && velocityExtent > 0f)
        require(hysteresis.isFinite() && hysteresis >= 0f)
    }

    fun hitAt(primaryPosition: Float, velocityPosition: Float): StrumHit? {
        if (toneCount == 0 || !primaryPosition.isFinite() || !velocityPosition.isFinite()) {
            return null
        }
        val primaryFraction = (primaryPosition / primaryExtent).coerceIn(0f, 1f)
        val toneIndex = (primaryFraction * toneCount).toInt().coerceIn(0, toneCount - 1)
        val velocityFraction = (velocityPosition / velocityExtent).coerceIn(0f, 1f)
        val velocity = (1f + velocityFraction * 126f).roundToInt().coerceIn(1, 127)
        return StrumHit(toneIndex = toneIndex, velocity = velocity)
    }
}

/**
 * Tracks each pointer independently and emits only tone-band crossings.
 *
 * A fast move can skip several visual bands; every intermediate index is returned in traversal
 * order. Moving within a band produces no hit, so pointer sampling rate never becomes mailbox
 * traffic. Reversing direction naturally emits the crossed bands in reverse order.
 */
internal class StrummerGestureTracker {
    private data class PointerState(
        val lastToneIndex: Int,
        val toneCount: Int,
    )

    private val pointers = linkedMapOf<Long, PointerState>()

    val activePointerCount: Int
        get() = pointers.size

    fun down(
        pointerId: Long,
        primaryPosition: Float,
        velocityPosition: Float,
        geometry: StrummerGeometry,
    ): List<StrumHit> {
        if (pointerId in pointers) return emptyList()
        val hit = geometry.hitAt(primaryPosition, velocityPosition) ?: return emptyList()
        pointers[pointerId] = PointerState(hit.toneIndex, geometry.toneCount)
        return listOf(hit)
    }

    fun move(
        pointerId: Long,
        primaryPosition: Float,
        velocityPosition: Float,
        geometry: StrummerGeometry,
    ): List<StrumHit> {
        val previous = pointers[pointerId] ?: return emptyList()
        val rawTarget = geometry.hitAt(primaryPosition, velocityPosition) ?: return emptyList()
        if (previous.toneCount != geometry.toneCount || previous.lastToneIndex !in 0 until geometry.toneCount) {
            pointers[pointerId] = PointerState(rawTarget.toneIndex, geometry.toneCount)
            return listOf(rawTarget)
        }
        if (previous.lastToneIndex == rawTarget.toneIndex) return emptyList()

        val bandExtent = geometry.primaryExtent / geometry.toneCount.toFloat()
        val hysteresis = geometry.hysteresis.coerceAtMost(bandExtent * 0.45f)
        val adjustedPrimary = if (rawTarget.toneIndex > previous.lastToneIndex) {
            primaryPosition - hysteresis
        } else {
            primaryPosition + hysteresis
        }
        val target = geometry.hitAt(adjustedPrimary, velocityPosition) ?: return emptyList()
        if (previous.lastToneIndex == target.toneIndex) return emptyList()

        val step = if (target.toneIndex > previous.lastToneIndex) 1 else -1
        val hits = buildList {
            var index = previous.lastToneIndex + step
            while (true) {
                add(target.copy(toneIndex = index))
                if (index == target.toneIndex) break
                index += step
            }
        }
        pointers[pointerId] = PointerState(target.toneIndex, geometry.toneCount)
        return hits
    }

    fun up(pointerId: Long): Boolean = pointers.remove(pointerId) != null

    fun cancelAll() {
        pointers.clear()
    }
}
