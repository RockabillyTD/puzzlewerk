package de.puzzlewerk.game.score

/**
 * Wertung einer GELÖSTEN Partie (Design §7.2). Zeit fließt nirgends ein (§7.2, §13.6).
 *
 * @property points Punkte 1000..1500: `1000 + max(0, 500 − 50 · max(0, Züge − Par))`
 *   (Invariante I5: monoton nicht-steigend in der Zugzahl).
 * @property stars Sterne 1..3: ★★★ bei `Züge ≤ Par`, ★★ bei `Züge ≤ Par + 3`,
 *   sonst ★ (§7.2). Sterne und Punkte schalten NICHTS frei (§7.2, §11.2).
 */
public data class Score(
    val points: Int,
    val stars: Int,
)

/**
 * Berechnet die Wertung aus Zügen und Par (Design §7.2) — pure Funktion,
 * nur bei gelöster Partie aufzurufen. Implementierung: Ticket PW-2.4.
 */
public fun interface ScoreCalculator {
    /**
     * Wertung für eine Lösung mit [moves] Zügen bei Par [par].
     *
     * Vorbedingung: `moves >= 0` und `par in 1..14` (Verletzung =
     * Programmierfehler, Regel C3). `moves < par` kommt im Spiel nicht vor
     * (Par ist minimal, §7.1), MUSS aber die volle Wertung 1500/★★★ liefern
     * (R31: bereits gelöst geladenes Level ⇒ 0 Züge).
     */
    public fun scoreFor(
        moves: Int,
        par: Int,
    ): Score
}
