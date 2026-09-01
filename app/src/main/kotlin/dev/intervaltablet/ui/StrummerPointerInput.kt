package dev.intervaltablet.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.unit.dp

/** Compose adapter kept intentionally thin: only quantized band crossings leave this function. */
internal suspend fun PointerInputScope.detectStrummerPointers(
    orientation: StrummerOrientation,
    toneCount: () -> Int,
    onHit: (toneIndex: Int, velocity: Int) -> Unit,
) {
    awaitPointerEventScope {
        val tracker = StrummerGestureTracker()
        try {
            while (true) {
                val event = awaitPointerEvent()
                event.changes.forEach { change ->
                    val geometry = strummerGeometry(
                        orientation = orientation,
                        width = this@detectStrummerPointers.size.width.toFloat(),
                        height = this@detectStrummerPointers.size.height.toFloat(),
                        toneCount = toneCount(),
                        hysteresis = 8.dp.toPx(),
                    ) ?: return@forEach
                    val coordinates = change.position.toStrummerCoordinates(orientation)
                    val hits = when {
                        change.pressed && !change.previousPressed -> tracker.down(
                            pointerId = change.id.value,
                            primaryPosition = coordinates.first,
                            velocityPosition = coordinates.second,
                            geometry = geometry,
                        )
                        change.pressed && change.previousPressed -> tracker.move(
                            pointerId = change.id.value,
                            primaryPosition = coordinates.first,
                            velocityPosition = coordinates.second,
                            geometry = geometry,
                        )
                        !change.pressed && change.previousPressed -> {
                            tracker.up(change.id.value)
                            emptyList()
                        }
                        else -> emptyList()
                    }
                    hits.forEach { hit -> onHit(hit.toneIndex, hit.velocity) }
                    if (change.pressed != change.previousPressed || hits.isNotEmpty()) change.consume()
                }
            }
        } finally {
            tracker.cancelAll()
        }
    }
}

private fun strummerGeometry(
    orientation: StrummerOrientation,
    width: Float,
    height: Float,
    toneCount: Int,
    hysteresis: Float,
): StrummerGeometry? {
    if (width <= 0f || height <= 0f || !width.isFinite() || !height.isFinite()) return null
    return when (orientation) {
        StrummerOrientation.VERTICAL -> StrummerGeometry(
            toneCount = toneCount.coerceAtLeast(0),
            primaryExtent = height,
            velocityExtent = width,
            hysteresis = hysteresis,
        )
        StrummerOrientation.HORIZONTAL -> StrummerGeometry(
            toneCount = toneCount.coerceAtLeast(0),
            primaryExtent = width,
            velocityExtent = height,
            hysteresis = hysteresis,
        )
    }
}

private fun Offset.toStrummerCoordinates(orientation: StrummerOrientation): Pair<Float, Float> {
    return when (orientation) {
        StrummerOrientation.VERTICAL -> y to x
        StrummerOrientation.HORIZONTAL -> x to y
    }
}
