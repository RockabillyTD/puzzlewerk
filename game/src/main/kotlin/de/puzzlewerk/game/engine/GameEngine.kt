package de.puzzlewerk.game.engine

import de.puzzlewerk.game.level.LevelDefinition

/**
 * Zugmodell der Partie (Design §6): pure Übergangsfunktionen auf [GameState];
 * die Implementierung erhält einen `Tracer` injiziert und wertet nach jedem
 * angewandten Zug frisch aus (§6.1).
 *
 * Bindende Semantik: R27–R32 (Randfall-Katalog §15, „Züge & Partie-Zustand")
 * sowie die Invarianten I6, I7, I9 und I10 (§14).
 *
 * Implementierung: Ticket PW-2.4.
 */
public interface GameEngine {
    /**
     * Startet eine Partie für [level]: Orientierungen aus dem Startbrett,
     * leerer Verlauf, Zähler 0, plus initiale trace-Auswertung (die UI rendert
     * Strahlen von Anfang an, §12.3).
     *
     * Randfall R31: Ist das Level bereits im Startzustand gelöst (nur durch
     * Datenfehler möglich), startet die Partie sofort als gelöst mit 0 Zügen.
     *
     * Vorbedingung: [level] hat die Validierung (§16.2) bestanden.
     */
    public fun newGame(level: LevelDefinition): MoveResult.Applied

    /**
     * Wendet [move] auf [state] an (Design §6.1/§6.2).
     *
     * - [Move.Rotate]: Orientierung +1 Stufe, Zelle an den Verlauf, Zähler +1;
     *   danach trace — löst der Zug das Brett, wechselt die Partie in Gelöst.
     * - [Move.Undo]: letzte Drehung rückgängig (I10), Zähler −1.
     * - [Move.Reset]: Startzustand, Verlauf leer (R29).
     * - Jeder wirkungslose Zug liefert [MoveResult.Invalid] und lässt [state]
     *   unverändert (I7): R27, R28, R32.
     *
     * [state] selbst wird NIE mutiert (pure Funktion, Regel C2).
     */
    public fun applyMove(
        state: GameState,
        move: Move,
    ): MoveResult
}
