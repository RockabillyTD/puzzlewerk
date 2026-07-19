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
 * On-Board-Absorptionspunkt eines Strahls (§13.8a) — Renderdatum für
 * die Auftreff-Funken. Ein Eintrag JE absorbiertem Strahl: enden zwei
 * Strahlen in derselben Zelle (z. B. R und G am Gelb-Kristall), gibt
 * es zwei Einträge mit je eigener Strahlfarbe.
 *
 * @property cell Zelle der Absorption (Wand, Quelle, Kristall oder
 *   Filter). Brett-Aus-Absorptionen erzeugen KEINEN Eintrag (§13.8a —
 *   bewusste Optik-Ausnahme zu §4.8; die trace-Logik bleibt unberührt).
 * @property color Farbe des absorbierten Strahls beim Auftreffen,
 *   nie 0 (I8).
 */
public data class BeamEndpoint(
    val cell: HexCoord,
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
 * @property endpoints Strahl-Endpunkte (§13.8a, ADR-012) in der normativen
 *   Strahl-Verarbeitungsreihenfolge (§5.2, dieselbe wie [segments]) —
 *   byte-identisch reproduzierbar und Emitter-Index-Basis (ADR-011).
 */
public data class TraceResult(
    val segments: List<Segment>,
    val received: Map<HexCoord, LightColor>,
    val solved: Boolean,
    val endpoints: List<BeamEndpoint>,
)
