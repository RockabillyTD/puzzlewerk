package de.puzzlewerk.app.ui.juice

/**
 * Unveränderlicher Snapshot des gesamten Effekt-Zustands je Frame
 * (docs/game-design.md §13.13, ADR-011). Der Renderer liest AUSSCHLIESSLICH
 * diesen Snapshot (ein Canvas-Layer, BlendMode.Plus) und allokiert dabei
 * nichts; neue Zustände entstehen nur über [JuiceStepper.step].
 *
 * GLEICHHEIT: [particles] enthält Arrays — `equals` vergleicht dort nur
 * Referenzen. Determinismus-Tests (§13.13: gleiche dt-Folge ⇒ bit-identische
 * Partikelzustände) vergleichen daher feldweise per `contentEquals`.
 *
 * @property elapsedMillis Zeit seit Betreten des Spiel-Screens — Nullpunkt der
 *   Puls-Phase (§13.8a, deterministisch, kein Zufall).
 * @property reduceMotion R44: Spawns erzeugen 0 Partikel, Flash wird zum Fade,
 *   Puls statisch; Zeitpunkte und Overlay-Frist unverändert. Leere
 *   Partikel-Snapshots sind gültiger Zustand (kein Codepfad darf crashen).
 * @property levelSeed Seed des laufenden Levels — Basis der Seed-Ableitung
 *   `juiceSeed(levelSeed, zugNummer, emitterIndex)` (ADR-011).
 * @property haloPulseFactor Multiplikator auf das Halo-Grundalpha für ALLE
 *   Strahlen dieses Frames: sinusförmig 1 ± 0,2 mit exakt 2,0 Hz, phasensynchron
 *   ab [elapsedMillis] = 0; konstant 1,0 unter Reduce-Motion (§13.8a/§13.12).
 * @property flashAlpha Alpha des additiven Vollbild-Weiß (§13.10: 0,35 → 0
 *   über 80 ms; Reduce-Motion: 0 → 0,15 → 0 über 400 ms); 0,0 = kein Flash.
 * @property emitters Aktive kontinuierliche Funken-Emitter an den
 *   Strahl-Endpunkten (§13.8a) — nach jedem Zug komplett ersetzt
 *   ([JuiceEvent.EndpointsChanged]).
 * @property pendingBursts Geplante, noch nicht gestartete Kristall-Bursts der
 *   Kaskade (§13.9: 40-ms-Versatz, Kappe ab dem 5.) sowie das geplante
 *   Feuerwerk (§13.10, Start bei t_fw).
 * @property particles Alle lebenden Partikel als Structure-of-Arrays.
 * @property flashRemainingMillis PW-4.4-Erweiterung (dokumentierte Abweichung
 *   von der PW-4.2-Deklaration): Restlaufzeit des Flash-Fensters. [flashAlpha]
 *   ist eine reine Projektion daraus (§13.10-Kurve bzw. Reduce-Motion-Dreieck)
 *   und deshalb NICHT eindeutig invertierbar — die Kurve braucht einen eigenen
 *   Zeitzähler, den der pure Stepper zwischen Frames tragen muss. 0 = kein Flash.
 */
internal data class JuiceState(
    val elapsedMillis: Long,
    val reduceMotion: Boolean,
    val levelSeed: Long,
    val haloPulseFactor: Float,
    val flashAlpha: Float,
    val emitters: List<SparkEmitter>,
    val pendingBursts: List<ScheduledBurst>,
    val particles: ParticleSnapshot,
    val flashRemainingMillis: Long,
) {
    internal companion object {
        /** Neutraler Startzustand vor dem ersten [JuiceEvent.ScreenEntered]. */
        val EMPTY = JuiceState(0L, false, 0L, 1f, 0f, emptyList(), emptyList(), ParticleSnapshot.EMPTY, 0L)
    }
}

/**
 * Lebende Partikel als Structure-of-Arrays — der Renderer iteriert
 * indexbasiert 0 until [count] ohne Allokation (Leitplanke
 * docs/phase4-juice-update.md §2). Alle Arrays haben mindestens Länge [count];
 * Slots ≥ [count] sind undefiniert (Pool-Kapazität).
 *
 * GLEICHHEIT: Arrays vergleichen per Referenz — Tests nutzen `contentEquals`
 * je Feld (siehe [JuiceState]).
 *
 * @property count Anzahl lebender Partikel (0 ist gültig, R44).
 * @property xDp X-Position in dp-Brettkoordinaten.
 * @property yDp Y-Position in dp-Brettkoordinaten.
 * @property sizeDp Durchmesser in dp (Funken: 2 dp, §13.8a).
 * @property alpha Deckkraft 0..1 (Lebensdauer-Ausblendung).
 * @property colorArgb ARGB-Farbe (Palette §13.4, bereits aufgelöst).
 * @property vxDp PW-4.4-Simulationsfeld: Geschwindigkeit x in dp/s.
 * @property vyDp PW-4.4-Simulationsfeld: Geschwindigkeit y in dp/s.
 * @property gravityDpPerSec2 PW-4.4-Simulationsfeld: Gravitation in dp/s²
 *   (Funken 0, Kristall-Burst 240, Feuerwerk 480 — §13.9/§13.10).
 * @property alphaFadePerMillis PW-4.4-Simulationsfeld: linearer Alpha-Abbau je
 *   ms (= 1/Lebensdauer); der Partikel stirbt, sobald [alpha] ≤ 0 fällt.
 *
 * ABWEICHUNG (dokumentiert, PW-4.4): Die vier Simulationsfelder ergänzen die
 * PW-4.2-Deklaration. Die pure [JuiceStepper.step]-Integration MUSS Geschwindigkeit,
 * Gravitation und Restlebensdauer zwischen Frames tragen; die render-orientierte
 * Deklaration konnte das nicht. Der Renderer (PW-4.5) liest weiterhin nur
 * count/xDp/yDp/sizeDp/alpha/colorArgb — die Simulationsfelder sind für ihn unsichtbar.
 */
internal data class ParticleSnapshot(
    val count: Int,
    val xDp: FloatArray,
    val yDp: FloatArray,
    val sizeDp: FloatArray,
    val alpha: FloatArray,
    val colorArgb: IntArray,
    val vxDp: FloatArray,
    val vyDp: FloatArray,
    val gravityDpPerSec2: FloatArray,
    val alphaFadePerMillis: FloatArray,
) {
    internal companion object {
        private val E = FloatArray(0)

        /** Leerer Pool (0 Partikel) — gültiger Zustand (R44). */
        val EMPTY = ParticleSnapshot(0, E, E, E, E, IntArray(0), E, E, E, E)
    }
}

/**
 * Kontinuierlicher Punkt-Emitter an einem Strahl-Endpunkt (§13.8a:
 * 4 Funken/s, Lebensdauer 400 ms, 2 dp, 60 dp/s radial; Winkel aus dem
 * Juice-PRNG).
 *
 * @property xDp Emitter-Position (dp-Brettkoordinaten).
 * @property yDp Emitter-Position (dp-Brettkoordinaten).
 * @property colorArgb Strahlfarbe des absorbierten Strahls (ARGB).
 * @property emitterIndex Position in `TraceResult.endpoints` (ADR-012) —
 *   zusammen mit [moveNumber] und [JuiceState.levelSeed] die Seed-Basis
 *   (ADR-011: `juiceSeed = mix64(mix64(mix64(levelSeed) + zugNummer) +
 *   emitterIndex)`).
 * @property moveNumber Zug, der den zugrunde liegenden trace erzeugte
 *   (0 = Partie-Start).
 * @property spawnedCount Bisher von diesem Emitter erzeugte Funken — Basis der
 *   puren Pro-Funke-Seed-Ableitung (Zufall wird NUR beim Spawn gezogen,
 *   §13.13/ADR-011); zusammen mit der dt-Folge deterministisch.
 */
internal data class SparkEmitter(
    val xDp: Float,
    val yDp: Float,
    val colorArgb: Int,
    val emitterIndex: Int,
    val moveNumber: Int,
    val spawnedCount: Int,
)

/**
 * Geplanter Einmal-Ausbruch: Kristall-Burst der Kaskade (§13.9) oder
 * Feuerwerks-Explosion (§13.10, [kind] = [BurstKind.FIREWORK], Start = t_fw).
 *
 * @property startAtMillis Absoluter Startzeitpunkt auf der
 *   [JuiceState.elapsedMillis]-Achse (Kaskaden-Versatz bereits eingerechnet:
 *   40 ms je Burst, Kappe ab dem 5., R45).
 * @property kind Art des Ausbruchs (bestimmt Partikelprofil und Glow).
 * @property xDp Ursprung (dp-Brettkoordinaten).
 * @property yDp Ursprung (dp-Brettkoordinaten).
 * @property colorsArgb Partikelfarben: Kristall-Burst = genau die Soll-Farbe;
 *   Feuerwerk = Soll-Farben aller Kristalle in Brett-Reihenfolge (r, dann q),
 *   zyklisch durchlaufen (§13.10).
 * @property particleCount Zu spawnende Partikelzahl; beim Kristall-Burst
 *   bereits aus dem Juice-PRNG gezogen (P = 8 + nextInt(5), §13.9), beim
 *   Feuerwerk F = min(120, 60 + 12·K) (§13.10); 0 unter Reduce-Motion (R44).
 * @property emitterIndex Seed-Bestandteil laut ADR-011 (1000 + Kaskadenposition
 *   bzw. 2000 für das Feuerwerk).
 * @property moveNumber Auslösender Zug (Seed-Bestandteil).
 */
internal data class ScheduledBurst(
    val startAtMillis: Long,
    val kind: BurstKind,
    val xDp: Float,
    val yDp: Float,
    val colorsArgb: List<Int>,
    val particleCount: Int,
    val emitterIndex: Int,
    val moveNumber: Int,
)

/** Art eines [ScheduledBurst]. */
internal enum class BurstKind {
    /** Kristall-Burst mit Glow (§13.9): Radius 0 → 28 dp, 250 ms, additiv. */
    CRYSTAL,

    /** Lösungs-Feuerwerk (§13.10): 120–320 dp/s, Gravitation 480 dp/s². */
    FIREWORK,
}
