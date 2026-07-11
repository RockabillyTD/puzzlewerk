package de.puzzlewerk.game.generator

import de.puzzlewerk.core.RandomSource
import de.puzzlewerk.core.SplitMix64Random
import de.puzzlewerk.game.board.Board
import de.puzzlewerk.game.board.Direction
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.board.Orientation
import de.puzzlewerk.game.color.LightColor
import de.puzzlewerk.game.element.Element
import de.puzzlewerk.game.level.Difficulty
import de.puzzlewerk.game.level.LevelDefinition

/**
 * Referenz-Implementierung des [LevelGenerator]-Vertrags (Design §9):
 * Rueckwaerts-Konstruktion von der Loesung (§9.3), exakter Par per
 * kostengeordnetem Solver (§9.4), deterministische Relaxierungsleiter bis zum
 * Fallback „Spiegelweg" (§9.5/7, R36). Aller Zufall stammt aus EINEM
 * fortlaufenden [SplitMix64Random]-Strom zum uebergebenen Seed (§8, ADR-003) —
 * gleiche `(seed, tier)` liefern byte-identische Level (I2, R34).
 */
public object DefaultLevelGenerator : LevelGenerator {
    override fun generate(
        seed: Long,
        tier: Difficulty,
    ): LevelDefinition = GeneratorRun(seed, tier).execute().level
}

/** Konstruktionsergebnis: Startzustand-Level plus unverwuerfeltes Loesungsbrett (I3). */
internal data class GeneratedLevel(
    val level: LevelDefinition,
    val solutionBoard: Board,
)

/** Verworfene Versuche je Relaxierungsstufe, bevor die Leiter weiterschaltet (§9.5/7). */
internal const val ATTEMPTS_PER_STAGE: Int = 1000

/** Par-Zielbereichs-Erweiterung der ersten Relaxierungsstufe (§9.5/7). */
private const val PAR_RELAXATION = 2

/** Ein Generator-Lauf; [attemptsPerStage] ist nur fuer R36-Tests injizierbar. */
internal class GeneratorRun(
    private val seed: Long,
    private val tier: Difficulty,
    private val attemptsPerStage: Int = ATTEMPTS_PER_STAGE,
) {
    private val random: RandomSource = SplitMix64Random(seed)

    fun execute(): GeneratedLevel {
        for (params in relaxationLadder(tierParameters(tier))) {
            repeat(attemptsPerStage) {
                LevelConstruction(params, tier, seed, random).attempt()?.let { return it }
            }
        }
        return spiegelwegFallback(tierParameters(tier).radiusRange.first, tier, seed, random)
    }
}

/**
 * Deterministische Relaxierungsleiter (§9.5/7): Stufe 0 = Tier-Parameter,
 * Stufe 1 = Par-Zielbereich ±2 (geklemmt auf 1..14), danach das Budget der
 * drehbaren Elemente schrittweise um 1 senken (optionale Elementtypen
 * entfallen dabei) bis 1..1. Danach greift [spiegelwegFallback].
 */
internal fun relaxationLadder(base: TierParameters): List<TierParameters> {
    val relaxedFloor = (base.parRange.first - PAR_RELAXATION).coerceAtLeast(LevelDefinition.MIN_PAR)
    val relaxedCeiling = (base.parRange.last + PAR_RELAXATION).coerceAtMost(LevelDefinition.MAX_PAR)
    val widened = base.copy(parRange = relaxedFloor..relaxedCeiling)
    val reduced =
        generateSequence(widened) { previous ->
            val upper = previous.rotatableRange.last - 1
            if (upper < 1) {
                null
            } else {
                previous.copy(
                    rotatableRange = (previous.rotatableRange.first - 1).coerceAtLeast(1)..upper,
                    palette =
                        previous.palette.copy(prismAllowed = false, filterAllowed = false, maxPortalPairs = 0),
                )
            }
        }
    return listOf(base) + reduced.toList()
}

/**
 * Parametrisches Fallback-Level „Spiegelweg" — EXAKT nach §9.5 Nr. 7: Quelle
 * Weiss auf `(−R, 0)` mit Richtung 0 (O), Spiegel auf `(0, 0)` mit
 * Loesungsorientierung `m = 0` (Parallelfall: Strahl laeuft geradeaus durch),
 * Kristall Weiss auf `(R, 0)`. Scramble-Offset `o = nextInt(5) + 1` aus dem
 * fortlaufenden Strom, Startorientierung `(0 − o) mod 6`; m = 0 ist die
 * einzige loesende Orientierung ⇒ exakt `Par = o`. Auf jedem Radius
 * konstruierbar ⇒ Terminierung garantiert (R36).
 */
internal fun spiegelwegFallback(
    radius: Int,
    tier: Difficulty,
    seed: Long,
    random: RandomSource,
): GeneratedLevel {
    val offset = random.nextInt(Orientation.COUNT - 1) + 1

    fun boardWith(mirror: Orientation): Board =
        Board(
            radius = radius,
            elements =
                mapOf(
                    HexCoord(-radius, 0) to Element.Source(LightColor.WHITE, Direction.EAST),
                    HexCoord(0, 0) to Element.Mirror(mirror),
                    HexCoord(radius, 0) to Element.Crystal(LightColor.WHITE),
                ),
        )

    return GeneratedLevel(
        level =
            LevelDefinition(
                board = boardWith(Orientation((-offset).mod(Orientation.COUNT))),
                par = offset,
                tier = tier,
                seed = seed,
            ),
        solutionBoard = boardWith(Orientation.ZERO),
    )
}
