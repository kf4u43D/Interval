package dev.intervaltablet

/** Shared presentation anchor; keeps header/pad previews aligned with reducer navigation. */
internal fun navigationAnchor(currentNote: Int, lastExternalNote: Int?, steps: Int): Int {
    return lastExternalNote?.takeIf { steps != 0 } ?: currentNote
}
