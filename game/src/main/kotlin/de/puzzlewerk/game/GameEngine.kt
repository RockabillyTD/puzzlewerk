package de.puzzlewerk.game

/**
 * Reine Spiellogik: Alle Funktionen sind frei von Seiteneffekten — gleicher Input
 * erzeugt immer denselben Output (Regel C2).
 *
 * HINWEIS (Phase 0): Platzhalter-Engine, die den Stil vorgibt. Die echten Regeln
 * werden in Phase 2 exakt nach docs/game-design.md implementiert.
 */
public object GameEngine {
    /**
     * Startet eine neue Partie für [levelId] mit dem gegebenen [seed].
     *
     * @throws IllegalArgumentException wenn [levelId] < 1 (Programmierfehler,
     *   kein erwartbarer Laufzeitfehler — siehe Regel C3).
     */
    public fun newGame(
        levelId: Int,
        seed: Long,
    ): GameState {
        require(levelId >= 1) { "levelId muss >= 1 sein, war $levelId" }
        return GameState(levelId = levelId, seed = seed)
    }

    /**
     * Wendet einen Punkte-Zug auf [state] an.
     *
     * Gültig nur, solange die Partie läuft und [points] nicht negativ ist;
     * andernfalls wird der Zug als [MoveResult.Rejected] abgelehnt.
     */
    public fun applyScore(
        state: GameState,
        points: Int,
    ): MoveResult =
        when {
            state.status != GameStatus.RUNNING ->
                MoveResult.Rejected(RejectionReason.GAME_ALREADY_FINISHED)

            points < 0 ->
                MoveResult.Rejected(RejectionReason.NEGATIVE_POINTS)

            else ->
                MoveResult.Applied(
                    state.copy(
                        score = state.score + points,
                        movesPlayed = state.movesPlayed + 1,
                    ),
                )
        }
}
