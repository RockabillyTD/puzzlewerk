package de.puzzlewerk.data.store

/**
 * Bindende Wertebereiche gelöster Partien (Design §7.2): Punkte
 * `1000 + max(0, 500 − 50 · max(0, Züge − Par))`, Sterne 1..3.
 * Der Lademapper weist Verstöße als Korruption ab; die Schreibpfade
 * erzwingen sie per `require` (Regel C3), damit nie ein Bestand entsteht,
 * den das eigene Laden ablehnen müsste.
 */
internal val SCORE_POINTS_RANGE: IntRange = 1000..1500

/** Sterne 1..3 (Design §7.2) — deckungsgleich mit der `Score`-Invariante. */
internal val SCORE_STARS_RANGE: IntRange = 1..3
