package de.puzzlewerk.game.trace

import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.color.LightColor

/**
 * Beleuchtete Brettkante von [from] zur Nachbarzelle [to] (Design §5.1) —
 * Renderdaten für die Strahl-Darstellung (§12.3).
 *
 * @property from Zelle, die der Strahl verlässt.
 * @property to Zelle, die der Strahl betritt ([from]-Nachbar in Laufrichtung).
 * @property color Strahlfarbe auf dieser Kante, nie 0 (Invariante I8).
 */
public data class Segment(
    val from: HexCoord,
    val to: HexCoord,
    val color: LightColor,
)

/**
 * Ergebnis einer Strahlverfolgung (Design §5, §16.1).
 *
 * @property segments Beleuchtete Kanten in der NORMATIVEN Erzeugungsreihenfolge
 *   (§5.2: Quellen sortiert nach (q, r); Splitter erst Transmission, dann
 *   Reflexion; Prisma R, G, B) — dadurch byte-identisch reproduzierbar
 *   (Golden-Tests, Animationsreihenfolge).
 * @property received OR-Akkumulator je Kristallzelle. Enthält NUR Kristalle,
 *   die mindestens ein Strahl getroffen hat — ein fehlender Eintrag bedeutet
 *   „dunkel" (empfangen = 0, §5.4); gespeicherte Werte sind immer 1..7 (I8).
 * @property solved `true` gdw. JEDER Kristall exakt seine Sollfarbe empfängt
 *   (§5.4); Übersättigung irgendeines Kristalls ⇒ `false`.
 */
public data class TraceResult(
    val segments: List<Segment>,
    val received: Map<HexCoord, LightColor>,
    val solved: Boolean,
)
