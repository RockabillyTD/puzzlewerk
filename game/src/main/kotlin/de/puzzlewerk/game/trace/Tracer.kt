package de.puzzlewerk.game.trace

import de.puzzlewerk.game.board.Board

/**
 * Harte Obergrenze verarbeiteter Strahlzustände je trace-Auswertung (Design §5.3).
 * Der Zustandsraum ist maximal `91 · 6 · 7 = 3822` (R=5); das Erreichen von 4000
 * ist per Konstruktion unmöglich und daher ein Programmierfehler — hier ist eine
 * Exception korrekt (Regel C3, Invariante I1).
 */
public const val MAX_TRACE_STEPS: Int = 4000

/**
 * Strahlverfolgung `trace(board)` (Design §5): pure Funktion vom Brettzustand
 * zum [TraceResult]; Strahlen interagieren NICHT miteinander (§3.2), Mischung
 * findet ausschließlich am Kristall statt.
 *
 * Vertrag (bindend, §5.2/§5.3):
 * - Ein Strahlzustand ist das Tripel (Zelle, Richtung, Farbe); das visited-Set
 *   MUSS die Farbe enthalten (R18).
 * - Quellen werden aufsteigend nach (q, dann r) gestartet; Splitter enqueued
 *   erst Transmission, dann Reflexion; Prisma enqueued R, G, B.
 * - Terminierung über die endliche Zustandsmenge, nie über Energie (§4.3);
 *   mehr als [MAX_TRACE_STEPS] verarbeitete Zustände ⇒ Exception (C3).
 *
 * Vorbedingung: [Board] erfüllt die Schema-Regeln aus §16.2 (validierte oder
 * generierte Level; die Engine verkraftet auch 100 % belegte Zellen, R41).
 *
 * Implementierung: Ticket PW-2.3.
 */
public fun interface Tracer {
    /** Wertet alle Strahlen deterministisch aus und liefert das reproduzierbare Ergebnis. */
    public fun trace(board: Board): TraceResult
}
